(ns bamf.movies.persistence-test
  (:require [bamf.movies.model :as model]
            [bamf.movies.persistence :as persistence]
            [bamf.movies.rama.client.depot :as depot]
            [bamf.movies.rama.client.pstate :as pstate]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]))

(def sample-json
  (-> "movie.json"
      io/resource
      slurp))

(def sample-payload
  (delay (merge (json/read-str sample-json :key-fn keyword)
                {:path                "/media/video/movies/Dune (2021)"
                 :targetSystem        "radarr"
                 :tmdbId              438631
                 :titleSlug           "438631"
                 :qualityProfileId    1
                 :minimumAvailability "released"
                 :movieFileId         0
                 :monitored           true})))

(def ^:private movie-depot ::movie-depot)

(defn- canonical-existing
  []
  (-> @sample-payload
      (assoc :added "2025-09-21T17:00:00Z" :lastSearchTime "2025-09-21T17:00:00Z")
      (model/normalize (constantly "2025-09-21T17:00:00Z"))
      (assoc :id 77)))

(deftest normalization-produces-canonical-structure
  (let [captured (atom nil)
        payload  (assoc @sample-payload
                        :tags           ["SciFi" "4K" "SciFi"]
                        :addOptions     nil
                        :added          "2025-09-21T12:00:00-05:00"
                        :lastSearchTime nil)
        env      {:clock (constantly "2025-09-21T17:00:00Z") :movie-depot movie-depot}]
    (with-redefs [pstate/movie-id-by-metadata-id (fn [& _] nil)
                  pstate/movie-by-path           (fn [& _] nil)
                  pstate/movie-by-id             (fn [& _] nil)
                  depot/put!                     (fn [{:keys [depot movie]}]
                                                   (reset! captured (dissoc movie :id))
                                                   (is (= movie-depot depot))
                                                   {:status :stored :movie {:id 99}})]
      (let [{:keys [status movie]} (persistence/save! env payload)
            recorded               (select-keys movie (keys @captured))]
        (is (= :stored status))
        (is (= 99 (:id movie)))
        (is (= "2025-09-21T17:00:00Z" (:added movie)))
        (is (= "2025-09-21T17:00:00Z" (:lastSearchTime movie)))
        (is (= ["scifi" "4k"] (:tags movie)))
        (is (= {} (:addOptions movie)))
        (is (= (:tmdbId payload) (:movieMetadataId movie)))
        (is (= @captured recorded))))))

(deftest duplicate-detected-by-metadata
  (let [existing {:id 7 :movieMetadataId 42 :path "/movies/foo.mkv"}
        payload  (assoc @sample-payload :tmdbId 42 :titleSlug "42" :movieMetadataId 0 :path "/movies/foo.mkv")]
    (with-redefs [pstate/movie-id-by-metadata-id (fn [_env metadata-id & _] (when (= 42 metadata-id) (:id existing)))
                  pstate/movie-by-id             (fn [_env id & _] (when (= (:id existing) id) existing))
                  pstate/movie-by-path           (fn [& _] nil)
                  depot/put!                     (fn [& _] (throw (ex-info "should not write" {})))]
      (let [result (persistence/save! {:movie-depot movie-depot} payload)]
        (is (= :duplicate (:status result)))
        (is (= 7 (:existing-id result)))
        (is (= "duplicate-metadata" (:reason result)))
        (is (= :tmdbId (:field result)))))))

(deftest duplicate-detected-by-path
  (let [existing {:id 11 :movieMetadataId 77 :path "same"}
        payload  (assoc @sample-payload :path "same" :tmdbId 77 :titleSlug "77")]
    (with-redefs [pstate/movie-id-by-metadata-id (fn [& _] nil)
                  pstate/movie-by-path           (fn [_env path & _] (when (= "same" path) existing))
                  pstate/movie-by-id             (fn [& _] nil)
                  depot/put!                     (fn [& _] (throw (ex-info "should not write" {})))]
      (let [result (persistence/save! {:movie-depot movie-depot} payload)]
        (is (= :duplicate (:status result)))
        (is (= 11 (:existing-id result)))
        (is (= "duplicate-path" (:reason result)))
        (is (= :path (:field result)))))))

(deftest invalid-payload-returns-errors
  (with-redefs [pstate/movie-id-by-metadata-id (fn [& _] nil)
                pstate/movie-by-path           (fn [& _] nil)
                pstate/movie-by-id             (fn [& _] nil)
                depot/put!                     (fn [& _] (throw (ex-info "should not write" {})))]
    (let [result (persistence/save! {:movie-depot movie-depot} (dissoc @sample-payload :path))]
      (is (= :invalid (:status result)))
      (is (some #{"path is required"} (:errors result))))))

(deftest title-slug-must-match-tmdb-id
  (with-redefs [pstate/movie-id-by-metadata-id (fn [& _] nil)
                pstate/movie-by-path           (fn [& _] nil)
                pstate/movie-by-id             (fn [& _] nil)
                depot/put!                     (fn [& _] (throw (ex-info "should not write" {})))]
    (let [result (persistence/save! {:movie-depot movie-depot}
                                    (assoc @sample-payload :titleSlug "mismatch" :tmdbId 66126))]
      (is (= :invalid (:status result)))
      (is (= ["titleSlug must match tmdbId"] (:errors result))))))

(deftest update-valid-movie-persists-fields
  (let [existing (canonical-existing)
        captured (atom nil)]
    (with-redefs [pstate/movie-by-id             (fn [_env id & _] (when (= (:id existing) id) existing))
                  pstate/movie-id-by-metadata-id (fn [& _] (:id existing))
                  depot/update!                  (fn [{:keys [movie]}]
                                                   (reset! captured movie)
                                                   {:status :updated :movie {:id (:id movie)}})]
      (let [result
            (persistence/update!
             {:movie-depot movie-depot}
             {:id (:id existing) :monitored false :lastSearchTime "2025-10-10T00:00:00Z" :tmdbId (:tmdbId existing)})]
        (is (= :updated (:status result)))
        (is (= false (get-in result [:movie :monitored])))
        (is (= "2025-10-10T00:00:00Z" (get-in result [:movie :lastSearchTime])))
        (is (= false (:monitored @captured)))))))

(deftest update-requires-id
  (with-redefs [pstate/movie-by-id (fn [& _] (throw (ex-info "should not call" {})))]
    (let [result (persistence/update! {:movie-depot movie-depot} {:monitored false})]
      (is (= :invalid (:status result)))
      (is (= ["id must be a positive integer"] (:errors result))))))

(deftest update-requires-mutable-fields
  (let [existing (canonical-existing)]
    (with-redefs [pstate/movie-by-id (fn [_env id & _] (when (= (:id existing) id) existing))]
      (let [result (persistence/update! {:movie-depot movie-depot} {:id (:id existing) :tmdbId (:tmdbId existing)})]
        (is (= :invalid (:status result)))
        (is (= ["At least one mutable field must be provided for updates"] (:errors result)))))))

(deftest update-returns-not-found
  (with-redefs [pstate/movie-by-id (fn [& _] nil)]
    (let [result (persistence/update! {:movie-depot movie-depot} {:id 123 :monitored false})]
      (is (= :not-found (:status result)))
      (is (= 123 (:movie-id result))))))

(deftest update-detects-duplicate-metadata
  (let [existing (canonical-existing)]
    (with-redefs [pstate/movie-by-id             (fn [_env id & _] (when (= (:id existing) id) existing))
                  pstate/movie-id-by-metadata-id (fn [_env metadata-id & _]
                                                   (cond (= metadata-id (:movieMetadataId existing)) (:id existing)
                                                         (= metadata-id 999)                         222
                                                         :else                                       nil))
                  depot/update!                  (fn [& _] (throw (ex-info "should not write" {})))]
      (let [result (persistence/update! {:movie-depot movie-depot} {:id (:id existing) :movieMetadataId 999})]
        (is (= :duplicate (:status result)))
        (is (= 222 (:existing-id result)))))))

(deftest update-validates-new-values
  (let [existing (canonical-existing)]
    (with-redefs [pstate/movie-by-id             (fn [_env id & _] (when (= (:id existing) id) existing))
                  pstate/movie-id-by-metadata-id (fn [& _] (:id existing))]
      (let [result (persistence/update! {:movie-depot movie-depot}
                                        {:id (:id existing) :qualityProfileId -1 :tmdbId (:tmdbId existing)})]
        (is (= :invalid (:status result)))
        (is (seq (:errors result)))))))

(deftest update-requires-depot
  (let [existing (canonical-existing)]
    (with-redefs [pstate/movie-by-id             (fn [_env id & _] (when (= (:id existing) id) existing))
                  pstate/movie-id-by-metadata-id (fn [& _] (:id existing))]
      (let [result (persistence/update! {} {:id (:id existing) :monitored false :tmdbId (:tmdbId existing)})]
        (is (= :error (:status result)))
        (is (= ["movie-depot handle not provided"] (:errors result)))))))
