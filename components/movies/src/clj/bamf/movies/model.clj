(ns bamf.movies.model
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [malli.core :as m]
            [tick.core :as t])
  (:import (java.time OffsetDateTime ZoneOffset)
           (java.time.format DateTimeFormatter DateTimeParseException)
           (java.time.temporal ChronoUnit)))

(def allowed-availability #{"announced" "inCinemas" "released" "tba" "deleted"})

(def iso-formatter DateTimeFormatter/ISO_INSTANT)

(def ^:private radarr-sentinel-timestamps
  "Radarr uses 0001-01-01T00:00:00(Z) as a placeholder for unset instants
   (see components/movies/test/resources/movie-save-request.json). Treat as nil."
  #{"0001-01-01T00:00:00" "0001-01-01T00:00:00Z"})

(defn- iso-instant-formatter [dt] (t/format :iso-instant dt))

(defn- format-seconds
  [dt]
  (-> dt
      (t/zoned-date-time)
      (t/truncate :seconds)
      (iso-instant-formatter)))

(defn- parse-timestamp
  [value]
  (try (when (and value (not (radarr-sentinel-timestamps (str value))))
         (-> (if (instance? OffsetDateTime value) value (OffsetDateTime/parse (str value)))
             format-seconds))
       (catch DateTimeParseException _ nil)))

(defn ->iso-utc
  [value fallback]
  (or (parse-timestamp value)
      (some-> (fallback)
              parse-timestamp)))

(defn- external-field-name [field] (csk/->camelCase (name field)))

(defn- missing-field-error [field] (format "%s is required" (external-field-name field)))

(defn- boolean-field-error [field] (format "%s must be a boolean" (external-field-name field)))

(defn- positive-int-error [field] (format "%s must be a positive integer" (external-field-name field)))

(defn- non-negative-int-error [field] (format "%s must be a non-negative integer" (external-field-name field)))

(defn- non-blank-string? [value] (and (string? value) (not (str/blank? value))))

(defn- positive-integer? [value] (and (integer? value) (pos? value)))

(defn- non-negative-integer? [value] (and (integer? value) (not (neg? value))))

(defn- string-field-error [field] (format "%s must be a non-blank string" (external-field-name field)))

(def movie-schema
  [:map {:closed false}
   [:id {:optional true :error/message (non-negative-int-error :id)}
    [:fn {:error/message (non-negative-int-error :id)} #(or (nil? %) (non-negative-integer? %))]]
   [:title {:optional false :error/message (missing-field-error :title)}
    [:fn {:error/message (missing-field-error :title)} non-blank-string?]]
   [:original-title {:optional true} [:maybe string?]]
   [:path {:optional true} [:fn {:error/message (string-field-error :path)} #(or (nil? %) (non-blank-string? %))]]
   [:root-folder-path {:optional true}
    [:fn {:error/message (string-field-error :root-folder-path)} #(or (nil? %) (non-blank-string? %))]]
   [:folder {:optional true} [:maybe string?]] [:folder-name {:optional true} [:maybe string?]]
   [:monitored {:optional false :error/message (missing-field-error :monitored)}
    [:and [:fn {:error/message (missing-field-error :monitored)} #(not (nil? %))]
     [:fn {:error/message (boolean-field-error :monitored)} #(or (nil? %) (instance? Boolean %))]]]
   [:quality-profile-id {:optional false :error/message (missing-field-error :quality-profile-id)}
    [:and [:fn {:error/message (missing-field-error :quality-profile-id)} #(not (nil? %))]
     [:fn {:error/message (positive-int-error :quality-profile-id)} #(or (nil? %) (positive-integer? %))]]]
   [:minimum-availability {:optional false :error/message (missing-field-error :minimum-availability)}
    [:and [:fn {:error/message (missing-field-error :minimum-availability)} #(not (nil? %))]
     [:fn {:error/message (format "minimumAvailability must be one of %s" allowed-availability)}
      #(or (nil? %) (allowed-availability %))]]]
   [:status {:optional true :error/message (format "status must be one of %s" allowed-availability)}
    [:fn {:error/message (format "status must be one of %s" allowed-availability)}
     #(or (nil? %) (allowed-availability %))]]
   [:tmdb-id {:optional false :error/message (missing-field-error :tmdb-id)}
    [:and [:fn {:error/message (missing-field-error :tmdb-id)} #(not (nil? %))]
     [:fn {:error/message (positive-int-error :tmdb-id)} #(or (nil? %) (positive-integer? %))]]]
   [:add-options {:optional true} [:fn {:error/message (missing-field-error :add-options)} #(or (nil? %) (map? %))]]
   [:movie-file-id {:optional true}
    [:fn {:error/message (non-negative-int-error :movie-file-id)} #(or (nil? %) (non-negative-integer? %))]]
   [:movie-file {:optional true} [:maybe map?]] [:collection {:optional true} [:maybe map?]]
   [:statistics {:optional true} [:maybe map?]] [:ratings {:optional true} [:maybe map?]]
   [:media-info {:optional true} [:maybe map?]]
   [:movie-metadata-id {:optional true}
    [:fn {:error/message (non-negative-int-error :movie-metadata-id)} #(or (nil? %) (non-negative-integer? %))]]
   [:size-on-disk {:optional true}
    [:fn {:error/message (non-negative-int-error :size-on-disk)} #(or (nil? %) (non-negative-integer? %))]]
   [:target-system {:optional true}
    [:fn {:error/message (string-field-error :target-system)} #(or (nil? %) (non-blank-string? %))]]
   [:tags {:optional true}
    [:sequential
     [:or [:fn {:error/message "tags must be non-blank strings or integers"} #(and (string? %) (not (str/blank? %)))]
      [:fn {:error/message "tags must be non-blank strings or integers"} #(integer? %)]]]]
   [:year {:optional true} [:fn {:error/message (positive-int-error :year)} #(or (nil? %) (positive-integer? %))]]
   [:secondary-year {:optional true}
    [:fn {:error/message (positive-int-error :secondary-year)} #(or (nil? %) (positive-integer? %))]]
   [:runtime {:optional true}
    [:fn {:error/message (non-negative-int-error :runtime)} #(or (nil? %) (non-negative-integer? %))]]
   [:title-slug {:optional true} [:maybe string?]] [:imdb-id {:optional true} [:maybe string?]]
   [:original-language {:optional true} [:maybe map?]] [:alternate-titles {:optional true} [:maybe [:sequential map?]]]
   [:overview {:optional true} [:maybe string?]] [:in-cinemas {:optional true} [:maybe string?]]
   [:physical-release {:optional true} [:maybe string?]] [:digital-release {:optional true} [:maybe string?]]
   [:release-date {:optional true} [:maybe string?]] [:remote-poster {:optional true} [:maybe string?]]
   [:website {:optional true} [:maybe string?]] [:you-tube-trailer-id {:optional true} [:maybe string?]]
   [:studio {:optional true} [:maybe string?]] [:clean-title {:optional true} [:maybe string?]]
   [:sort-title {:optional true} [:maybe string?]] [:genres {:optional true} [:maybe [:sequential string?]]]
   [:keywords {:optional true} [:maybe [:sequential string?]]] [:has-file {:optional true} [:maybe boolean?]]
   [:quality-cutoff-not-met {:optional true} [:maybe boolean?]] [:is-available {:optional true} [:maybe boolean?]]
   [:images {:optional true} [:maybe [:sequential map?]]] [:popularity {:optional true} [:maybe number?]]])

(defn- title-slug-error
  [{:keys [tmdb-id title-slug]}]
  (when (and (string? title-slug) (not (str/blank? title-slug)) (some? tmdb-id) (not= title-slug (str tmdb-id)))
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
       (keep (fn [tag]
               (cond (integer? tag)                             (str tag)
                     (and (string? tag) (not (str/blank? tag))) (let [trimmed (str/trim tag)]
                                                                  (if (re-matches #"-?\d+" trimmed)
                                                                    (try (str (Long/parseLong trimmed))
                                                                         (catch Exception _ (str/lower-case trimmed)))
                                                                    (str/lower-case trimmed)))
                     :else                                      nil)))
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
        last-search   (->iso-utc (:last-search-time movie) (constantly nil))
        in-cinemas    (->iso-utc (:in-cinemas movie) (constantly nil))
        physical-rel  (->iso-utc (:physical-release movie) (constantly nil))
        digital-rel   (->iso-utc (:digital-release movie) (constantly nil))
        release-date  (->iso-utc (:release-date movie) (constantly nil))
        raw-metadata  (:movie-metadata-id movie)
        metadata-src  (if (and (integer? raw-metadata) (pos? raw-metadata)) raw-metadata (:tmdb-id movie))
        metadata-id   (some-> metadata-src
                              long)
        target-system (let [ts (:target-system movie)]
                        (if (and (string? ts) (not (str/blank? ts)))
                          (-> ts
                              str/trim
                              str/lower-case)
                          "radarr"))
        add-options   (or (:add-options movie) {})
        tags          (canonical-tags (:tags movie))]
    (-> movie
        (assoc :path (canonical-path (:path movie)))
        (assoc :root-folder-path (canonical-path (:root-folder-path movie)))
        (assoc :folder (canonical-path (:folder movie)))
        (assoc :folder-name (canonical-path (:folder-name movie)))
        (assoc :monitored (boolean (:monitored movie)))
        (assoc :quality-profile-id
               (some-> (:quality-profile-id movie)
                       long))
        (assoc :movie-file-id
               (some-> (:movie-file-id movie)
                       long))
        (assoc :runtime
               (some-> (:runtime movie)
                       long))
        (assoc :size-on-disk
               (or (some-> (:size-on-disk movie)
                           long)
                   0))
        (assoc :secondary-year
               (some-> (:secondary-year movie)
                       long))
        (assoc :id
               (some-> (:id movie)
                       long))
        (assoc :movie-metadata-id metadata-id)
        (assoc :tmdb-id
               (some-> (:tmdb-id movie)
                       long))
        (assoc :year
               (some-> (:year movie)
                       long))
        (assoc :minimum-availability (:minimum-availability movie))
        (assoc :status (:status movie))
        (assoc :added added)
        (assoc :last-search-time last-search)
        (assoc :in-cinemas in-cinemas)
        (assoc :physical-release physical-rel)
        (assoc :digital-release digital-rel)
        (assoc :release-date release-date)
        (assoc :tags tags)
        (assoc :add-options add-options)
        (assoc :target-system target-system))))
