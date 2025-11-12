(ns bamf.movies.rama.client.pstate
  (:require [bamf.movies.rama.common :as common]
            [com.rpl.rama :as rama]
            [com.rpl.rama.path :refer [keypath]]))

(defn- ensure-ipc
  [env]
  (let [ipc (or (:ipc env) (get-in env [:movies/env :ipc]) (get-in env [:runtime-state :movies/env :ipc]))]
    (or ipc (throw (IllegalStateException. "Movies Rama env missing :ipc handle")))))

(defn- select-one
  [env pstate-name k pkey]
  (let [ipc    (ensure-ipc env)
        pstate (rama/foreign-pstate ipc common/module-name pstate-name)]
    (rama/foreign-select-one (keypath k) pstate {:pkey pkey})))

(defn- lookup-id [env index-name k pkey] (select-one env index-name k pkey))

(defn- to-set [value] (when value (into #{} value)))

(defn movie-by-id ([env movie-id pkey] (select-one env common/movie-by-id-pstate-name movie-id pkey)))

(defn movie-id-by-metadata-id
  ([env metadata-id pkey] (lookup-id env common/movies-id-by-metadata-id-pstate-name metadata-id pkey)))

(defn movie-id-by-tmdb-id ([env tmdb-id pkey] (lookup-id env common/movies-id-by-tmdb-id-pstate-name tmdb-id pkey)))

(defn movie-by-path ([env path] (movie-by-path env path path)) ([env _ _] nil))

(defn movie-ids-by-tag
  ([env tag pkey]
   (some-> (select-one env common/movies-ids-by-tag-pstate-name tag pkey)
           to-set)))

(defn movie-ids-by-target-system
  ([env target-system pkey]
   (some-> (select-one env common/movies-ids-by-target-system-pstate-name target-system pkey)
           to-set)))

(defn movie-ids-by-monitor
  ([env monitor pkey]
   (some-> (select-one env common/movies-ids-by-monitor-pstate-name monitor pkey)
           to-set)))

(defn movie-ids-by-monitored
  ([env pkey]
   (some-> (select-one env common/movies-ids-by-monitored-pstate-name true pkey)
           to-set)))
