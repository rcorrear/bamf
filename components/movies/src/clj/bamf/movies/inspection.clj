(ns bamf.movies.inspection
  (:require [bamf.movies.rama-client.pstate :as pstate]))

(defn movie-by-metadata-id [env metadata-id] (pstate/movie-by-metadata-id env metadata-id))

(defn movie-by-path [env path] (pstate/movie-by-path env path))

(defn movies-by-tag [env tag] (pstate/movies-by-tag env tag))

(defn movies-by-target-system [env system] (pstate/movies-by-target-system env system))
