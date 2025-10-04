(ns user
  {:author "Ricardo Correa"}
  (:require [donut.system :as ds]
            [donut.system.repl :as dsr]
            [donut.system.repl.state :as state]
            [taoensso.telemere :as t])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(defonce ^:private dev-core-loaded? (atom false))

(defn- ensure-dev-core-loaded
  []
  (when-not @dev-core-loaded?
    (try
      (require 'bamf.dev.core)
      (reset! dev-core-loaded? true)
      (catch java.io.FileNotFoundException cause
        (throw
         (ex-info
          "bamf.dev.core is unavailable. Launch with the :dev alias (or equivalent project profile) so development systems are on the classpath."
          {:missing-ns 'bamf.dev.core}
          cause)))
      (catch Throwable cause (throw cause)))))

(def ^:private environment (atom nil))

(defmethod ds/named-system ::ds/repl [_] (ds/system @environment))

(defn go
  ([] (go :local))
  ([env]
   (ensure-dev-core-loaded)
   (reset! environment env)
   (try (dsr/start env)
        :ready-to-rock-and-roll
        (catch ExceptionInfo e (t/log! {:level :error} (ex-data e)) (throw e) :bye-bye))))

(defn stop [] (ensure-dev-core-loaded) (dsr/stop))

(defn restart [] (ensure-dev-core-loaded) (dsr/restart))

(defn status ([] (status @environment)) ([env] (ensure-dev-core-loaded) (ds/describe-system (ds/system env))))

(defn runtime-state [] (ensure-dev-core-loaded) (:runtime-state (::ds/instances state/system)))

(defn config [] (ensure-dev-core-loaded) (:config (::ds/instances state/system)))

(comment
  (go)
  (stop)
  (restart)
  (status :local)
  (runtime-state)
  (config)
  (ds/system @environment))
