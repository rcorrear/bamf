(ns user
  {:author "Ricardo Correa"}
  (:require [bamf.system.interface :as system]))

(set! *warn-on-reflection* true)

(defmethod system/start :bamf
  ([runtime] (system/ensure-ns-loaded 'bamf.dev.system) (system/start (assoc runtime :system :go))))

(defn start [] (system/start {:environment :local :system :bamf}))

(defn stop [] (system/stop))

(defn restart [] (system/restart))

(defn status [] (system/status))

(defn runtime-state [] (system/runtime-state))

(defn config [] (system/config))

(comment
  (start)
  (stop)
  (restart)
  (status)
  (runtime-state)
  (config))
