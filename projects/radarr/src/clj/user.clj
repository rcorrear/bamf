(ns user
  {:author "Ricardo Correa"}
  (:require [bamf.system.interface :as system]))

(set! *warn-on-reflection* true)

(defn start [] (system/start {:environment :local :dev-ns 'radarr.dev.system}))

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
