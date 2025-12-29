(ns bamf.movies.http
  "HTTP handlers, schemas, and route exports for the Movies component."
  (:require [bamf.movies.inspection :as inspection]
            [bamf.movies.persistence :as persistence]
            [camel-snake-kebab.core :as csk]
            [clojure.walk :refer [keywordize-keys]]
            [ring.util.mime-type :as mime]
            [taoensso.telemere :as t]))

(def movie-path "/api/v3/movie")

(def json-media-type (mime/default-mime-types "json"))

(def json-media [json-media-type])

(def ^:private movie-env-key :movies/env)

(def movie-record
  [:map {:closed false} [:id {:optional true} [:maybe pos-int?]] [:tmdb-id pos-int?] [:imdb-id {:optional true} string?]
   [:title string?] [:original-title {:optional true} [:maybe string?]] [:title-slug {:optional true} string?]
   [:path {:optional true} [:maybe string?]] [:root-folder-path {:optional true} [:maybe string?]]
   [:folder {:optional true} [:maybe string?]] [:folder-name {:optional true} [:maybe string?]]
   [:minimum-availability string?] [:status {:optional true} [:maybe string?]] [:monitored boolean?]
   [:has-file {:optional true} [:maybe boolean?]] [:is-available {:optional true} boolean?]
   [:quality-profile-id pos-int?] [:movie-file-id {:optional true} int?] [:runtime {:optional true} [:maybe int?]]
   [:size-on-disk {:optional true} [:maybe int?]] [:year {:optional true} int?]
   [:secondary-year {:optional true} [:maybe int?]] [:popularity {:optional true} number?]
   [:tags {:optional true} [:sequential [:or int? string?]]] [:add-options {:optional true} map?]])

(def movie-create-request-camel
  [:map {:closed false} [:title string?] [:path {:optional true} [:maybe string?]]
   [:rootFolderPath {:optional true} [:maybe string?]] [:monitored boolean?] [:qualityProfileId pos-int?]
   [:minimumAvailability string?] [:tmdbId pos-int?] [:titleSlug {:optional true} string?]
   [:imdbId {:optional true} string?] [:addOptions {:optional true} map?]
   [:tags {:optional true} [:sequential [:or int? string?]]] [:year {:optional true} int?]
   [:secondaryYear {:optional true} [:maybe int?]] [:status {:optional true} string?]
   [:hasFile {:optional true} [:maybe boolean?]] [:isAvailable {:optional true} boolean?]
   [:movieFileId {:optional true} int?] [:runtime {:optional true} int?] [:sizeOnDisk {:optional true} [:maybe int?]]
   [:popularity {:optional true} number?]])

(def movie-update-request-camel
  [:map {:closed false} [:monitored {:optional true} boolean?] [:qualityProfileId {:optional true} pos-int?]
   [:minimumAvailability {:optional true} string?] [:movieMetadataId {:optional true} pos-int?]
   [:lastSearchTime {:optional true} [:maybe string?]] [:title {:optional true} string?]
   [:path {:optional true} [:maybe string?]] [:rootFolderPath {:optional true} [:maybe string?]]
   [:titleSlug {:optional true} string?] [:imdbId {:optional true} string?]
   [:tags {:optional true} [:sequential [:or int? string?]]] [:year {:optional true} int?]
   [:secondaryYear {:optional true} [:maybe int?]] [:status {:optional true} string?]
   [:hasFile {:optional true} [:maybe boolean?]] [:isAvailable {:optional true} boolean?]
   [:movieFileId {:optional true} int?] [:runtime {:optional true} int?] [:sizeOnDisk {:optional true} [:maybe int?]]
   [:popularity {:optional true} number?]])

(def duplicate-body [:map [:error string?] [:field string?] [:existing-id pos-int?]])

(def error-body [:map [:errors [:sequential string?]]])

(def not-found-body [:map [:errors [:sequential string?]]])

(def list-query-schema-camel
  [:maybe
   [:map {:closed false} [:tmdbId {:optional true} pos-int?] [:excludeLocalCovers {:optional true} boolean?]
    [:languageId {:optional true} pos-int?]]])

(defn- safe-payload [request] (or (:body-params request) (:body request) {}))

(defn- request-env
  [request]
  (or (:movies/env request)
      (get-in request [:reitit.core/match :data movie-env-key])
      (throw (IllegalStateException. "Movies HTTP route missing :movies/env binding"))))

(defn- external-field-name
  [field]
  (cond (string? field)  field
        (keyword? field) (csk/->camelCase (name field))
        :else            (or (some-> field
                                     str)
                             "unknown")))

(defn- response-movie
  [movie]
  (let [movie (or movie {})]
    (-> movie
        (update :movie-metadata-id #(or % (:tmdb-id movie)))
        (update :size-on-disk #(if (some? %) % 0))
        (dissoc :target-system))))

(defn- response-movies [movies] (mapv response-movie (or movies [])))

(defn- duplicate->response
  [{:keys [reason field existing-id]}]
  (let [field-name (external-field-name field)]
    {:status 409 :body {:error (or reason "duplicate") :field field-name :existing-id existing-id}}))

(defn- invalid->response [{:keys [errors]}] {:status 422 :body {:errors (vec (or errors []))}})

(defn- error->response [{:keys [errors]}] {:status 500 :body {:errors (vec (or errors ["unexpected error"]))}})

(defn- stored->response [{:keys [movie]}] {:status 201 :body (response-movie movie)})

(defn- updated->response [{:keys [movie]}] {:status 200 :body (response-movie movie)})

(defn- not-found->response
  [{:keys [movie-id]}]
  {:status 404 :body {:errors [(if movie-id (format "Movie %s not found" movie-id) "Movie not found")]}})

(defn list-movies
  "Return movies filtered by the supplied query parameters."
  [request]
  (let [env              (request-env request)
        query            (or (get-in request [:parameters :query])
                             (some-> request
                                     :query-params
                                     keywordize-keys)
                             {})
        {:keys [movies]} (inspection/list-movies env query)]
    (t/log! :debug {:reason :rest-api/list-movies :query-params query})
    {:status 200 :body (response-movies movies)}))

(defn get-movie
  "Fetch a single movie by id."
  [request]
  (let [env    (request-env request)
        id     (get-in request [:path-params :id])
        result (inspection/get-movie env id)]
    (case (:status result)
      :ok        {:status 200 :body (response-movie (:movie result))}
      :not-found (not-found->response result)
      (do (t/log! {:level   :error
                   :reason  :movies/http-unexpected-status
                   :details {:status (:status result) :operation :get-movie :id id}}
                  "movies/get-movie returned unexpected status")
          (error->response {:errors [(format "Unexpected status %s" (:status result))]})))))

(defn create-movie
  "Persist a movie payload and translate the component response into HTTP semantics."
  [request]
  (let [payload (safe-payload request)
        env     (request-env request)
        result  (persistence/save! env payload)]
    (case (:status result)
      :stored    (stored->response result)
      :duplicate (duplicate->response result)
      :invalid   (invalid->response result)
      :error     (error->response result)
      (do (t/log! {:level   :error
                   :reason  :movies/http-unexpected-status
                   :details {:status       (:status result)
                             :payload-keys (-> payload
                                               keys
                                               sort
                                               vec)}}
                  "movies/save-movie! returned unexpected status")
          (error->response {:errors [(format "Unexpected status %s" (:status result))]})))))

(defn update-movie
  "Apply mutations to an existing movie and translate persistence responses to HTTP."
  [request]
  (let [payload    (safe-payload request)
        path-id    (get-in request [:path-params :id])
        move-files (or (get-in request [:parameters :query :move-files]) (get-in request [:query-params :move-files]))
        env        (request-env request)
        result     (persistence/update! env
                                        (cond-> (assoc payload :id (or (:id payload) path-id))
                                          (some? move-files) (assoc :move-files move-files)))]
    (case (:status result)
      :updated   (updated->response result)
      :duplicate (duplicate->response result)
      :invalid   (invalid->response result)
      :not-found (not-found->response result)
      :error     (error->response result)
      (do (t/log!
           {:level :error :reason :movies/http-unexpected-status :details {:status (:status result) :operation :update}}
           "movies/update-movie! returned unexpected status")
          (error->response {:errors [(format "Unexpected status %s" (:status result))]})))))

(defn get-http-api
  "Return the Movies component HTTP route declarations for aggregation."
  [{:keys [runtime-state] :as context}]
  (let [env (or (:movies/env context) (get runtime-state :movies/env))]
    [[movie-path
      {:name         :movies/movie
       :produces     json-media
       movie-env-key env
       :get          {:name       :movies/list
                      :handler    list-movies
                      :parameters {:query list-query-schema-camel}
                      :responses  {200 {:body [:sequential movie-record]}}
                      :produces   json-media}
       :post         {:name       :movies/create
                      :handler    create-movie
                      :parameters {:body movie-create-request-camel}
                      :responses  {201 {:body movie-record}
                                   409 {:body duplicate-body}
                                   422 {:body error-body}
                                   500 {:body error-body}}
                      :produces   json-media
                      :consumes   json-media}}]
     [(str movie-path "/{id}")
      {:name         :movies/movie-by-id
       :produces     json-media
       movie-env-key env
       :get          {:name       :movies/detail
                      :handler    get-movie
                      :parameters {:path [:map [:id pos-int?]]}
                      :responses  {200 {:body movie-record} 404 {:body not-found-body} 500 {:body error-body}}
                      :produces   json-media}
       :put          {:name       :movies/update
                      :handler    update-movie
                      :parameters {:path  [:map [:id pos-int?]]
                                   :query [:map {:closed false} [:moveFiles {:optional true} boolean?]]
                                   :body  movie-update-request-camel}
                      :responses  {200 {:body movie-record}
                                   404 {:body not-found-body}
                                   409 {:body duplicate-body}
                                   422 {:body error-body}
                                   500 {:body error-body}}
                      :produces   json-media
                      :consumes   json-media}}]]))
