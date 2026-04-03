(ns bamf.dev.system.rama
  "Rama lifecycle helpers for Donut-managed systems."
  (:require [bamf.rama.interface :as rama]
            [com.rpl.rama.test :as rt])
  (:import (java.io Closeable)))

(defn start! [] (rama/local-runtime (rt/create-ipc)))

(defn stop!
  [rama-runtime]
  (when-let [handle (rama/handle rama-runtime)]
    (when (instance? Closeable handle) (try (.close ^Closeable handle) (catch Exception _ nil)))))
