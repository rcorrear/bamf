(ns bamf.movies.rama.common "Shared Rama constants and helpers for the Movies Rama module.")

(defrecord MoviePayload [lastSearchTime imdbId minimumAvailability monitor monitored movieFileId movieMetadataId
                         qualityProfileId rootFolderPath searchForMovie tags targetSystem title titleSlug tmdbId year])

(def module-name "bamf.movies.rama.module/MovieModule")
(def movies-etl-name "movies")
(def movie-depot-name "*movie-saves-depot")
(def movie-by-id-pstate-name "$$movies")
(def movies-id-by-metadata-id-pstate-name "$$movies-id-by-metadata-id")
(def movies-id-by-tmdb-id-pstate-name "$$movies-id-by-tmdb-id")
(def movies-ids-by-monitor-pstate-name "$$movies-ids-by-monitor")
(def movies-ids-by-monitored-pstate-name "$$movies-ids-by-monitored")
(def movies-ids-by-tag-pstate-name "$$movies-ids-by-tag")
(def movies-ids-by-target-system-pstate-name "$$movies-ids-by-target-system")

(def movie-created-event-version 1)

(defn movie-created-event
  [movie]
  {:event :movie.created :version movie-created-event-version :payload (map->MoviePayload movie)})
