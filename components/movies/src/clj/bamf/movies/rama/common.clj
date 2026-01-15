(ns bamf.movies.rama.common "Shared Rama constants and helpers for the Movies Rama module.")

(defrecord MoviePayload [id added last-search-time imdb-id minimum-availability monitor monitored movie-file-id
                         quality-profile-id root-folder-path path search-for-movie tags target-system title title-slug
                         tmdb-id year add-options images genres sort-title clean-title original-title
                         clean-original-title original-language status last-info-sync runtime in-cinemas
                         physical-release digital-release secondary-year ratings recommendations certification
                         you-tube-trailer-id studio overview website popularity collection metadata])

(def module-name "bamf.movies.rama.module.core/MovieModule")
(def movie-depot-name "*movie-saves-depot")
(def movie-by-id-pstate-name "$$movies")
(def metadata-by-movie-id-pstate-name "$$metadata-by-movie-id")
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
