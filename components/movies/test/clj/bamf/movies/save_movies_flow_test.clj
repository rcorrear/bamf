(ns bamf.movies.save-movies-flow-test
  (:require [bamf.movies.inspection :as inspection]
            [bamf.movies.persistence :as persistence]
            [bamf.movies.rama-client.depot :as depot]
            [bamf.movies.rama-client.pstate :as pstate]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(def sample-movie
  (delay (merge (json/read-str (slurp (io/resource "movie.json")) :key-fn keyword)
                {:path                "/media/video/movies/Dune (2021)"
                 :targetSystem        "radarr"
                 :title               "Dune"
                 :qualityProfileId    1
                 :minimumAvailability "released"
                 :movieFileId         0
                 :monitored           true
                 :tags                ["SciFi" "Adventure"]})))

(deftest save-movies-flow-detects-duplicates
  (let [state  (atom {:by-id {} :by-metadata {} :by-path {} :by-tag {} :by-target {}})
        id-seq (atom 0)
        env    {:clock (constantly "2025-09-21T17:00:00Z") :movie-depot ::movie-depot}]
    (with-redefs [pstate/movie-by-metadata-id    (fn [_env metadata-id] (get-in @state [:by-metadata metadata-id]))
                  pstate/movie-by-path           (fn [_env path] (get-in @state [:by-path path]))
                  pstate/movie-by-id             (fn [_env id] (get-in @state [:by-id id]))
                  pstate/movies-by-tag           (fn [_env tag] (get-in @state [:by-tag tag]))
                  pstate/movies-by-target-system (fn [_env system] (get-in @state [:by-target system]))
                  pstate/next-id                 (fn [_env] (swap! id-seq inc))
                  depot/put!                     (fn [{:keys [depot payload]}]
                                                   (is (= ::movie-depot depot))
                                                   (swap! state
                                                     (fn [acc]
                                                       (let [movie (into {} payload)
                                                             id    (:id movie)
                                                             tags  (set (or (:tags movie) []))]
                                                         (reduce
                                                          (fn [m tag] (update-in m [:by-tag tag] (fnil conj #{}) id))
                                                          (-> acc
                                                              (assoc-in [:by-id id] movie)
                                                              (assoc-in [:by-metadata (:movieMetadataId movie)] movie)
                                                              (assoc-in [:by-path (:path movie)] movie)
                                                              (update-in [:by-target (:targetSystem movie)]
                                                                         (fnil conj #{})
                                                                         id))
                                                          tags))))
                                                   {:status :stored :movie (into {} payload)})]
      (testing "first save persists movie"
        (let [{:keys [status movie]} (persistence/save! env @sample-movie)]
          (is (= :stored status))
          (is (= 1 (:id movie)))
          (is (= "2025-09-21T17:00:00Z" (:added movie)))
          (is (= "2025-09-21T17:00:00Z" (:lastSearchTime movie)))
          (is (= ["scifi" "adventure"] (:tags movie)))
          (is (= movie (inspection/movie-by-metadata-id env (:movieMetadataId movie))))
          (is (= movie (inspection/movie-by-path env (:path movie))))))
      (testing "duplicate submission returns conflict metadata"
        (let [{:keys [status existing-id reason field]} (persistence/save! env @sample-movie)]
          (is (= :duplicate status))
          (is (= 1 existing-id))
          (is (= "duplicate-metadata" reason))
          (is (= :tmdbId field))))
      (testing "indexes expose stored movie"
        (let [by-target (inspection/movies-by-target-system env "radarr")
              by-tag    (inspection/movies-by-tag env "scifi")]
          (is (set? by-target))
          (is (contains? by-target 1))
          (is (set? by-tag))
          (is (contains? by-tag 1)))))))
