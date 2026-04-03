(ns user
  {:author "Ricardo Correa"}
  (:require [bamf.system.interface :as system]
            [taoensso.telemere :as t]))

(set! *warn-on-reflection* true)

(defn start [] (system/start {:environment :local :dev-ns 'bamf.dev.system}))

(defn stop [] (system/stop))

(defn restart [] (system/restart))

(defn status [] (system/status))

(defn runtime-state [] (system/runtime-state))

(defn config [] (system/config))

(comment
  (t/set-min-level! :debug)
  (start)
  (stop)
  (restart)
  (status)
  (runtime-state)
  (config))
