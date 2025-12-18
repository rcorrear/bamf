(ns bamf.movies.rama.module
  "Scaffolding for the Rama MovieModule declarations.

   Phase 1 establishes the namespace and shared metadata so later phases can
   enrich it with concrete depot/p-state definitions."
  (:use [com.rpl.rama] [com.rpl.rama.path])
  (:require [bamf.movies.rama.common :as common]
            [com.rpl.rama.aggs :as aggs]
            [com.rpl.rama.ops :as ops]
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
  (fixed-keys-schema {:added                java.time.Instant
                      :imdb-id              String
                      :last-search-time     java.time.Instant
                      :minimum-availability String
                      :monitored            Boolean
                      :movie-file-id        Long
                      :movie-metadata-id    Long
                      :quality-profile-id   Long
                      :root-folder-path     String
                      :title                String
                      :title-slug           String
                      :tmdb-id              Long
                      :year                 Long}))

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
(defn print-event
  ([level event reason] (print-it level {:event event :reason reason}))
  ([level event reason target value] (print-it level {:reason reason :event event :target target :value value})))

(defn- ->instant
  [value]
  (cond (instance? java.time.Instant value) value
        (string? value)                     (try (java.time.Instant/parse value) (catch Exception _ nil))
        :else                               nil))

(defmodule
 MovieModule
 [setup topologies]
 (declare-depot setup *movie-saves-depot (hash-by :tmdb-id))
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
    (<<switch
     *event
     (case> :movie.created)
     (print-it :info {:event *event})
     ;; -------- :movie.created (create-only) --------
     (identity *payload
               :>
               {:keys [*imdb-id *minimum-availability *monitor *monitored *movie-file-id *movie-metadata-id
                       *quality-profile-id *root-folder-path *tags *target-system *title *title-slug *tmdb-id *year]})
     (|hash$$ $$movies *tmdb-id)
     ;; -------- Check if movie exists by :tmdb-id --------
     (local-select> (keypath *tmdb-id) $$movies-id-by-tmdb-id :> *existing-movie-id)
     (<<if
      (nil? *existing-movie-id)
      ;; -------- Generate a new movie id --------
      (print-event :debug :movie/save :generating-new-id)
      (java-macro! (.genId idgen "*new-movie-id"))
      (|hash$$ $$movies *new-movie-id)
      (print-event :info :movie/save {:id *new-movie-id})
      ;; -------- Save movie to $$movies PState --------
      (print-event :debug :movie/save :creating-movie)
      (identity {:added                (java.time.Instant/now)
                 :imdb-id              *imdb-id
                 :last-search-time     nil
                 :minimum-availability *minimum-availability
                 :monitored            *monitored
                 :movie-file-id        *movie-file-id
                 :movie-metadata-id    *movie-metadata-id
                 :quality-profile-id   *quality-profile-id
                 :root-folder-path     *root-folder-path
                 :title                *title
                 :title-slug           *title-slug
                 :tmdb-id              *tmdb-id
                 :year                 *year}
                :>
                *movie-row)
      (print-event :debug :movie/save :saving-movie {:id *new-movie-id} *movie-row)
      (local-transform> [(keypath *new-movie-id) (termval *movie-row)] $$movies)
      ;; -------- Save movie id to secondary PStates -------- -------- Save movie-metadata-id --------
      (|hash$$ $$movies-id-by-metadata-id *movie-metadata-id)
      (print-event :debug :movie/save :saving-movie-metadata-id {:movie-metadata-id *movie-metadata-id} *new-movie-id)
      (local-transform> [(keypath *movie-metadata-id) (termval *new-movie-id)] $$movies-id-by-metadata-id)
      ;; -------- Save tmdb-id --------
      (|hash$$ $$movies-id-by-tmdb-id *tmdb-id)
      (print-event :debug :movie/save :saving-tmdb-id {:tmdb-id *tmdb-id} *new-movie-id)
      (local-transform> [(keypath *tmdb-id) (termval *new-movie-id)] $$movies-id-by-tmdb-id)
      ;; -------- Save monitor --------
      (|hash$$ $$movies-ids-by-monitor *monitor)
      (print-event :debug :movie/save :saving-monitor {:monitor *monitor} *new-movie-id)
      (local-transform> [(keypath *monitor) NONE-ELEM (termval *new-movie-id)] $$movies-ids-by-monitor)
      ;; -------- Save monitored --------
      (<<if *monitored
            (|hash$$ $$movies-ids-by-monitored *monitored)
            (print-event :debug :movie/save :saving-monitored {:monitored *monitored} *new-movie-id)
            (local-transform> [(keypath true) NONE-ELEM (termval *new-movie-id)] $$movies-ids-by-monitored))
      ;; -------- Save tags --------
      (ops/explode *tags :> *tag)
      (|hash$$ $$movies-ids-by-tag *tag)
      (print-event :debug :movie/save :saving-tags {:tags *tags} *new-movie-id)
      (local-transform> [(keypath *tag) NONE-ELEM (termval *new-movie-id)] $$movies-ids-by-tag)
      ;; -------- Save target-system --------
      (|hash$$ $$movies-ids-by-target-system *target-system)
      (print-event :debug :movie/save :saving-target-system {:target-system *target-system} *new-movie-id)
      (local-transform> [(keypath *target-system) NONE-ELEM (termval *new-movie-id)] $$movies-ids-by-target-system)
      ;; -------- Return newly generated id --------
      (print-event :debug :movie/save {:returning-new-id {:id *new-movie-id}})
      (ack-return> {:status :stored :movie {:id *new-movie-id}})
      (else>)
      (print-event :debug :movie/save {:returning-existing-id {:id *existing-movie-id}})
      (ack-return> {:status :duplicate}))
     (case> :movie.updated)
     (identity *payload
               :>
               {:keys [*last-search-time *minimum-availability *monitored *movie-metadata-id *quality-profile-id
                       *root-folder-path *tmdb-id]})
     (identity *last-search-time :> *incoming-last-search)
     (identity *minimum-availability :> *incoming-minimum-availability)
     (identity *monitored :> *incoming-monitored)
     (identity *movie-metadata-id :> *incoming-movie-metadata-id)
     (identity *quality-profile-id :> *incoming-quality-profile-id)
     (identity *root-folder-path :> *incoming-root-folder-path)
     (|hash$$ $$movies *tmdb-id)
     (local-select> (keypath *tmdb-id) $$movies-id-by-tmdb-id :> *existing-movie-id)
     (<<if
      (not (nil? *existing-movie-id))
      (|hash$$ $$movies *existing-movie-id)
      (local-select> (keypath *existing-movie-id) $$movies :> *existing-movie-row)
      (identity *existing-movie-row
                :>
                {:keys [*added *imdb-id *last-search-time *minimum-availability *existing-monitored *movie-file-id
                        *movie-metadata-id *quality-profile-id *root-folder-path *title *title-slug *tmdb-id *year]})
      (<<if *incoming-last-search
            (identity (->instant *incoming-last-search) :> *parsed-last-search)
            (else>)
            (identity nil :> *parsed-last-search))
      (<<if (nil? *parsed-last-search)
            (identity *last-search-time :> *resolved-last-search)
            (else>)
            (identity *parsed-last-search :> *resolved-last-search))
      (<<if *incoming-minimum-availability
            (identity *incoming-minimum-availability :> *resolved-minimum-availability)
            (else>)
            (identity *minimum-availability :> *resolved-minimum-availability))
      ;; movieMetadataId is immutable for updates; keep existing
      (identity *movie-metadata-id :> *resolved-movie-metadata-id)
      (<<if *incoming-quality-profile-id
            (identity *incoming-quality-profile-id :> *resolved-quality-profile-id)
            (else>)
            (identity *quality-profile-id :> *resolved-quality-profile-id))
      (<<if *incoming-root-folder-path
            (identity *incoming-root-folder-path :> *resolved-root-folder-path)
            (else>)
            (identity *root-folder-path :> *resolved-root-folder-path))
      (<<if (nil? *incoming-monitored)
            (identity *existing-monitored :> *resolved-monitored)
            (else>)
            (identity *incoming-monitored :> *resolved-monitored))
      (identity {:added                *added
                 :imdb-id              *imdb-id
                 :last-search-time     *resolved-last-search
                 :minimum-availability *resolved-minimum-availability
                 :monitored            *resolved-monitored
                 :movie-file-id        *movie-file-id
                 :movie-metadata-id    *resolved-movie-metadata-id
                 :quality-profile-id   *resolved-quality-profile-id
                 :root-folder-path     *resolved-root-folder-path
                 :title                *title
                 :title-slug           *title-slug
                 :tmdb-id              *tmdb-id
                 :year                 *year}
                :>
                *movie-row)
      (local-transform> [(keypath *existing-movie-id) (termval *movie-row)] $$movies)
      (<<if
       (not (nil? *incoming-monitored))
       (|hash$$ $$movies-ids-by-monitored true)
       (local-select> (keypath true) $$movies-ids-by-monitored :> *monitored-set)
       (<<if (nil? *monitored-set) (identity #{} :> *monitored-set) (else>) (identity *monitored-set :> *monitored-set))
       (<<if *incoming-monitored
             (identity (conj *monitored-set *existing-movie-id) :> *updated-monitored-set)
             (else>)
             (identity (disj *monitored-set *existing-movie-id) :> *updated-monitored-set))
       (local-transform> [(keypath true) (termval *updated-monitored-set)] $$movies-ids-by-monitored)
       (else>)
       (identity nil))
      (ack-return> {:status :updated :movie {:id *existing-movie-id}})
      (else>)
      (ack-return> {:status :not-found :movie {:tmdb-id *tmdb-id}}))
     (default>)
     (ack-return> {:status :not-found :event *event})))))
