(ns bamf.movies.model
  (:require [clojure.string :as str]
            [malli.core :as m])
  (:import (java.time OffsetDateTime ZoneOffset)
           (java.time.format DateTimeFormatter DateTimeParseException)))

(def allowed-availability #{"announced" "inCinemas" "released" "tba"})

(def iso-formatter DateTimeFormatter/ISO_INSTANT)

(defn- parse-timestamp
  [value]
  (try (when (and value (not (#{"0001-01-01T00:00:00" "0001-01-01T00:00:00Z"} (str value))))
         (-> (if (instance? OffsetDateTime value) value (OffsetDateTime/parse (str value)))
             (.atZoneSameInstant ZoneOffset/UTC)
             (.format iso-formatter)))
       (catch DateTimeParseException _ nil)))

(defn ->iso-utc [value fallback] (or (parse-timestamp value) (fallback)))

(defn- missing-field-error [field] (format "%s is required" (name field)))

(defn- boolean-field-error [field] (format "%s must be a boolean" (name field)))

(defn- positive-int-error [field] (format "%s must be a positive integer" (name field)))

(defn- non-negative-int-error [field] (format "%s must be a non-negative integer" (name field)))

(defn- non-blank-string? [value] (and (string? value) (not (str/blank? value))))

(defn- positive-integer? [value] (and (integer? value) (pos? value)))

(defn- non-negative-integer? [value] (and (integer? value) (not (neg? value))))

(defn- string-field-error [field] (format "%s must be a non-blank string" (name field)))

(def movie-schema
  [:map {:closed false}
   [:title {:optional false :error/message (missing-field-error :title)}
    [:fn {:error/message (missing-field-error :title)} non-blank-string?]]
   [:path {:optional false :error/message (missing-field-error :path)}
    [:fn {:error/message (missing-field-error :path)} non-blank-string?]]
   [:rootFolderPath {:optional false :error/message (missing-field-error :rootFolderPath)}
    [:fn {:error/message (missing-field-error :rootFolderPath)} non-blank-string?]]
   [:monitored {:optional false :error/message (missing-field-error :monitored)}
    [:and [:fn {:error/message (missing-field-error :monitored)} #(not (nil? %))]
     [:fn {:error/message (boolean-field-error :monitored)} #(or (nil? %) (instance? Boolean %))]]]
   [:qualityProfileId {:optional false :error/message (missing-field-error :qualityProfileId)}
    [:and [:fn {:error/message (missing-field-error :qualityProfileId)} #(not (nil? %))]
     [:fn {:error/message (positive-int-error :qualityProfileId)} #(or (nil? %) (positive-integer? %))]]]
   [:minimumAvailability {:optional false :error/message (missing-field-error :minimumAvailability)}
    [:and [:fn {:error/message (missing-field-error :minimumAvailability)} #(not (nil? %))]
     [:fn {:error/message (format "minimumAvailability must be one of %s" allowed-availability)}
      #(or (nil? %) (allowed-availability %))]]]
   [:tmdbId {:optional false :error/message (missing-field-error :tmdbId)}
    [:and [:fn {:error/message (missing-field-error :tmdbId)} #(not (nil? %))]
     [:fn {:error/message (positive-int-error :tmdbId)} #(or (nil? %) (positive-integer? %))]]]
   [:addOptions {:optional true :error/message (missing-field-error :addOptions)}
    [:fn {:error/message (missing-field-error :addOptions)} #(or (nil? %) (map? %))]]
   [:movieFileId {:optional true}
    [:fn {:error/message (non-negative-int-error :movieFileId)} #(or (nil? %) (non-negative-integer? %))]]
   [:movieMetadataId {:optional true}
    [:fn {:error/message (non-negative-int-error :movieMetadataId)} #(or (nil? %) (non-negative-integer? %))]]
   [:targetSystem {:optional true}
    [:fn {:error/message (string-field-error :targetSystem)} #(or (nil? %) (non-blank-string? %))]]
   [:tags {:optional true} [:sequential [:fn {:error/message "tags must contain non-blank strings"} non-blank-string?]]]
   [:year {:optional true} [:fn {:error/message (positive-int-error :year)} #(or (nil? %) (positive-integer? %))]]
   [:titleSlug {:optional true}
    [:fn {:error/message (string-field-error :titleSlug)} #(or (nil? %) (non-blank-string? %))]]
   [:imdbId {:optional true} [:fn {:error/message (string-field-error :imdbId)} #(or (nil? %) (non-blank-string? %))]]])

(defn- title-slug-error
  [{:keys [tmdbId titleSlug]}]
  (when (and (string? titleSlug) (not (str/blank? titleSlug)) (some? tmdbId) (not= titleSlug (str tmdbId)))
    "titleSlug must match tmdbId"))

(defn- error->message
  [{:keys [type path schema]}]
  (or (when (= :malli.core/missing-key type)
        (when-let [field (->> path
                              (filter keyword?)
                              last)]
          (missing-field-error field)))
      (:error/message (m/properties schema))))

(defn- extract-errors
  [explanation]
  (->> (:errors explanation)
       (keep error->message)
       distinct
       vec))

(defn validate
  [movie]
  (let [schema-errors (when-let [explanation (m/explain movie-schema movie)] (extract-errors explanation))
        slug-error    (title-slug-error movie)]
    (->> (concat schema-errors (when slug-error [slug-error]))
         (remove nil?)
         distinct
         vec)))

(defn- canonical-tags
  [tags]
  (->> (or tags [])
       (keep #(when (and (string? %) (not (str/blank? %)))
                (-> %
                    str/trim
                    str/lower-case)))
       distinct
       vec))

(defn- canonical-path
  [path]
  (some-> path
          str/trim))

(defn normalize
  "Returns canonical movie map when input passes validation. Expects `clock` to
  be a 0-argument function returning ISO-8601 UTC string for defaults."
  [movie clock]
  (let [added         (->iso-utc (:added movie) clock)
        last-search   (->iso-utc (:lastSearchTime movie) (constantly added))
        raw-metadata  (:movieMetadataId movie)
        metadata-src  (if (and (integer? raw-metadata) (pos? raw-metadata)) raw-metadata (:tmdbId movie))
        metadata-id   (some-> metadata-src
                              long)
        target-system (let [ts (:targetSystem movie)]
                        (if (and (string? ts) (not (str/blank? ts)))
                          (-> ts
                              str/trim
                              str/lower-case)
                          "radarr"))
        add-options   (or (:addOptions movie) {})
        tags          (canonical-tags (:tags movie))]
    (-> movie
        (assoc :path (canonical-path (:path movie)))
        (assoc :rootFolderPath (canonical-path (:rootFolderPath movie)))
        (assoc :monitored (boolean (:monitored movie)))
        (assoc :qualityProfileId
               (some-> (:qualityProfileId movie)
                       long))
        (assoc :movieFileId
               (some-> (:movieFileId movie)
                       long))
        (assoc :movieMetadataId metadata-id)
        (assoc :tmdbId
               (some-> (:tmdbId movie)
                       long))
        (assoc :year
               (some-> (:year movie)
                       long))
        (assoc :minimumAvailability (:minimumAvailability movie))
        (assoc :added added)
        (assoc :lastSearchTime last-search)
        (assoc :tags tags)
        (assoc :addOptions add-options)
        (assoc :targetSystem target-system))))
