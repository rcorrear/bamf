(ns bamf.movies.save-movies-flow-test
  (:require [bamf.movies.persistence :as persistence]
            [bamf.movies.rama.client.depot :as depot]
            [bamf.movies.rama.client.pstate :as pstate]
            [camel-snake-kebab.core :as csk]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]))

(defn- kebabize-keys
  [data]
  (letfn [(fmt [k]
            (cond (keyword? k) (-> k
                                   name
                                   csk/->kebab-case
                                   keyword)
                  (string? k)  (csk/->kebab-case k)
                  :else        k))]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map (fn [[k v]] [(fmt k) v]) x)) x)) data)))

(def sample-movie
  (merge (kebabize-keys (json/read-str (slurp (io/resource "movie-request.json")) :key-fn keyword))
         {:path                 "/media/video/movies/Dune (2021)"
          :target-system        "radarr"
          :title                "Dune"
          :quality-profile-id   1
          :minimum-availability "released"
          :movie-file-id        0
          :monitored            true
          :tags                 ["SciFi" "Adventure"]}))

(def sample-response
  (let [response (kebabize-keys (json/read-str (slurp (io/resource "movie-response.json")) :key-fn keyword))]
    (assoc response :movie-metadata-id (or (:movie-metadata-id response) (:tmdb-id response)))))

(deftest save-movies-flow-detects-duplicates
  (let [state  (atom {:by-id {} :id-by-metadata {} :ids-by-tag {} :ids-by-target {} :id-by-tmdb {}})
        id-seq (atom 0)
        env    {:clock (constantly "2025-09-21T17:00:00Z") :movie-depot ::movie-depot}]
    (with-redefs [pstate/movie-id-by-metadata-id    (fn [_env metadata-id & _]
                                                      (or (get-in @state [:id-by-metadata metadata-id])
                                                          (some (fn [[id movie]]
                                                                  (when (= metadata-id (:movie-metadata-id movie)) id))
                                                                (:by-id @state))))
                  pstate/movie-id-by-tmdb-id        (fn [_env tmdb-id & _]
                                                      (or (get-in @state [:id-by-tmdb tmdb-id])
                                                          (some (fn [[id movie]] (when (= tmdb-id (:tmdb-id movie)) id))
                                                                (:by-id @state))))
                  pstate/movie-by-id                (fn [_env id & _] (get-in @state [:by-id id]))
                  pstate/movie-ids-by-tag           (fn [_env tag] (or (get-in @state [:ids-by-tag tag]) #{}))
                  pstate/movie-ids-by-target-system (fn [_env system] (or (get-in @state [:ids-by-target system]) #{}))
                  depot/put!                        (fn [{:keys [depot movie]}]
                                                      (is (= ::movie-depot depot))
                                                      (let [new-id   (swap! id-seq inc)
                                                            with-id  (assoc movie :id new-id)
                                                            tags     (set (or (:tags with-id) []))
                                                            meta-id  (:movie-metadata-id with-id)
                                                            response (assoc sample-response
                                                                            :id                new-id
                                                                            :movie-metadata-id meta-id)]
                                                        (swap! state
                                                          (fn [acc]
                                                            (reduce
                                                             (fn [m tag]
                                                               (update-in m [:ids-by-tag tag] (fnil conj #{}) new-id))
                                                             (-> acc
                                                                 (assoc-in [:by-id new-id] with-id)
                                                                 (assoc-in [:id-by-metadata meta-id] new-id)
                                                                 (assoc-in [:id-by-tmdb (:tmdb-id with-id)] new-id)
                                                                 (update-in [:ids-by-target (:target-system with-id)]
                                                                            (fnil conj #{})
                                                                            new-id))
                                                             tags)))
                                                        {:status :stored :movie response}))]
      (testing "first save persists movie"
        (let [{:keys [status movie]} (persistence/save! env sample-movie)
              retrieved-id           (pstate/movie-id-by-metadata-id env (:movie-metadata-id movie))
              via-metadata           (when retrieved-id (pstate/movie-by-id env retrieved-id))]
          (is (= :stored status))
          (is (= 1 (:id movie)))
          (is (= "2025-12-14T03:09:54Z" (:added movie)))
          (is (= "2025-09-21T17:00:00Z" (:last-search-time movie)))
          (is (= (assoc sample-response :id (:id movie) :movie-metadata-id (:movie-metadata-id movie))
                 (select-keys movie (keys sample-response))))
          (is (= (:id movie) (:id via-metadata)))))
      (testing "duplicate submission returns conflict metadata"
        (let [{:keys [status existing-id reason field]} (persistence/save! env sample-movie)]
          (is (= :duplicate status))
          (is (= 1 existing-id))
          (is (= "duplicate-metadata" reason))
          (is (= :tmdb-id field))))
      (testing "indexes expose stored movie"
        (let [by-target (pstate/movie-ids-by-target-system env "radarr")
              by-tag    (pstate/movie-ids-by-tag env "scifi")]
          (is (set? by-target))
          (is (contains? by-target 1))
          (is (set? by-tag))
          (is (contains? by-tag 1)))))))
