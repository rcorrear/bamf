(ns bamf.api.save-movies
  (:require [taoensso.telemere :as t]))

(defn- require-fn
  [context k]
  (or (get context k)
      (throw (IllegalStateException.
              (format "Missing %s in context. bamf.api.save-movies expects :movie/validate and :movie/save!" k)))))

(defn- request-payload
  [request]
  (cond (map? (:json-body request)) (:json-body request)
        (map? (:body request))      (:body request)
        (map? (:params request))    (:params request)
        :else                       {}))

(defn- invalid-response [errors] {:status 400 :body {:message "Validation failed" :errors errors}})

(defn- stored-response
  [movie]
  {:status 201
   :body   (-> movie
               (dissoc :targetSystem)
               (update :tags #(or % [])))})

(defn- duplicate-response
  [{:keys [reason existing-id field]}]
  (let [message    "Movie already exists"
        field-name (case field
                     :path   "path"
                     :tmdbId "tmdbId"
                     (name field))]
    {:status 400 :body {:message message :errors {field-name [(str message " (id " existing-id ")")]} :reason reason}}))

(defn save-movie
  "Handle POST /api/v3/movie by delegating to provided movie persistence functions."
  [context request]
  (let [validate (require-fn context :movie/validate)
        save!    (require-fn context :movie/save!)
        payload  (request-payload request)
        errors   (validate payload)]
    (t/log! {:level :info :event :api/save-movie.received :details {:path (:path payload) :tmdbId (:tmdbId payload)}}
            "Incoming movie save request")
    (if (seq errors)
      (do (t/log! {:level :warn :event :api/save-movie.validation-failed :details {:errors errors}}
                  "Movie save request failed validation")
          (invalid-response errors))
      (let [result (save! payload)]
        (case (:status result)
          :stored    (do (t/log!
                          {:level :info :event :api/save-movie.stored :details {:movie-id (get-in result [:movie :id])}}
                          "Movie saved successfully via API")
                         (stored-response (:movie result)))
          :duplicate (do (t/log! {:level   :info
                                  :event   :api/save-movie.duplicate
                                  :details {:field (:field result) :existing-id (:existing-id result)}}
                                 "Duplicate movie detected via API")
                         (duplicate-response result))
          :invalid   (do (t/log! {:level :error :event :api/save-movie.invalid :details {:errors (:errors result)}}
                                 "Movie persistence returned invalid status")
                         (invalid-response (:errors result)))
          (do (t/log! {:level :error :event :api/save-movie.unexpected :details {:status (:status result)}}
                      "Unexpected result status when saving movie")
              {:status 500 :body {:error "unexpected-state"}}))))))
