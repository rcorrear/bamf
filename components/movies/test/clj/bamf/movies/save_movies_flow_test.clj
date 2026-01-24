(ns bamf.movies.save-movies-flow-test
  (:require [bamf.casing :as casing]
            [bamf.movies.model :as model]
            [bamf.movies.persistence :as persistence]
            [bamf.movies.rama.client.depot :as depot]
            [bamf.movies.rama.client.pstate :as pstate]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(def sample-movie
  (merge (casing/->kebab-keys (json/read-str (slurp (io/resource "movie-save-request.json")) :key-fn keyword))
         {:path                 "/media/video/movies/Dune (2021)"
          :target-system        "radarr"
          :title                "Dune"
          :quality-profile-id   1
          :minimum-availability "released"
          :movie-file-id        0
          :monitored            true
          :tags                 ["SciFi" "Adventure"]}))

(def sample-response
  (casing/->kebab-keys (json/read-str (slurp (io/resource "movie-save-response.json")) :key-fn keyword)))

(def ^:private metadata-keys
  [:images :genres :sort-title :clean-title :original-title :clean-original-title :original-language :status
   :last-info-sync :runtime :in-cinemas :physical-release :digital-release :year :secondary-year :ratings
   :recommendations :certification :you-tube-trailer-id :studio :overview :website :popularity :collection])

(defn- metadata-from
  [movie]
  (->> (select-keys movie metadata-keys)
       (remove (comp nil? val))
       (into {})))

(deftest save-movies-flow-detects-duplicates
  (let [state  (atom {:by-id {} :ids-by-tag {} :ids-by-target {} :id-by-tmdb {} :metadata-by-id {}})
        id-seq (atom 0)
        env    {:clock (constantly "2025-09-21T17:00:00Z") :movie-depot ::movie-depot}]
    (with-redefs [pstate/movie-id-by-tmdb-id        (fn [_env tmdb-id & _]
                                                      (or (get-in @state [:id-by-tmdb tmdb-id])
                                                          (some (fn [[id movie]] (when (= tmdb-id (:tmdb-id movie)) id))
                                                                (:by-id @state))))
                  pstate/movie-by-id                (fn [_env id & _] (get-in @state [:by-id id]))
                  pstate/metadata-by-movie-id       (fn [_env movie-id & _] (get-in @state [:metadata-by-id movie-id]))
                  pstate/movie-ids-by-tag           (fn [_env tag] (or (get-in @state [:ids-by-tag tag]) #{}))
                  pstate/movie-ids-by-target-system (fn [_env system] (or (get-in @state [:ids-by-target system]) #{}))
                  depot/put!                        (fn [{:keys [depot movie]}]
                                                      (is (= ::movie-depot depot))
                                                      (let [new-id   (swap! id-seq inc)
                                                            with-id  (assoc movie :id new-id)
                                                            tags     (set (or (:tags with-id) []))
                                                            metadata (metadata-from with-id)
                                                            response (assoc sample-response :id new-id)]
                                                        (swap! state
                                                          (fn [acc]
                                                            (reduce
                                                             (fn [m tag]
                                                               (update-in m [:ids-by-tag tag] (fnil conj #{}) new-id))
                                                             (-> acc
                                                                 (assoc-in [:by-id new-id] with-id)
                                                                 (assoc-in [:id-by-tmdb (:tmdb-id with-id)] new-id)
                                                                 (cond-> (seq metadata)    (assoc-in [:metadata-by-id
                                                                                                      new-id]
                                                                                            metadata)
                                                                         (empty? metadata) (update :metadata-by-id
                                                                                                   dissoc
                                                                                                   new-id))
                                                                 (update-in [:ids-by-target (:target-system with-id)]
                                                                            (fnil conj #{})
                                                                            new-id))
                                                             tags)))
                                                        {:status :stored :movie response}))]
      (testing "first save persists movie"
        (let [{:keys [status movie]} (persistence/save! env sample-movie)
              expected-metadata      (or (model/serialize-metadata (model/extract-metadata sample-movie)) {})
              expected-response      (-> sample-response
                                         (assoc :id (:id movie))
                                         (merge expected-metadata))]
          (is (= :stored status))
          (is (= 1 (:id movie)))
          (is (= "2025-12-29T02:12:56Z" (:added movie)))
          (is (nil? (:last-search-time movie)))
          (is (= expected-response (select-keys movie (keys expected-response))))))
      (testing "duplicate submission returns conflict metadata"
        (let [original-metadata                         (pstate/metadata-by-movie-id env 1)
              duplicate-payload                         (assoc sample-movie :genres ["Mystery"] :status "tba")
              {:keys [status existing-id reason field]} (persistence/save! env duplicate-payload)]
          (is (= :duplicate status))
          (is (= 1 existing-id))
          (is (= "duplicate-metadata" reason))
          (is (= :tmdb-id field))
          (is (seq original-metadata))
          (is (= original-metadata (pstate/metadata-by-movie-id env 1)))))
      (testing "indexes expose stored movie"
        (let [by-target (pstate/movie-ids-by-target-system env "radarr")
              by-tag    (pstate/movie-ids-by-tag env "scifi")]
          (is (set? by-target))
          (is (contains? by-target 1))
          (is (set? by-tag))
          (is (contains? by-tag 1)))))))
