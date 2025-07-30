(ns bamf.movies.persistence
  (:require [bamf.movies.model :as model]
            [bamf.movies.rama-client.depot :as depot]
            [bamf.movies.rama-client.pstate :as pstate]
            [taoensso.telemere :as t])
  (:import (java.time Instant)
           (java.time.format DateTimeFormatter)))

(def ^:private iso-formatter DateTimeFormatter/ISO_INSTANT)

(defn- now-utc [] (.format iso-formatter (Instant/now)))

(defn- clock [env] (or (:clock env) now-utc))

(defn- duplicate-response
  [field reason existing]
  {:status :duplicate :reason reason :field field :existing-id (:id existing)})

(defn save!
  "Validate, normalize and persist the incoming movie payload.
   Returns maps shaped for API translation: {:status :stored|:duplicate|:invalid ...}."
  [env movie]
  (let [errors (model/validate movie)]
    (if (seq errors)
      (do (t/log! {:level :warn :event :movies/save-invalid :details {:errors errors}}
                  "Rejecting movie payload: validation failed")
          {:status :invalid :errors errors})
      (let [canonical      (model/normalize movie (clock env))
            metadata-id    (:movieMetadataId canonical)
            path           (:path canonical)
            duplicate-meta (pstate/movie-by-metadata-id env metadata-id)
            duplicate-path (when-not duplicate-meta (pstate/movie-by-path env path))
            movie-depot    (:movie-depot env)]
        (cond duplicate-meta     (let [response (duplicate-response :tmdbId "duplicate-metadata" duplicate-meta)]
                                   (t/log! {:level   :info
                                            :event   :movies/save-duplicate
                                            :details {:field       (:field response)
                                                      :existing-id (:existing-id response)
                                                      :reason      (:reason response)}}
                                           "Duplicate movie detected during save")
                                   response)
              duplicate-path     (let [response (duplicate-response :path "duplicate-path" duplicate-path)]
                                   (t/log! {:level   :info
                                            :event   :movies/save-duplicate
                                            :details {:field       (:field response)
                                                      :existing-id (:existing-id response)
                                                      :reason      (:reason response)}}
                                           "Duplicate movie detected during save")
                                   response)
              (nil? movie-depot) (do (t/log! {:level   :error
                                              :event   :movies/save-error
                                              :details {:message "movie-depot handle not provided"}}
                                             "Failed to save movie: missing depot handle")
                                     {:status :error :errors ["movie-depot handle not provided"]})
              :else              (let [id        (long (pstate/next-id env))
                                       movie*    (assoc canonical :id id)
                                       event     (depot/movie-saved-event movie-depot movie*)
                                       result    (depot/put! event)
                                       persisted (merge movie* (:movie result))
                                       status    (or (:status result) :stored)]
                                   (t/log! {:level   :info
                                            :event   :movies/save-stored
                                            :details {:movie-id id
                                                      :tmdbId   (:tmdbId persisted)
                                                      :path     (:path persisted)
                                                      :status   status}}
                                           "Movie persisted successfully")
                                   {:status status :movie persisted}))))))
