(ns user
  {:author "Ricardo Correa"}
  (:require [bamf.dev.core :as core] ;; required in order to load in the defmulti's that define the donut `named-system`'s.
            [donut.system :as ds]
            [donut.system.repl :as dsr]
            [donut.system.repl.state :as state]
            ;; [taoensso.telemere :as t]
            )
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def ^:private environment (atom nil))

(defmethod ds/named-system ::ds/repl [_] (ds/system @environment))

(defn go
  ([] (go ::core/local))
  ([env]
   (reset! environment env)
   (try (dsr/start env)
        :ready-to-rock-and-roll
        (catch ExceptionInfo e
          ;; (t/log! {:level :error} (ex-data e))
          (throw e)
          :bye-bye))))

(def stop dsr/stop)

(def restart dsr/restart)

(defn status
  []
  (ds/describe-system (ds/system @environment)))

(defn runtime-state [] (::core/runtime-state (::ds/instances state/system)))

(defn config [] (::core/config (::ds/instances state/system)))

(comment
  (go)
  (stop)
  (restart)
  (status)
  (runtime-state)
  (config)
  (ds/system @environment))
