(ns bamf.movies.save-movies-flow-test
  (:require [bamf.movies.persistence :as persistence]
            [bamf.movies.rama.client.depot :as depot]
            [bamf.movies.rama.client.pstate :as pstate]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(def sample-movie
  (merge (json/read-str (slurp (io/resource "movie.json")) :key-fn keyword)
         {:path                "/media/video/movies/Dune (2021)"
          :targetSystem        "radarr"
          :title               "Dune"
          :qualityProfileId    1
          :minimumAvailability "released"
          :movieFileId         0
          :monitored           true
          :tags                ["SciFi" "Adventure"]}))

(deftest save-movies-flow-detects-duplicates
  (let [state  (atom {:by-id {} :id-by-metadata {} :id-by-path {} :ids-by-tag {} :ids-by-target {}})
        id-seq (atom 0)
        env    {:clock (constantly "2025-09-21T17:00:00Z") :movie-depot ::movie-depot}]
    (with-redefs [pstate/movie-id-by-metadata-id    (fn [_env metadata-id]
                                                      (or (get-in @state [:id-by-metadata metadata-id])
                                                          (some (fn [[id movie]]
                                                                  (when (= metadata-id (:movieMetadataId movie)) id))
                                                                (:by-id @state))))
                  pstate/movie-by-path              (fn [_env path]
                                                      (when-let [found-id (get-in @state [:id-by-path path])]
                                                        (get-in @state [:by-id found-id])))
                  pstate/movie-by-id                (fn [_env id] (get-in @state [:by-id id]))
                  pstate/movie-ids-by-tag           (fn [_env tag] (or (get-in @state [:ids-by-tag tag]) #{}))
                  pstate/movie-ids-by-target-system (fn [_env system] (or (get-in @state [:ids-by-target system]) #{}))
                  depot/put!                        (fn [{:keys [depot movie]}]
                                                      (is (= ::movie-depot depot))
                                                      (let [new-id  (swap! id-seq inc)
                                                            with-id (assoc movie :id new-id)
                                                            tags    (set (or (:tags with-id) []))
                                                            meta-id (:movieMetadataId with-id)]
                                                        (swap! state
                                                          (fn [acc]
                                                            (reduce
                                                             (fn [m tag]
                                                               (update-in m [:ids-by-tag tag] (fnil conj #{}) new-id))
                                                             (-> acc
                                                                 (assoc-in [:by-id new-id] with-id)
                                                                 (assoc-in [:id-by-metadata meta-id] new-id)
                                                                 (assoc-in [:id-by-path (:path with-id)] new-id)
                                                                 (update-in [:ids-by-target (:targetSystem with-id)]
                                                                            (fnil conj #{})
                                                                            new-id))
                                                             tags)))
                                                        {:status :stored :movie {:id new-id}}))]
      (testing "first save persists movie"
        (let [{:keys [status movie]} (persistence/save! env sample-movie)
              retrieved-id           (pstate/movie-id-by-metadata-id env (:movieMetadataId movie))
              via-metadata           (when retrieved-id (pstate/movie-by-id env retrieved-id))]
          (is (= :stored status))
          (is (= 1 (:id movie)))
          (is (= "2025-09-21T17:00:00Z" (:added movie)))
          (is (= "2025-09-21T17:00:00Z" (:lastSearchTime movie)))
          (is (= ["scifi" "adventure"] (:tags movie)))
          (is (= movie via-metadata))))
      (testing "duplicate submission returns conflict metadata"
        (let [{:keys [status existing-id reason field]} (persistence/save! env sample-movie)]
          (is (= :duplicate status))
          (is (= 1 existing-id))
          (is (= "duplicate-metadata" reason))
          (is (= :tmdbId field))))
      (testing "indexes expose stored movie"
        (let [by-target (pstate/movie-ids-by-target-system env "radarr")
              by-tag    (pstate/movie-ids-by-tag env "scifi")]
          (is (set? by-target))
          (is (contains? by-target 1))
          (is (set? by-tag))
          (is (contains? by-tag 1)))))))
