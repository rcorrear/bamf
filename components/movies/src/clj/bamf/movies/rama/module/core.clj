(ns bamf.movies.rama.module.core
  "Scaffolding for the Rama MovieModule declarations.

   Phase 1 establishes the namespace and shared metadata so later phases can
   enrich it with concrete depot/p-state definitions."
  (:use [com.rpl.rama] [com.rpl.rama.path])
  (:require [bamf.movies.rama.common :as common]
            [bamf.movies.rama.module.create :as create]
            [bamf.movies.rama.module.helpers :as helpers]
            [bamf.movies.rama.module.state :as state]
            [bamf.movies.rama.module.update :as update])
  (:import [com.rpl.rama.helpers ModuleUniqueIdPState]))

(defmodule MovieModule
           [setup topologies]
           (declare-depot setup *movie-saves-depot (hash-by :tmdb-id))
           (let [topology (stream-topology topologies common/movies-etl-name)]
             (state/declare-index-pstates! topology)
             (state/declare-movies-pstate! topology)
             (.declarePState (ModuleUniqueIdPState. "$$id") topology)
             (<<sources topology
                        (source> *movie-saves-depot :> {:keys [*event *payload]})
                        (helpers/print-it :info {:event *event})
                        (<<switch *event
                                  (case> :movie.created)
                                  (create/movie-create *payload)
                                  (case> :movie.updated)
                                  (update/movie-update *payload)
                                  (default>)
                                  (ack-return> {:status :not-found :event *event})))))
