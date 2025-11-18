(ns bamf.movies.interface
  "Public entry points for the movies component."
  (:require [bamf.movies.http :as http]
            [bamf.movies.inspection :as inspection]
            [bamf.movies.persistence :as persistence]
            [bamf.movies.rama.runtime :as runtime]))

(defn save-movie!
  "Persist a movie payload using the provided Rama environment bindings."
  [env movie]
  (persistence/save! env movie))

(defn list-movies "List movies by monitored status or target system." [env query] (inspection/list-movies env query))

(defn get-movie "Return a movie by id." [env movie-id] (inspection/get-movie env movie-id))

(defn update-movie!
  "Apply mutable updates to an existing movie using the Rama environment."
  [env movie]
  (persistence/update! env movie))

(defn start!
  "Start the Rama movie module environment used by HTTP handlers."
  ([] (runtime/start!))
  ([options] (runtime/start! options)))

(defn stop! "Shutdown resources previously created via start-runtime!." [env] (runtime/stop! env))

(defn get-http-api "Expose Movies component HTTP routes for aggregation." [context] (http/get-http-api context))
