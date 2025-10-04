(ns user
  {:author "Ricardo Correa"}
  (:require [bamf.system.interface :as system]))

(set! *warn-on-reflection* true)

(defmethod system/start :radarr
  ([runtime] (system/ensure-ns-loaded 'radarr.dev.system) (system/start (assoc runtime :system :go))))

(defn stop [] (system/stop))

(defn restart [] (system/restart))

(defn status [] (system/status))

(defn runtime-state [] (system/runtime-state))

(defn config [] (system/config))

(comment
  (system/start {:environment :local :system :radarr})
  (stop)
  (restart)
  (status)
  (runtime-state)
  (config))
