(ns bamf.rama.client
  "Neutral Rama handle/client helpers for local IPC and future connected-cluster modes."
  (:require [com.rpl.rama :as rama])
  (:import (java.io Closeable)))

(deftype RamaRuntime [kind handle])

(defn kind
  [rama-runtime]
  (cond (instance? RamaRuntime rama-runtime) (.-kind ^RamaRuntime rama-runtime)
        (map? rama-runtime)                  (:kind rama-runtime)
        :else                                nil))

(defn handle
  "Return the underlying Rama object usable with foreign-* operations.
   Accepts a neutral Rama runtime map or a raw handle."
  [rama-runtime]
  (cond (instance? RamaRuntime rama-runtime) (.-handle ^RamaRuntime rama-runtime)
        (map? rama-runtime)                  (:handle rama-runtime)
        :else                                rama-runtime))

(defn local-runtime [ipc] (RamaRuntime. :ipc ipc))

(defn cluster-runtime [manager] (RamaRuntime. :cluster manager))

(defn open-cluster! [config] (cluster-runtime ((requiring-resolve 'com.rpl.rama/open-cluster-manager) config)))

(defn close!
  [rama-runtime]
  (let [h (handle rama-runtime)] (when (instance? Closeable h) (try (.close ^Closeable h) (catch Exception _ nil)))))

(defn foreign-depot
  [rama-runtime module-name depot-name]
  (rama/foreign-depot (handle rama-runtime) module-name depot-name))

(defn foreign-pstate
  [rama-runtime module-name pstate-name]
  (rama/foreign-pstate (handle rama-runtime) module-name pstate-name))

(defn foreign-query
  [rama-runtime module-name query-topology-name]
  (rama/foreign-query (handle rama-runtime) module-name query-topology-name))

(defn foreign-invoke-query [query-client] (rama/foreign-invoke-query query-client))

(defn foreign-select-one [path pstate & [opts]] (rama/foreign-select-one path pstate opts))

(defn deployed-module-names
  [rama-runtime]
  (if-let [f (try (requiring-resolve 'com.rpl.rama/deployed-module-names) (catch Throwable _ nil))]
    (f (handle rama-runtime))
    []))

(defn module-deployed?
  [rama-runtime module-name]
  (try (contains? (set (deployed-module-names rama-runtime)) module-name) (catch Exception _ false)))
