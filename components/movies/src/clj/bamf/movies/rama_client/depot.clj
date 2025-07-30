(ns bamf.movies.rama-client.depot
  (:require [com.rpl.rama :refer [foreign-append!]]))

(defrecord MovieRow [id title titleSlug path rootFolderPath monitored qualityProfileId added tags addOptions movieFileId
                     minimumAvailability movieMetadataId tmdbId year lastSearchTime targetSystem])

(defn movie-saved-event [movie-depot movie] {:event :movie.saved :depot movie-depot :payload (map->MovieRow movie)})

(defn put!
  "Append the provided event payload to the supplied Rama depot."
  [{:keys [depot payload]}]
  (foreign-append! depot payload)
  {:status :stored :movie (into {} payload)})
