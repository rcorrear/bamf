(ns bamf.movies.runtime
  "Manage Rama IPC runtime state and related environment construction."
  (:require [com.rpl.rama :as rama]))

(def ^:private module-name "MovieModule")
(def ^:private movie-depot-name "*movie-saves-depot")
(def ^:private runtime-ipc (atom nil))

(defn- not-started [] (IllegalStateException. "Movies component has not been started."))

(defn- ensure-ipc [] (or @runtime-ipc (throw (not-started))))

(defn- build-env [ipc] {:movie-depot (rama/foreign-depot ipc module-name movie-depot-name)})

(defn env
  "Create the Rama environment needed by the movies component.
   Without arguments, uses the currently cached IPC handle."
  ([] (build-env (ensure-ipc)))
  ([ipc] (when-not ipc (throw (not-started))) (build-env ipc)))

(defn start! "Cache the Rama IPC handle for subsequent operations." [ipc] (reset! runtime-ipc ipc) ipc)

(defn stop! "Clear any cached Rama IPC handle." ([] (reset! runtime-ipc nil)) ([_] (stop!)))
