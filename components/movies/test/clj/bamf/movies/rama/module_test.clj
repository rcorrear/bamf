(ns bamf.movies.rama.module-test
  (:use [com.rpl rama] [com.rpl.rama path])
  (:require [bamf.movies.rama.client.pstate :as pstate]
            [bamf.movies.rama.common :as common]
            [bamf.movies.rama.module :as mm]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [com.rpl.rama.test :as rtest]))

(def sample-json
  (-> "movie.json"
      io/resource
      slurp))

(def sample-movie-row
  (-> sample-json
      (json/read-str :key-fn keyword)
      (as-> parsed
        (let [base (select-keys parsed
                                [:addOptions :added :imdbId :minimumAvailability :monitored :movieFileId
                                 :qualityProfileId :rootFolderPath :tags :title :titleSlug :tmdbId :year])]
          (delay (-> base
                     (assoc :lastSearchTime  nil
                            :monitor         (get-in base [:addOptions :monitor])
                            :movieMetadataId (:tmdbId parsed)
                            :searchForMovie  (get-in base [:addOptions :searchForMovie])
                            :tags            (set '("tag1" "tag2"))
                            :targetSystem    "radarr")
                     (dissoc :added :addOptions :id :path)
                     common/map->MoviePayload))))))

(deftest save-movie
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc mm/MovieModule {:tasks 4 :threads 2})
    (let [module-name       (get-module-name mm/MovieModule)
          rama-env          {:movies/env {:ipc ipc}}
          tmdb-id           (:tmdbId @sample-movie-row)
          movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
          ack-response      (get (foreign-append! movie-saves-depot (common/movie-created-event @sample-movie-row) :ack)
                                 common/movies-etl-name)
          movie-id          (get-in ack-response [:movie :id])
          saved             (pstate/movie-by-id rama-env movie-id tmdb-id)]
      (is (= :stored (:status ack-response)))
      (is (= java.lang.Long (class movie-id)))
      (is (map? saved))
      (is (= movie-id (get-in ack-response [:movie :id])))
      (is (= movie-id (pstate/movie-id-by-tmdb-id rama-env tmdb-id tmdb-id)))
      (is (= movie-id (pstate/movie-id-by-metadata-id rama-env (:movieMetadataId @sample-movie-row) tmdb-id)))
      (is (contains? (pstate/movie-ids-by-monitor rama-env (:monitor @sample-movie-row) tmdb-id) movie-id))
      (is (contains? (pstate/movie-ids-by-target-system rama-env (:targetSystem @sample-movie-row) tmdb-id) movie-id))
      (is (contains? (pstate/movie-ids-by-monitored rama-env tmdb-id) movie-id))
      (doseq [tag (:tags @sample-movie-row)]
        (is (contains? (pstate/movie-ids-by-tag rama-env tag tmdb-id) movie-id))))))
