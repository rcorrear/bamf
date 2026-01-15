(ns bamf.movies.model
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [malli.core :as m]
            [tick.core :as t])
  (:import (java.time OffsetDateTime)
           (java.time.format DateTimeParseException)))

(def allowed-availability #{"announced" "inCinemas" "released" "tba" "deleted"})

(def ^:private metadata-status-tokens ["deleted" "tba" "announced" "inCinemas" "released"])

(def ^:private metadata-status-token-by-input
  (into {} (map (fn [token] [(str/lower-case token) token]) metadata-status-tokens)))

(def ^:private metadata-status-namespace "bamf.movies.metadata-status")

(def ^:private metadata-status-by-token
  (into {} (map (fn [token] [token (keyword metadata-status-namespace token)]) metadata-status-tokens)))

(def ^:private metadata-status-token-by-keyword (into {} (map (fn [[token kw]] [kw token]) metadata-status-by-token)))

(def ^:private metadata-status-requirement (format "must be one of %s" (str/join ", " metadata-status-tokens)))

(def ^:private metadata-status-message (str "status " metadata-status-requirement))

(def metadata-fields
  "MovieMetadata field keys in kebab-case."
  #{:images :genres :sort-title :clean-title :original-title :clean-original-title :original-language :status
    :last-info-sync :runtime :in-cinemas :physical-release :digital-release :year :secondary-year :ratings
    :recommendations :certification :you-tube-trailer-id :studio :overview :website :popularity :collection})

(def ^:private metadata-fields-camel
  (->> metadata-fields
       (map (comp csk/->camelCase name))
       set))

(defn- metadata-key->kebab
  [k]
  (cond (keyword? k) (when (contains? metadata-fields k) k)
        (string? k)  (when (contains? metadata-fields-camel k) (keyword (csk/->kebab-case k)))
        :else        nil))

(defn extract-metadata
  "Return only recognized metadata keys from a payload. Supports camelCase strings
   and kebab-case keywords without accepting aliases."
  [movie]
  (reduce-kv (fn [acc k v]
               (if-let [k* (metadata-key->kebab k)]
                 (assoc acc k* v)
                 acc))
             {}
             (or movie {})))

(defn- metadata-status-token
  [value]
  (cond (nil? value)     nil
        (keyword? value) (get metadata-status-token-by-keyword value)
        (string? value)  (some-> value
                                 str/trim
                                 str/lower-case
                                 metadata-status-token-by-input)
        :else            nil))

(defn normalize-metadata-status
  "Normalize status values to namespaced keywords for internal storage."
  [value]
  (cond (nil? value)     nil
        (keyword? value) (when (contains? metadata-status-token-by-keyword value) value)
        (string? value)  (when-let [token (metadata-status-token value)] (get metadata-status-by-token token))
        :else            nil))

(defn- normalize-metadata-value
  [k v]
  (case k
    :status         (normalize-metadata-status v)
    :year           (some-> v
                            int)
    :secondary-year (some-> v
                            int)
    v))

(defn normalize-metadata
  "Normalize metadata values for internal storage."
  [metadata]
  (reduce-kv (fn [acc k v] (assoc acc k (normalize-metadata-value k v))) {} (or metadata {})))

(defn serialize-metadata
  "Prepare metadata values for HTTP responses (string tokens, no nils)."
  [metadata]
  (let [metadata (reduce-kv (fn [acc k v]
                              (let [value (if (= k :status) (metadata-status-token v) v)]
                                (if (nil? value) acc (assoc acc k value))))
                            {}
                            (or metadata {}))]
    (when (seq metadata) metadata)))

(defn- metadata-status? [value] (boolean (normalize-metadata-status value)))

(declare external-field-name)

(defn- metadata-field-error [field message] (format "%s %s" (external-field-name field) message))

(def ^:private metadata-field-specs
  {:images               {:pred    #(or (nil? %) (and (sequential? %) (every? map? %)))
                          :message "must be a list of objects"}
   :genres               {:pred    #(or (nil? %) (and (sequential? %) (every? string? %)))
                          :message "must be a list of strings"}
   :sort-title           {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :clean-title          {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :original-title       {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :clean-original-title {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :original-language    {:pred #(or (nil? %) (map? %)) :message "must be an object"}
   :status               {:pred #(or (nil? %) (metadata-status? %)) :message metadata-status-requirement}
   :last-info-sync       {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :runtime              {:pred #(or (nil? %) (integer? %)) :message "must be an integer"}
   :in-cinemas           {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :physical-release     {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :digital-release      {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :year                 {:pred #(or (nil? %) (integer? %)) :message "must be an integer"}
   :secondary-year       {:pred #(or (nil? %) (integer? %)) :message "must be an integer"}
   :ratings              {:pred #(or (nil? %) (map? %)) :message "must be an object"}
   :recommendations      {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :certification        {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :you-tube-trailer-id  {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :studio               {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :overview             {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :website              {:pred #(or (nil? %) (string? %)) :message "must be a string"}
   :popularity           {:pred #(or (nil? %) (number? %)) :message "must be a number"}
   :collection           {:pred #(or (nil? %) (map? %)) :message "must be an object"}})

(defn validate-metadata
  "Return validation errors for metadata fields or an empty vector."
  [metadata]
  (let [metadata (extract-metadata metadata)]
    (->> metadata
         (keep (fn [[field value]]
                 (when-let [{:keys [pred message]} (get metadata-field-specs field)]
                   (when-not (pred value) (metadata-field-error field message)))))
         distinct
         vec)))

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
   [:status {:optional true :error/message metadata-status-message}
    [:fn {:error/message metadata-status-message} #(or (nil? %) (metadata-status? %))]]
   [:tmdb-id {:optional false :error/message (missing-field-error :tmdb-id)}
    [:and [:fn {:error/message (missing-field-error :tmdb-id)} #(not (nil? %))]
     [:fn {:error/message (positive-int-error :tmdb-id)} #(or (nil? %) (positive-integer? %))]]]
   [:add-options {:optional true} [:fn {:error/message (missing-field-error :add-options)} #(or (nil? %) (map? %))]]
   [:movie-file-id {:optional true}
    [:fn {:error/message (non-negative-int-error :movie-file-id)} #(or (nil? %) (non-negative-integer? %))]]
   [:movie-file {:optional true} [:maybe map?]] [:collection {:optional true} [:maybe map?]]
   [:statistics {:optional true} [:maybe map?]] [:ratings {:optional true} [:maybe map?]]
   [:media-info {:optional true} [:maybe map?]]
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
                       int))
        (assoc :id
               (some-> (:id movie)
                       long))
        (assoc :tmdb-id
               (some-> (:tmdb-id movie)
                       long))
        (assoc :year
               (some-> (:year movie)
                       int))
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
