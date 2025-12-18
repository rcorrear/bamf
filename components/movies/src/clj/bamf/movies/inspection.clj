(ns bamf.movies.inspection
  "Query helpers that read movie data from Rama p-states."
  (:require [bamf.movies.rama.client.pstate :as pstate]
            [taoensso.telemere :as t]))

(defn- fetch-movie
  [env monitored-ids movie-id]
  (when-let [movie (pstate/movie-by-id env movie-id)]
    (let [monitored
          (if monitored-ids (boolean (or (:monitored movie) (contains? monitored-ids movie-id))) (:monitored movie))]
      (-> movie
          (assoc :id movie-id)
          (cond-> (or monitored-ids (some? (:monitored movie))) (assoc :monitored (boolean monitored)))))))

(defn- fetch-movies
  [env ids monitored-ids]
  (->> (or ids [])
       (sort)
       (keep #(fetch-movie env monitored-ids %))
       vec))

(defn get-movie
  "Return a single movie by id or a not-found marker."
  [env movie-id]
  (if-let [movie (fetch-movie env movie-id)]
    {:status :ok :movie movie}
    {:status :not-found :movie-id movie-id}))

(defn list-movies
  "Return movies visible for the supplied filters.

  Supported options:
  - :tmdb-id → optional exact match filter (short-circuits other filters).
  Other spec query params are accepted but ignored (excludeLocalCovers, languageId)."
  [env {:keys [tmdb-id]}]
  (let [tmdb-id   (some-> tmdb-id
                          parse-long)
        condition (cond tmdb-id (when-let [id (pstate/movie-id-by-tmdb-id env tmdb-id)] [id])
                        :else   (pstate/movie-ids-by-target-system env "radarr"))]
    (t/log! :debug {:reason :movies/list-movies :details {:tmdb-id tmdb-id :condition-keys condition}})
    (let [monitored-ids (try (pstate/movie-ids-by-monitored env) (catch Exception _ nil))]
      {:status :ok :movies (fetch-movies env condition monitored-ids)})))

(comment
  (require '[bamf.system.interface :as sys])
  (def rs (sys/runtime-state))
  (def env (:movies/env rs))
  (pstate/movie-ids-by-target-system env "radarr")
  (list-movies env {:tmdb-id "66126"}))
