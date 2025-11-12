(ns bamf.movies.interface
  "Public entry points for the movies component."
  (:require [bamf.movies.http :as http]
            [bamf.movies.persistence :as persistence]))

(defn save-movie!
  "Persist a movie payload using the provided Rama environment bindings."
  [env movie]
  (persistence/save! env movie))

(defn update-movie!
  "Apply mutable updates to an existing movie using the Rama environment."
  [env movie]
  (persistence/update! env movie))

(defn get-http-api "Expose Movies component HTTP routes for aggregation." [context] (http/get-http-api context))
