(ns bamf.movies.rama.common "Shared Rama constants and helpers for the Movies Rama module.")

(defrecord MoviePayload [id added last-search-time imdb-id minimum-availability monitor monitored movie-file-id
                         movie-metadata-id quality-profile-id root-folder-path path search-for-movie tags target-system
                         title title-slug tmdb-id year add-options])

(def module-name "bamf.movies.rama.module.core/MovieModule")
(def movie-depot-name "*movie-saves-depot")
(def movie-by-id-pstate-name "$$movies")
(def movies-id-by-metadata-id-pstate-name "$$movies-id-by-metadata-id")
(def movies-id-by-tmdb-id-pstate-name "$$movies-id-by-tmdb-id")
(def movies-ids-by-monitor-pstate-name "$$movies-ids-by-monitor")
(def movies-ids-by-monitored-pstate-name "$$movies-ids-by-monitored")
(def movies-ids-by-tag-pstate-name "$$movies-ids-by-tag")
(def movies-ids-by-target-system-pstate-name "$$movies-ids-by-target-system")

(def movie-event-version 1)

(defn movie-created-event
  [movie]
  {:event :movie.created :version movie-event-version :payload (map->MoviePayload movie)})

(defn movie-updated-event
  [movie]
  {:event :movie.updated :version movie-event-version :payload (map->MoviePayload movie)})
