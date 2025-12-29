(ns bamf.system.interface
  {:author "Ricardo Correa"}
  (:require [donut.system :as ds]
            [donut.system.repl :as dsr]
            [donut.system.repl.state :as state]
            [taoensso.telemere :as t])
  (:import [clojure.lang ExceptionInfo]))

(def ^:private current-runtime (atom nil))

(defn ensure-ns-loaded
  [ns]
  (when-not (find-ns ns)
    (try
      (require ns)
      (catch java.io.FileNotFoundException cause
        (throw
         (ex-info
          (str
           ns
           " is unavailable. Launch with the :dev alias (or equivalent project profile) so development systems are on the classpath.")
          {:missing-ns ns}
          cause)))
      (catch Throwable cause (throw cause)))))

(defn stop [] (dsr/stop))

(defn restart [] (dsr/restart))

(defn status [] (ds/describe-system (ds/system (:environment @current-runtime))))

(defn runtime-state [] (:runtime-state (::ds/instances state/system)))

(defn config [] (:config (::ds/instances state/system)))

(defmulti start (fn [system & _] (:system system)))

(defmethod start :go
  [runtime]
  (reset! current-runtime runtime)
  (let [environment (:environment runtime)]
    (try (dsr/start environment)
         (t/log! {:level :info :reason :system/start-success} "ready-to-rock-and-roll")
         :ready-to-rock-and-roll
         (catch ExceptionInfo e
           (t/log! {:level :error :reason :system/start-failed :details (ex-data e)} "system start failed")
           (throw e)))))

(comment
  (add-watch current-runtime
             :logger
             (fn [_ _ old-state new-state] (println (str "Atom changed from " old-state " to " new-state)))))
