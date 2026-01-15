(ns bamf.movies.rama.client.pstate
  (:require [bamf.movies.rama.common :as common]
            [com.rpl.rama :as rama]
            [com.rpl.rama.path :refer [keypath]]
            [taoensso.telemere :as t]))

(defn- to-set [value] (when value (into #{} value)))

(defn- ensure-ipc
  [env]
  (let [ipc (or (:ipc env) (get-in env [:movies/env :ipc]) (get-in env [:runtime-state :movies/env :ipc]))]
    (or ipc (throw (IllegalStateException. "Movies Rama env missing :ipc handle")))))

(defn- select-one
  ([env pstate-name k] (select-one env pstate-name k nil))
  ([env pstate-name k options]
   (let [ipc    (ensure-ipc env)
         pstate (rama/foreign-pstate ipc common/module-name pstate-name)]
     (if options
       (do (t/log! {:level :debug :reason :pstate/select-one}
                   (format "executing foreign-select-one %s %s %s" pstate-name k options))
           (rama/foreign-select-one (keypath k) pstate options))
       (do (t/log! {:level :debug :reason :pstate/select-one}
                   (format "executing foreign-select-one %s %s" pstate-name k))
           (rama/foreign-select-one (keypath k) pstate))))))

(defn- lookup-id [env index-name k] (select-one env index-name k))

(defn movie-by-id [env movie-id] (select-one env common/movie-by-id-pstate-name movie-id))

(defn metadata-by-movie-id [env movie-id] (select-one env common/metadata-by-movie-id-pstate-name movie-id))

(defn movie-id-by-tmdb-id [env tmdb-id] (lookup-id env common/movies-id-by-tmdb-id-pstate-name tmdb-id))

(defn movie-ids-by-tag
  [env tag]
  (some-> (select-one env common/movies-ids-by-tag-pstate-name tag)
          to-set))

(defn movie-ids-by-target-system
  [env target-system]
  (some-> (select-one env common/movies-ids-by-target-system-pstate-name target-system)
          to-set))

(defn movie-ids-by-monitor
  [env monitor]
  (some-> (select-one env common/movies-ids-by-monitor-pstate-name monitor)
          to-set))

(defn movie-ids-by-monitored
  [env]
  (some-> (select-one env common/movies-ids-by-monitored-pstate-name true)
          to-set))

(comment
  (require '[bamf.system.interface :as sys])
  (def rs (sys/runtime-state))
  (def env (:movies/env rs))
  (def movie-id (movie-id-by-tmdb-id env 66126))
  (movie-by-id env movie-id))
