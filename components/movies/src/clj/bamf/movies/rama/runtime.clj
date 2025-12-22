(ns bamf.movies.rama.runtime
  "Lifecycle helpers for starting and stopping the Rama movie module."
  (:require [bamf.movies.rama.common :as common]
            [bamf.movies.rama.module.core :as module]
            [com.rpl.rama :as rama]
            [com.rpl.rama.test :as rt]
            [taoensso.telemere :as t])
  (:import (java.io Closeable)))

(def ^:private default-launch-config {:tasks 4 :threads 2})

(defn- close-ipc!
  [ipc]
  (when (instance? Closeable ipc)
    (try (.close ^Closeable ipc)
         (catch Exception cause
           (t/log! {:level   :warn
                    :event   :movies/rama-ipc-close-failed
                    :reason  :movies/rama-ipc-close-failed
                    :details {:message (.getMessage cause)}}
                   "Failed to close Rama IPC cleanly")))))

(defn start!
  "Start the Rama MovieModule and return an env map containing the IPC + depot handles."
  ([] (start! {}))
  ([{:keys [launch-config] :or {launch-config default-launch-config}}]
   (let [ipc (rt/create-ipc)]
     (try (rt/launch-module! ipc module/MovieModule launch-config)
          {:ipc ipc :movie-depot (rama/foreign-depot ipc common/module-name common/movie-depot-name)}
          (catch Throwable cause (close-ipc! ipc) (throw cause))))))

(defn stop!
  "Shutdown any resources associated with the provided movies env map."
  [{:keys [ipc]}]
  (when ipc
    (t/log! {:level :info :event :movies/rama-ipc-stop :reason :movies/rama-ipc-stop}
            "Shutting down Rama IPC for movies env")
    (close-ipc! ipc)))
