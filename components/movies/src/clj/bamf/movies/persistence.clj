(ns bamf.movies.persistence
  (:require [bamf.movies.model :as model]
            [bamf.movies.rama.client.depot :as depot]
            [bamf.movies.rama.client.pstate :as pstate]
            [clojure.string :as str]
            [taoensso.telemere :as t])
  (:import (java.time Instant)
           (java.time.format DateTimeFormatter)))

(def ^:private iso-formatter DateTimeFormatter/ISO_INSTANT)

(defn- now-utc [] (.format iso-formatter (Instant/now)))

(defn- clock [env] (or (:clock env) now-utc))

(defn- duplicate-response
  [field reason existing]
  {:status :duplicate :reason reason :field field :existing-id (:id existing)})

(def ^:private mutable-update-fields
  #{:monitored :qualityProfileId :minimumAvailability :movieMetadataId :lastSearchTime})
(def ^:private missing-id-error "id must be a positive integer")
(def ^:private missing-update-error "At least one mutable field must be provided for updates")

(defn- parse-long*
  [value]
  (cond (instance? Long value) value
        (integer? value)       (long value)
        (string? value)        (try (Long/parseLong ^String (str/trim value)) (catch Exception _ nil))
        :else                  nil))

(defn- positive-id [value] (let [parsed (parse-long* value)] (when (and parsed (pos? parsed)) parsed)))

(defn- prepare-update
  [movie-id existing patch clock-fn]
  (let [merged (merge existing patch)
        errors (model/validate merged)]
    (if (seq errors)
      {:errors errors}
      {:movie (-> merged
                  (model/normalize clock-fn)
                  (assoc :id movie-id))})))

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
            existing-id    (pstate/movie-id-by-metadata-id env metadata-id)
            duplicate-meta (when existing-id (pstate/movie-by-id env existing-id))
            duplicate-path (when-not duplicate-meta (pstate/movie-by-path env path))
            movie-depot    (:movie-depot env)]
        (cond duplicate-meta     (let [response (duplicate-response :tmdbId "duplicate-metadata" duplicate-meta)]
                                   (t/log! {:level   :info
                                            :event   :movies/create-duplicate
                                            :details {:field       (:field response)
                                                      :existing-id (:existing-id response)
                                                      :reason      (:reason response)}}
                                           "Duplicate movie detected during save")
                                   response)
              duplicate-path     (let [response (duplicate-response :path "duplicate-path" duplicate-path)]
                                   (t/log! {:level   :info
                                            :event   :movies/create-duplicate
                                            :details {:field       (:field response)
                                                      :existing-id (:existing-id response)
                                                      :reason      (:reason response)}}
                                           "Duplicate movie detected during save")
                                   response)
              (nil? movie-depot) (do (t/log! {:level   :error
                                              :event   :movies/create-error
                                              :details {:message "movie-depot handle not provided"}}
                                             "Failed to save movie: missing depot handle")
                                     {:status :error :errors ["movie-depot handle not provided"]})
              :else              (let [result    (depot/put! {:depot movie-depot :movie canonical})
                                       persisted (merge canonical (:movie result))
                                       status    (or (:status result) :stored)]
                                   (t/log! {:level   :info
                                            :event   :movies/create-stored
                                            :details {:movie-id (:id persisted)
                                                      :tmdbId   (:tmdbId persisted)
                                                      :path     (:path persisted)
                                                      :status   status}}
                                           "Movie persisted successfully")
                                   {:status status :movie persisted}))))))

(defn update!
  "Validate, normalize, and persist mutable fields for an existing movie."
  [env movie]
  (let [movie-id (positive-id (:id movie))
        tmdb-id  (:tmdbId movie)]
    (println "tmdb-id" tmdb-id)
    (if (nil? movie-id)
      (do (t/log! {:level :warn :event :movies/update-invalid :details {:errors [missing-id-error]}}
                  "Rejecting movie update: missing id")
          {:status :invalid :errors [missing-id-error]})
      (if-let [existing (pstate/movie-by-id env movie-id tmdb-id)]
        (if-let [patch (not-empty (select-keys movie mutable-update-fields))]
          (let [{:keys [errors movie]} (prepare-update movie-id existing patch (clock env))]
            (if (seq errors)
              (do (t/log! {:level :warn :event :movies/update-invalid :details {:movie-id movie-id :errors errors}}
                          "Rejecting movie update: validation failed")
                  {:status :invalid :errors errors})
              (let [metadata-id (:movieMetadataId movie)
                    conflict-id (when metadata-id (pstate/movie-id-by-metadata-id env metadata-id tmdb-id))]
                (cond (and conflict-id (not= conflict-id movie-id)) (let [conflict (or (pstate/movie-by-id env
                                                                                                           conflict-id
                                                                                                           tmdb-id)
                                                                                       {:id conflict-id})
                                                                          response (duplicate-response
                                                                                    :tmdbId
                                                                                    "duplicate-metadata"
                                                                                    conflict)]
                                                                      (t/log! {:level   :info
                                                                               :event   :movies/update-duplicate
                                                                               :details {:movie-id    movie-id
                                                                                         :field       (:field response)
                                                                                         :existing-id (:existing-id
                                                                                                       response)}}
                                                                              "Duplicate movie detected during update")
                                                                      response)
                      (nil? (:movie-depot env))                     (do (t/log!
                                                                         {:level   :error
                                                                          :event   :movies/update-error
                                                                          :details {:movie-id movie-id
                                                                                    :message
                                                                                    "movie-depot handle not provided"}}
                                                                         "Failed to update movie: missing depot handle")
                                                                        {:status :error
                                                                         :errors ["movie-depot handle not provided"]})
                      :else                                         (let [result    (depot/update! {:depot (:movie-depot
                                                                                                            env)
                                                                                                    :movie movie})
                                                                          persisted (merge movie (:movie result))
                                                                          status    (or (:status result) :updated)]
                                                                      (t/log! {:level   :info
                                                                               :event   :movies/update-stored
                                                                               :details {:movie-id movie-id
                                                                                         :status   status}}
                                                                              "Movie updated successfully")
                                                                      {:status status :movie persisted})))))
          (do (t/log!
               {:level :warn :event :movies/update-invalid :details {:movie-id movie-id :errors [missing-update-error]}}
               "Rejecting movie update: no mutable fields provided")
              {:status :invalid :errors [missing-update-error]}))
        (do (t/log! {:level :info :event :movies/update-not-found :details {:movie-id movie-id}}
                    "Movie update failed: record not found")
            {:status :not-found :movie-id movie-id})))))
