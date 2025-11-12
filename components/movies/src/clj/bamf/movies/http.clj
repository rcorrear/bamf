(ns bamf.movies.http
  "HTTP handlers, schemas, and route exports for the Movies component."
  (:require [bamf.movies.persistence :as persistence]
            [ring.util.mime-type :as mime]
            [taoensso.telemere :as t]))

(def movie-path "/api/v3/movie")

(def json-media-type (mime/default-mime-types "json"))

(def json-media [json-media-type])

(def ^:private movie-env-key :movies/env)

(def movie-record
  [:map {:closed false} [:id pos-int?] [:tmdbId pos-int?] [:title string?] [:path string?]
   [:minimumAvailability string?] [:monitored boolean?] [:qualityProfileId pos-int?]])

(def movie-create-request
  [:map {:closed false} [:title string?] [:path string?] [:rootFolderPath string?] [:monitored boolean?]
   [:qualityProfileId pos-int?] [:minimumAvailability string?] [:tmdbId pos-int?] [:addOptions {:optional true} map?]
   [:tags {:optional true} [:sequential string?]] [:year {:optional true} pos-int?]])

(def movie-update-request
  [:map {:closed false} [:monitored {:optional true} boolean?] [:qualityProfileId {:optional true} pos-int?]
   [:minimumAvailability {:optional true} string?] [:movieMetadataId {:optional true} pos-int?]
   [:lastSearchTime {:optional true} [:maybe string?]]])

(def duplicate-body [:map [:error string?] [:field keyword?] [:existing-id pos-int?]])

(def error-body [:map [:errors [:sequential string?]]])

(def not-found-body [:map [:errors [:sequential string?]]])

(def list-query-schema
  [:maybe [:map {:closed false} [:term {:optional true} string?] [:page {:optional true} pos-int?]]])

(defn- safe-payload [request] (or (:body-params request) (:body request) {}))

(defn list-movies "Placeholder handler until listing is implemented." [_] {:status 200 :body {:data []}})

(defn- duplicate->response
  [{:keys [reason field existing-id]}]
  {:status 409 :body {:error (or reason "duplicate") :field (or field :unknown) :existing-id existing-id}})

(defn- invalid->response [{:keys [errors]}] {:status 422 :body {:errors (vec (or errors []))}})

(defn- error->response [{:keys [errors]}] {:status 500 :body {:errors (vec (or errors ["unexpected error"]))}})

(defn- stored->response [{:keys [movie]}] {:status 201 :body {:data movie}})

(defn- updated->response [{:keys [movie]}] {:status 200 :body {:data movie}})

(defn- not-found->response
  [{:keys [movie-id]}]
  {:status 404 :body {:errors [(if movie-id (format "Movie %s not found" movie-id) "Movie not found")]}})

(defn- request-env
  [request]
  (or (:movies/env request)
      (get-in request [:reitit.core/match :data movie-env-key])
      (throw (IllegalStateException. "Movies HTTP route missing :movies/env binding"))))

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
                   :event   :movies/http-unexpected-status
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
  (let [payload (safe-payload request)
        path-id (get-in request [:path-params :id])
        env     (request-env request)
        result  (persistence/update! env (assoc payload :id (or (:id payload) path-id)))]
    (case (:status result)
      :updated   (updated->response result)
      :duplicate (duplicate->response result)
      :invalid   (invalid->response result)
      :not-found (not-found->response result)
      :error     (error->response result)
      (do (t/log!
           {:level :error :event :movies/http-unexpected-status :details {:status (:status result) :operation :update}}
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
                      :parameters {:query list-query-schema}
                      :responses  {200 {:body [:map [:data [:sequential movie-record]]]}}
                      :produces   json-media}
       :post         {:name       :movies/create
                      :handler    create-movie
                      :parameters {:body movie-create-request}
                      :responses  {201 {:body [:map [:data movie-record]]}
                                   409 {:body duplicate-body}
                                   422 {:body error-body}
                                   500 {:body error-body}}
                      :produces   json-media
                      :consumes   json-media}}]
     [(str movie-path "/{id}")
      {:name         :movies/movie-by-id
       :produces     json-media
       movie-env-key env
       :patch        {:name       :movies/update
                      :handler    update-movie
                      :parameters {:path [:map [:id pos-int?]] :body movie-update-request}
                      :responses  {200 {:body [:map [:data movie-record]]}
                                   404 {:body not-found-body}
                                   409 {:body duplicate-body}
                                   422 {:body error-body}
                                   500 {:body error-body}}
                      :produces   json-media
                      :consumes   json-media}}]]))
