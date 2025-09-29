(ns bamf.movies.interface
  "Public entry points for the movies component."
  (:require [bamf.movies.persistence :as persistence]
            [bamf.movies.runtime :as runtime]))

(defn save-movie!
  "Persist a movie payload using the provided Rama environment bindings."
  ([movie] (let [env (runtime/env)] (save-movie! env movie)))
  ([env movie] (persistence/save! env movie)))

;; DONUT LIFECYCLE FUNCTIONS ↓

(defn start
  "Initialise the movies component by caching the Rama IPC handle for later use."
  [rama-ipc]
  (runtime/start! rama-ipc))

(defn stop "Clear any cached Rama IPC handle." ([] (runtime/stop!)) ([_] (stop)))
