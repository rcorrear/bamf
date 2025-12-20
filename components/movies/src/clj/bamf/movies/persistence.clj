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
  #{:monitored :minimum-availability :quality-profile-id :path :tags :movie-metadata-id :last-search-time})

(def ^:private depot-update-fields
  "Fields required when applying updates. Keep the depot payload minimal to avoid
  pushing nullable or unnecessary data. tmdb-id is needed for Rama to locate the row."
  #{:id :tmdb-id :monitored :minimum-availability :quality-profile-id :path :tags})

(def ^:private missing-id-error "id must be a positive integer")
(def ^:private missing-update-error "At least one mutable field must be provided for updates")

(defn- parse-long*
  [value]
  (cond (instance? Long value) value
        (integer? value)       (long value)
        (string? value)        (parse-long value)
        :else                  nil))

(defn- positive-id [value] (let [parsed (parse-long* value)] (when (and parsed (pos? parsed)) parsed)))

(defn- duplicate-check
  "Return a duplicate response map if tmdb-id already exists, otherwise nil."
  [env canonical]
  (let [tmdb-id     (:tmdb-id canonical)
        existing-id (pstate/movie-id-by-tmdb-id env tmdb-id)
        duplicate   (when existing-id (pstate/movie-by-id env existing-id))]
    (when duplicate
      (duplicate-response :tmdb-id "duplicate-metadata" duplicate))))

(defn- validate-new
  "Validate incoming movie payload; return either {:errors [...]} or {:movie canonical}."
  [env movie]
  (let [errors (model/validate movie)]
    (if (seq errors)
      {:errors errors}
      {:movie (model/normalize movie (clock env))})))

(defn- persist-new
  "Persist a validated, canonical movie payload to the depot."
  [env canonical]
  (let [tmdb-id     (:tmdb-id canonical)
        movie-depot (:movie-depot env)]
    (cond
      (nil? movie-depot)
      (do (t/log! {:level   :error
                   :reason  :movies/create-error
                   :details {:message "movie-depot handle not provided"}}
                  "Failed to save movie: missing depot handle")
          {:status :error :errors ["movie-depot handle not provided"]})

      :else
      (let [result       (depot/put! {:depot movie-depot :movie canonical})
            depot-movie  (:movie result)
            depot-id     (:id depot-movie)
            canonical-id (:id canonical)
            lookup-id    (or depot-id
                             (pstate/movie-id-by-tmdb-id env tmdb-id)
                             (when (pos? (or canonical-id 0)) canonical-id))
            merged       (merge canonical depot-movie)
            persisted    (assoc merged :id lookup-id)
            status       (or (:status result) :stored)]
        (t/log! {:level   :info
                 :reason  :movies/create-stored
                 :details {:movie-id (:id persisted)
                           :tmdb-id  (:tmdb-id persisted)
                           :path     (:path persisted)
                           :status   status}}
                "Movie persisted successfully")
        {:status status :movie persisted}))))

(defn- conflict-response-if-needed
  "Detect metadata conflicts for updates; return a duplicate-response or nil when safe."
  [env movie-id movie]
  (when-let [metadata-id (:movie-metadata-id movie)]
    (when-let [conflict-id (pstate/movie-id-by-metadata-id env metadata-id)]
      (when (not= conflict-id movie-id)
        (duplicate-response :tmdb-id "duplicate-metadata" (or (pstate/movie-by-id env conflict-id)
                                                              {:id conflict-id}))))))
(defn- update-patch
  "Return the subset of fields callers are allowed to mutate, or nil when none are present."
  [movie]
  (not-empty (select-keys movie mutable-update-fields)))

(defn- persist-update
  "Send minimal payload to depot and merge any depot-provided fields into the response."
  [env movie-id movie]
  (if (nil? (:movie-depot env))
    (do (t/log! {:level   :error
                 :reason  :movies/update-error
                 :details {:movie-id movie-id
                           :message  "movie-depot handle not provided"}}
                "Failed to update movie: missing depot handle")
        {:status :error :errors ["movie-depot handle not provided"]})
    (let [depot-payload (select-keys movie depot-update-fields)
          result        (depot/update! {:depot (:movie-depot env) :movie depot-payload})
          depot-movie   (:movie result)
          depot-id      (:id depot-movie)
          merged        (merge movie depot-movie)
          persisted     (assoc merged :id (or depot-id movie-id))
          status        (or (:status result) :updated)]
      (t/log! {:level   :info
               :reason  :movies/update-stored
               :details {:movie-id movie-id :status status}}
              "Movie updated successfully")
      {:status status :movie persisted})))

(defn- prepare-update
  [movie-id existing patch clock-fn]
  (let [merged (merge existing patch)
        errors (model/validate merged)]
    (if (seq errors)
      {:errors errors}
      {:movie (-> merged
                  (model/normalize clock-fn)
                  (assoc :id movie-id))})))

(defn- apply-update
  "Validate, check conflicts, and persist an update for an existing movie."
  [env movie-id existing movie]
  (let [patch (update-patch movie)]
    (cond
      (nil? patch)
      (do (t/log! {:level   :warn
                   :reason  :movies/update-invalid
                   :details {:movie-id movie-id :errors [missing-update-error]}}
                  "Rejecting movie update: no mutable fields provided")
          {:status :invalid :errors [missing-update-error]})

      :else
      (let [{:keys [errors movie]} (prepare-update movie-id existing patch (clock env))
            conflict                (conflict-response-if-needed env movie-id movie)]
        (cond
          (seq errors)
          (do (t/log! {:level :warn :reason :movies/update-invalid :details {:movie-id movie-id :errors errors}}
                      "Rejecting movie update: validation failed")
              {:status :invalid :errors errors})

          conflict
          (do (t/log! {:level   :info
                       :reason  :movies/update-duplicate
                       :details {:movie-id    movie-id
                                 :field       (:field conflict)
                                 :existing-id (:existing-id conflict)}}
                      "Duplicate movie detected during update")
              conflict)

          :else
          (persist-update env movie-id movie))))))

(defn save!
  "Validate, normalize and persist the incoming movie payload.
   Returns maps shaped for API translation: {:status :stored|:duplicate|:invalid ...}."
  [env movie]
  (let [{:keys [errors movie]} (validate-new env movie)
        duplicate               (when movie (duplicate-check env movie))]
    (cond
      (seq errors)
      (do (t/log! {:level :warn :reason :movies/save-invalid :details {:errors errors}}
                  "Rejecting movie payload: validation failed")
          {:status :invalid :errors errors})

      duplicate
      (do (t/log! {:level   :info
                   :reason  :movies/create-duplicate
                   :details {:field       (:field duplicate)
                             :existing-id (:existing-id duplicate)
                             :reason      (:reason duplicate)}}
                  "Duplicate movie detected during save")
          duplicate)

      :else
      (persist-new env movie))))

(defn update!
  "Validate, normalize, and persist mutable fields for an existing movie."
  [env movie]
  (let [movie-id (positive-id (:id movie))]
    (cond
      (nil? movie-id)
      (do (t/log! {:level :warn :reason :movies/update-invalid :details {:errors [missing-id-error]}}
                  "Rejecting movie update: missing id")
          {:status :invalid :errors [missing-id-error]})

      :else
      (if-let [existing (pstate/movie-by-id env movie-id)]
        (apply-update env movie-id existing movie)
        (do (t/log! {:level :info :reason :movies/update-not-found :details {:movie-id movie-id}}
                    "Movie update failed: record not found")
            {:status :not-found :movie-id movie-id})))))
