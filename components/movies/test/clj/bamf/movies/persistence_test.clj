(ns bamf.movies.persistence-test
  (:require [bamf.movies.persistence :as persistence]
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
                 :qualityProfileId    1
                 :minimumAvailability "released"
                 :movieFileId         0
                 :monitored           true})))

(def ^:private movie-depot ::movie-depot)

(deftest normalization-produces-canonical-structure
  (let [captured (atom nil)
        payload  (assoc @sample-payload
                        :tags           ["SciFi" "4K" "SciFi"]
                        :addOptions     nil
                        :added          "2025-09-21T12:00:00-05:00"
                        :lastSearchTime nil)
        env      {:clock (constantly "2025-09-21T17:00:00Z") :movie-depot movie-depot}]
    (with-redefs [pstate/movie-id-by-metadata-id (constantly nil)
                  pstate/movie-by-path           (constantly nil)
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
    (with-redefs [pstate/movie-id-by-metadata-id (fn [_env metadata-id] (when (= 42 metadata-id) (:id existing)))
                  pstate/movie-by-id             (fn [_env id] (when (= (:id existing) id) existing))
                  pstate/movie-by-path           (constantly nil)
                  depot/put!                     (fn [& _] (throw (ex-info "should not write" {})))]
      (let [result (persistence/save! {:movie-depot movie-depot} payload)]
        (is (= :duplicate (:status result)))
        (is (= 7 (:existing-id result)))
        (is (= "duplicate-metadata" (:reason result)))
        (is (= :tmdbId (:field result)))))))

(deftest duplicate-detected-by-path
  (let [existing {:id 11 :movieMetadataId 77 :path "same"}
        payload  (assoc @sample-payload :path "same" :tmdbId 77 :titleSlug "77")]
    (with-redefs [pstate/movie-id-by-metadata-id (constantly nil)
                  pstate/movie-by-path           (fn [_env path] (when (= "same" path) existing))
                  pstate/movie-by-id             (fn [& _] nil)
                  depot/put!                     (fn [& _] (throw (ex-info "should not write" {})))]
      (let [result (persistence/save! {:movie-depot movie-depot} payload)]
        (is (= :duplicate (:status result)))
        (is (= 11 (:existing-id result)))
        (is (= "duplicate-path" (:reason result)))
        (is (= :path (:field result)))))))

(deftest invalid-payload-returns-errors
  (with-redefs [pstate/movie-id-by-metadata-id (constantly nil)
                pstate/movie-by-path           (constantly nil)
                pstate/movie-by-id             (fn [& _] nil)
                depot/put!                     (fn [& _] (throw (ex-info "should not write" {})))]
    (let [result (persistence/save! {:movie-depot movie-depot} (dissoc @sample-payload :path))]
      (is (= :invalid (:status result)))
      (is (= ["path is required"] (:errors result))))))

(deftest title-slug-must-match-tmdb-id
  (with-redefs [pstate/movie-id-by-metadata-id (constantly nil)
                pstate/movie-by-path           (constantly nil)
                pstate/movie-by-id             (fn [& _] nil)
                depot/put!                     (fn [& _] (throw (ex-info "should not write" {})))]
    (let [result (persistence/save! {:movie-depot movie-depot}
                                    (assoc @sample-payload :titleSlug "mismatch" :tmdbId 66126))]
      (is (= :invalid (:status result)))
      (is (= ["titleSlug must match tmdbId"] (:errors result))))))
