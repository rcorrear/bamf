(ns bamf.movies.persistence
  (:require [bamf.movies.model :as model]
            [bamf.movies.rama.client.depot :as depot]
            [bamf.movies.rama.client.pstate :as pstate]
            [clojure.string :as str]
            [taoensso.telemere :as t])
  (:import (java.nio.file Paths)
           (java.time Instant)
           (java.time.format DateTimeFormatter)))

(def ^:private iso-formatter DateTimeFormatter/ISO_INSTANT)

(defn- now-utc [] (.format iso-formatter (Instant/now)))

(defn- clock [env] (or (:clock env) now-utc))

(defn- duplicate-response
  [field reason existing]
  {:status :duplicate :reason reason :field field :existing-id (:id existing)})

(def ^:private mutable-update-fields
  "Fields callers may mutate via PUT. Keep this aligned with the Rama update op and the HTTP schema."
  #{:last-search-time :monitored :path :quality-profile-id :root-folder-path :tags})

(def ^:private depot-update-fields
  "Fields required when applying updates. Keep the depot payload minimal to avoid
  pushing nullable or unnecessary data. id is used by the Rama module to locate the row."
  #{:id :monitored :path :quality-profile-id :root-folder-path :tags})

(def ^:private missing-id-error "id must be a positive integer")
(def ^:private missing-minimum-availability-error "minimumAvailability is required")

(defn- parse-long*
  [value]
  (cond (instance? Long value) value
        (integer? value)       (long value)
        (string? value)        (parse-long value)
        :else                  nil))

(defn- positive-id [value] (let [parsed (parse-long* value)] (when (and parsed (pos? parsed)) parsed)))

(defn- trim-path
  [path]
  (let [trimmed (some-> path
                        str
                        str/trim)]
    (when (seq trimmed) trimmed)))

(defn- parent-path
  [path]
  (some-> path
          java.io.File.
          .getParent))

(defn- combine-path
  "Join root + leaf, respecting absolute leafs and tolerating nils.
   Uses java.nio.file to normalize separators and \"..\" segments."
  [root leaf]
  (let [leaf* (trim-path leaf)
        root* (trim-path root)]
    (when leaf*
      (let [leaf-path (Paths/get ^String leaf* (make-array String 0))]
        (cond (.isAbsolute ^java.nio.file.Path leaf-path) (-> leaf-path
                                                              .normalize
                                                              .toString)
              (str/blank? root*)                          (-> leaf-path
                                                              .normalize
                                                              .toString)
              :else                                       (-> (Paths/get ^String root* (make-array String 0))
                                                              (.resolve leaf-path)
                                                              .normalize
                                                              .toString))))))

(defn- derive-paths
  "Return canonical {:path :root-folder-path :folder-name} using Radarr semantics.
   - Prefer explicit path when provided.
   - Otherwise build from folderName (full or relative) or folder + root.
   - Derive root from provided root-folder-path or the parent of the chosen path.
   - folder is never persisted."
  [existing-path {:keys [path root-folder-path folder folder-name]}]
  (let [requested     (trim-path path)
        root          (trim-path root-folder-path)
        leaf          (or (trim-path folder-name) (trim-path folder))
        derived       (combine-path root leaf)
        chosen-path   (or requested derived leaf existing-path)
        resolved-root (or root (parent-path chosen-path))]
    {:path chosen-path :root-folder-path resolved-root :folder-name (or (trim-path folder-name) derived chosen-path)}))

(defn- reconcile-paths
  "Return only the mutable path fields that changed compared to the existing record."
  [existing movie]
  (let [{:keys [path root-folder-path]} (derive-paths (:path existing) movie)
        path-change                     (when path (not= path (:path existing)))
        root-change                     (when root-folder-path (not= root-folder-path (:root-folder-path existing)))]
    {:path (when path-change path) :root-folder-path (when root-change root-folder-path)}))

(defn- duplicate-check
  "Return a duplicate response map if tmdb-id already exists, otherwise nil."
  [env canonical]
  (let [tmdb-id     (:tmdb-id canonical)
        existing-id (pstate/movie-id-by-tmdb-id env tmdb-id)
        duplicate   (when existing-id (pstate/movie-by-id env existing-id))]
    (when duplicate (duplicate-response :tmdb-id "duplicate-metadata" duplicate))))

(defn- validate-new
  "Validate incoming movie payload; return either {:errors [...]} or {:movie canonical}."
  [env movie]
  (let [metadata        (model/extract-metadata movie)
        metadata-errors (model/validate-metadata metadata)
        minimum-error   (when (nil? (:minimum-availability movie)) missing-minimum-availability-error)
        errors          (model/validate movie)
        all-errors      (->> (concat errors metadata-errors (when minimum-error [minimum-error]))
                             (remove nil?)
                             distinct
                             vec)]
    (if (seq all-errors)
      {:errors all-errors}
      {:movie (-> movie
                  (model/normalize (clock env))
                  (merge (derive-paths nil movie))
                  (dissoc :folder)
                  (merge (model/normalize-metadata metadata)))})))

(declare merge-response-metadata)

(defn- persist-new
  "Persist a validated, canonical movie payload to the depot."
  [env canonical]
  (let [tmdb-id     (:tmdb-id canonical)
        movie-depot (:movie-depot env)]
    (cond (nil? movie-depot) (do (t/log! {:level   :error
                                          :reason  :movies/create-error
                                          :details {:message "movie-depot handle not provided"}}
                                         "Failed to save movie: missing depot handle")
                                 {:status :error :errors ["movie-depot handle not provided"]})
          :else              (let [result       (depot/put! {:depot movie-depot :movie canonical})
                                   depot-movie  (:movie result)
                                   depot-id     (:id depot-movie)
                                   canonical-id (:id canonical)
                                   lookup-id    (or depot-id
                                                    (pstate/movie-id-by-tmdb-id env tmdb-id)
                                                    (when (pos? (or canonical-id 0)) canonical-id))
                                   merged       (merge canonical depot-movie)
                                   persisted    (assoc merged :id lookup-id)
                                   status       (or (:status result) :stored)
                                   response     (merge-response-metadata persisted (model/extract-metadata canonical))]
                               (t/log! {:level   :info
                                        :reason  :movies/create-stored
                                        :details {:movie-id (:id persisted)
                                                  :tmdb-id  (:tmdb-id persisted)
                                                  :path     (:path persisted)
                                                  :status   status}}
                                       "Movie persisted successfully")
                               {:status status :movie response}))))


(defn- update-patch
  "Return the subset of fields callers are allowed to mutate, or nil when none are present."
  [existing movie]
  (let [raw-patch                       (select-keys movie mutable-update-fields)
        {:keys [path root-folder-path]} (reconcile-paths existing movie)
        path-change                     (when path (not= path (:path existing)))
        root-change                     (when root-folder-path (not= root-folder-path (:root-folder-path existing)))]
    (-> raw-patch
        (cond-> path-change (assoc :path path))
        (cond-> root-change (assoc :root-folder-path root-folder-path))
        not-empty)))

(defn- metadata-patch [movie] (let [patch (model/extract-metadata movie)] (when (seq patch) patch)))

(defn- merge-metadata
  [existing patch]
  (let [existing (or existing {})
        removals (->> patch
                      (filter (comp nil? val))
                      (map key))
        updates  (into {} (remove (comp nil? val) patch))
        merged   (merge (apply dissoc existing removals) updates)]
    (if (seq merged) merged {})))

(defn- merge-response-metadata
  [movie metadata]
  (let [movie (dissoc movie :metadata)]
    (if-let [response-metadata (model/serialize-metadata metadata)]
      (merge movie response-metadata)
      movie)))

(defn- persist-update
  "Send minimal payload to depot and merge any depot-provided fields into the response."
  [env movie-id movie metadata-patch metadata-update]
  (if (nil? (:movie-depot env))
    (do (t/log! {:level   :error
                 :reason  :movies/update-error
                 :details {:movie-id movie-id :message "movie-depot handle not provided"}}
                "Failed to update movie: missing depot handle")
        {:status :error :errors ["movie-depot handle not provided"]})
    (let [depot-payload (cond-> (select-keys movie depot-update-fields)
                          metadata-patch (merge metadata-patch)
                          metadata-patch (assoc :metadata metadata-update))
          result        (depot/update! {:depot (:movie-depot env) :movie depot-payload})
          depot-movie   (:movie result)
          depot-id      (:id depot-movie)
          merged        (merge movie depot-movie)
          persisted     (assoc merged :id (or depot-id movie-id))
          status        (or (:status result) :updated)]
      (t/log! {:level :info :reason :movies/update-stored :details {:movie-id movie-id :status status}}
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
  (let [patch          (update-patch existing movie)
        metadata-patch (metadata-patch movie)]
    (cond (and (nil? patch) (nil? metadata-patch))
          (do (t/log! {:level :info :reason :movies/update-noop :details {:movie-id movie-id}}
                      "Ignoring movie update: no mutable changes provided")
              {:status :updated :movie (merge-response-metadata existing (pstate/metadata-by-movie-id env movie-id))})
          :else
          (let [metadata-errors        (when metadata-patch (model/validate-metadata metadata-patch))
                patch                  (or patch {})
                {:keys [errors movie]} (prepare-update movie-id existing patch (clock env))
                all-errors             (->> (concat errors metadata-errors)
                                            (remove nil?)
                                            distinct
                                            vec)
                existing-metadata      (when metadata-patch (pstate/metadata-by-movie-id env movie-id))
                normalized-metadata    (when metadata-patch (model/normalize-metadata metadata-patch))
                metadata-update        (when metadata-patch (merge-metadata existing-metadata normalized-metadata))]
            (cond (seq all-errors) (do (t/log! {:level   :warn
                                                :reason  :movies/update-invalid
                                                :details {:movie-id movie-id :errors all-errors}}
                                               "Rejecting movie update: validation failed")
                                       {:status :invalid :errors all-errors})
                  :else            (let [result (persist-update env movie-id movie normalized-metadata metadata-update)
                                         response-metadata
                                         (if metadata-patch metadata-update (pstate/metadata-by-movie-id env movie-id))]
                                     (update result :movie merge-response-metadata response-metadata)))))))

(defn save!
  "Validate, normalize and persist the incoming movie payload.
   Returns maps shaped for API translation: {:status :stored|:duplicate|:invalid ...}."
  [env movie]
  (let [movie                  (dissoc movie :last-search-time) ; create flow must not set last-search-time
        {:keys [errors movie]} (validate-new env movie)
        duplicate              (when movie (duplicate-check env movie))]
    (cond (seq errors) (do (t/log! {:level :warn :reason :movies/save-invalid :details {:errors errors}}
                                   "Rejecting movie payload: validation failed")
                           {:status :invalid :errors errors})
          duplicate    (do (t/log! {:level   :info
                                    :reason  :movies/create-duplicate
                                    :details {:field       (:field duplicate)
                                              :existing-id (:existing-id duplicate)
                                              :reason      (:reason duplicate)}}
                                   "Duplicate movie detected during save")
                           duplicate)
          :else        (persist-new env movie))))

(defn update!
  "Validate, normalize, and persist mutable fields for an existing movie."
  [env movie]
  (let [movie-id (positive-id (:id movie))]
    (cond (nil? movie-id) (do (t/log!
                               {:level :warn :reason :movies/update-invalid :details {:errors [missing-id-error]}}
                               "Rejecting movie update: missing id")
                              {:status :invalid :errors [missing-id-error]})
          :else           (if-let [existing (pstate/movie-by-id env movie-id)]
                            (apply-update env movie-id existing movie)
                            (do (t/log! {:level :info :reason :movies/update-not-found :details {:movie-id movie-id}}
                                        "Movie update failed: record not found")
                                {:status :not-found :movie-id movie-id})))))
