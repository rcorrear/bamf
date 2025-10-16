(ns bamf.movies.rama.module
  "Scaffolding for the Rama MovieModule declarations.

   Phase 1 establishes the namespace and shared metadata so later phases can
   enrich it with concrete depot/p-state definitions."
  (:use [com.rpl.rama] [com.rpl.rama.path])
  (:require [bamf.movies.rama.common :as common]
            [taoensso.telemere :as t])
  (:import [com.rpl.rama.helpers ModuleUniqueIdPState]))

(def ^:private $$movies nil)
(def ^:private $$movies-id-by-metadata-id nil)
(def ^:private $$movies-id-by-tmdb-id nil)
(def ^:private $$movies-ids-by-monitor nil)
(def ^:private $$movies-ids-by-monitored nil)
(def ^:private $$movies-ids-by-tag nil)
(def ^:private $$movies-ids-by-target-system nil)

(def ^:private movie-row-schema
  (fixed-keys-schema {:added               java.time.Instant
                      :imdbId              String
                      :lastSearchTime      java.time.Instant
                      :minimumAvailability String
                      :movieFileId         Long
                      :movieMetadataId     Long
                      :qualityProfileId    Long
                      :rootFolderPath      String
                      :title               String
                      :titleSlug           String
                      :tmdbId              Long
                      :year                Long}))

(def ^:private movies-pstate-schema (map-schema Long movie-row-schema))
(def ^:private movies-id-by-metadata-id-schema (map-schema Long Long))
(def ^:private movies-id-by-tmdb-id-schema (map-schema Long Long))
(def ^:private movies-ids-by-monitor-schema (map-schema String (set-schema Long)))
(def ^:private movies-ids-by-monitored-schema {Boolean (set-schema Long)})
(def ^:private movies-ids-by-tag-schema (map-schema String (set-schema Long)))
(def ^:private movies-ids-by-target-system-schema (map-schema String (set-schema Long)))

(defn- declare-movies-pstate! [topology] (declare-pstate topology $$movies movies-pstate-schema))

(defn- declare-index-pstates!
  [topology]
  (declare-pstate topology $$movies-id-by-metadata-id movies-id-by-metadata-id-schema)
  (declare-pstate topology $$movies-id-by-tmdb-id movies-id-by-tmdb-id-schema)
  (declare-pstate topology $$movies-ids-by-monitor movies-ids-by-monitor-schema)
  (declare-pstate topology $$movies-ids-by-monitored movies-ids-by-monitored-schema)
  (declare-pstate topology $$movies-ids-by-tag movies-ids-by-tag-schema)
  (declare-pstate topology $$movies-ids-by-target-system movies-ids-by-target-system-schema))

(defn print-it ([args] (t/log! args)) ([level args] (t/log! level args)))

(defn ->printable
  [v]
  (cond (instance? java.util.Map v)        (into {} v) ; ContiguousMap, etc.
        (instance? java.util.Collection v) (into [] v) ; Java lists/sets
        :else                              v))

(defmodule
 MovieModule
 [setup topologies]
 (declare-depot setup *movie-saves-depot (hash-by :tmdbId))
 (let [topology (stream-topology topologies common/movies-etl-name)
       idgen    (ModuleUniqueIdPState. "$$id")]
   (declare-index-pstates! topology)
   (declare-movies-pstate! topology)
   (.declarePState idgen topology)
   (<<sources
    topology
    ;; 1) Read events {:event .. :payload ..}
    (source> *movie-saves-depot :> {:keys [*event *payload]})
    ;; 2) Branch on event type
    (<<if (= *event :movie.created)
          (print-it :info {:event *event})
          ;; -------- :movie.created (create-only) --------
          (identity *payload
                    :>
                    {:keys [*imdbId *minimumAvailability *monitor *monitored *movieFileId *movieMetadataId
                            *qualityProfileId *rootFolderPath *tags *targetSystem *title *titleSlug *tmdbId *year]})
          (print-it :info {:event *event :movieMetadataId *movieMetadataId :tmdbId *tmdbId})
          (|hash$$ $$movies *tmdbId)
          ;; -------- Check if movie exists by :tmdbId --------
          (local-select> (keypath *tmdbId) $$movies-id-by-tmdb-id :> *existing-movie-id)
          (<<if
           (nil? *existing-movie-id)
           ;; -------- Generate a new movie id --------
           (print-it :info {:event :movie/save :reason :generating-id})
           (java-macro! (.genId idgen "*new-movie-id"))
           (print-it :info {:event :movie/save :reason :generated-id :with-id *new-movie-id})
           ;; -------- Save movie to $$movies PState --------
           (print-it :info {:event :movie/save :reason :creating-movie})
           (identity {:added               (java.time.Instant/now)
                      :imdbId              *imdbId
                      :lastSearchTime      nil
                      :minimumAvailability *minimumAvailability
                      :movieFileId         *movieFileId
                      :movieMetadataId     *movieMetadataId
                      :qualityProfileId    *qualityProfileId
                      :rootFolderPath      *rootFolderPath
                      :title               *title
                      :titleSlug           *titleSlug
                      :tmdbId              *tmdbId
                      :year                *year}
                     :>
                     *movie-row)
           (print-it :info {:event :movie/save :reason :saving-movie :movie-row *movie-row})
           (local-transform> [(keypath *new-movie-id) (termval *movie-row)] $$movies)
           (print-it :info {:event :movie/save :reason :create :movie-id *new-movie-id})
           ;; -------- Save movie id to secondary PStates --------
           (print-it :info {:event :movie/save :reason :saving-movieMetadataId :movieMetadataId *movieMetadataId})
           (local-transform> [(keypath *movieMetadataId) (termval *new-movie-id)] $$movies-id-by-metadata-id)
           (print-it :info {:event :movie/save :reason :saving-tmdbId :tmdbId *tmdbId})
           (local-transform> [(keypath *tmdbId) (termval *new-movie-id)] $$movies-id-by-tmdb-id)
           (print-it :info {:event :movie/save :reason :saving-monitor :monitor *monitor})
           (local-transform> [(keypath *monitor) NONE-ELEM (termval *new-movie-id)] $$movies-ids-by-monitor)
           (<<if *monitored
                 (print-it :info {:event :movie/save :reason :saving-monitored :monitored *monitored})
                 (local-transform> [(keypath true) NONE-ELEM (termval *new-movie-id)] $$movies-ids-by-monitored))
           (print-it :info {:event :movie/save :reason :saving-tags :tags *tags})
           (local-transform> [(apply multi-path *tags) NONE-ELEM (termval *new-movie-id)] $$movies-ids-by-tag)
           (print-it :info {:event :movie/save :reason :saving-targetSystem :targetSystem *targetSystem})
           (local-transform> [(keypath *targetSystem) NONE-ELEM (termval *new-movie-id)] $$movies-ids-by-target-system)
           ;; -------- Return newly generated id --------
           (print-it :info {:event :movie/saved :reason :returning-movie})
           (ack-return> {:status :stored :movie {:id *new-movie-id}})
           ;; ;; -------- UPDATE (update-only) --------
           ;;               ((:id *payload) :> *id)
           ;;               (<<if (nil? *id)
           ;;                     (print-it :info {:event :movie/update-failed :reason
           ;;                     :missing-id})
           ;;                     (ack-return> {:status :error :op :update :reason
           ;;                     :missing-id})
           ;;                     (local-select> (keypath *id) $$movies :> *existing)
           ;;                     (<<if (nil? *existing)
           ;;                           (print-it :info {:event :movie/update-failed
           ;;                           :reason :not-found :id *id})
           ;;                           (ack-return> {:status :error :op :update :id *id
           ;;                           :reason :not-found})
           ;;           ;; merge strategy: payload wins; ensure :id is *id
           ;;                           ((assoc (merge *existing *payload) :id *id) :>
           ;;                           *to-save)
           ;;                           (local-transform> [(keypath *id) (termval
           ;;                           *to-save)] $$movies)
           ;;                           (print-it :info {:event :movie/updated :id *id})
           ;;                           (ack-return> {:status :ok :op :update :id *id}))
          )))))
