(ns bamf.movies.persistence-test
  (:require [bamf.movies.model :as model]
            [bamf.movies.persistence :as persistence]
            [bamf.movies.rama.client.depot :as depot]
            [bamf.movies.rama.client.pstate :as pstate]
            [camel-snake-kebab.core :as csk]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [clojure.walk :as walk]))

(def sample-json
  (-> "movie-request.json"
      io/resource
      slurp))

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

(def sample-response
  (delay (kebabize-keys (json/read-str (-> "movie-response.json"
                                           io/resource
                                           slurp)
                                       :key-fn
                                       keyword))))

(def sample-payload
  (delay (merge (kebabize-keys (json/read-str sample-json :key-fn keyword))
                {:path                 "/media/video/movies/Dune (2021)"
                 :target-system        "radarr"
                 :tmdb-id              438631
                 :title-slug           "438631"
                 :quality-profile-id   1
                 :minimum-availability "released"
                 :movie-file-id        0
                 :monitored            true})))

(def ^:private movie-depot ::movie-depot)

(defn- canonical-existing
  []
  (-> @sample-payload
      (assoc :added "2025-09-21T17:00:00Z" :last-search-time "2025-09-21T17:00:00Z")
      (model/normalize (constantly "2025-09-21T17:00:00Z"))
      (assoc :id 77)))

(deftest normalization-produces-canonical-structure
  (let [captured (atom nil)
        payload  (assoc @sample-payload
                        :tags             ["SciFi" "4K" "SciFi"]
                        :add-options      nil
                        :added            "2025-09-21T12:00:00-05:00"
                        :last-search-time nil)
        env      {:clock (constantly "2025-09-21T17:00:00Z") :movie-depot movie-depot}]
    (with-redefs [pstate/movie-id-by-tmdb-id     (fn [& _] nil)
                  pstate/movie-id-by-metadata-id (fn [& _] nil)
                  pstate/movie-by-id             (fn [& _] nil)
                  depot/put!                     (fn [{:keys [depot movie]}]
                                                   (reset! captured (dissoc movie :id))
                                                   (is (= movie-depot depot))
                                                   {:status :stored
                                                    :movie  (assoc @sample-response
                                                                   :id                99
                                                                   :movie-metadata-id (:movie-metadata-id movie))})]
      (let [{:keys [status movie]} (persistence/save! env payload)
            expected-response      (assoc @sample-response
                                          :id                (:id movie)
                                          :movie-metadata-id (:movie-metadata-id movie))]
        (is (= :stored status))
        (is (= 99 (:id movie)))
        (is (= expected-response (select-keys movie (keys expected-response))))
        (is (= "2025-09-21T17:00:00Z" (:last-search-time movie)))
        (is (= ["scifi" "4k"] (:tags @captured)))
        (is (= {} (:add-options @captured)))
        (is (= (:tmdb-id payload) (:movie-metadata-id @captured)))
        (is (= "2025-09-21T17:00:00Z" (:added @captured)))))))

(deftest duplicate-detected-by-metadata
  (let [existing {:id 7 :movie-metadata-id 42 :path "/movies/foo.mkv"}
        payload  (assoc @sample-payload :tmdb-id 42 :title-slug "42" :movie-metadata-id 0 :path "/movies/foo.mkv")]
    (with-redefs [pstate/movie-id-by-tmdb-id     (fn [_env tmdb-id & _] (when (= 42 tmdb-id) (:id existing)))
                  pstate/movie-id-by-metadata-id (fn [_env metadata-id & _] (when (= 42 metadata-id) (:id existing)))
                  pstate/movie-by-id             (fn [_env id & _] (when (= (:id existing) id) existing))
                  depot/put!                     (fn [& _] (throw (ex-info "should not write" {})))]
      (let [result (persistence/save! {:movie-depot movie-depot} payload)]
        (is (= :duplicate (:status result)))
        (is (= 7 (:existing-id result)))
        (is (= "duplicate-metadata" (:reason result)))
        (is (= :tmdb-id (:field result)))))))

(deftest invalid-payload-returns-errors
  (with-redefs [pstate/movie-id-by-tmdb-id     (fn [& _] nil)
                pstate/movie-id-by-metadata-id (fn [& _] nil)
                pstate/movie-by-id             (fn [& _] nil)
                depot/put!                     (fn [& _] (throw (ex-info "should not write" {})))]
    (let [result (persistence/save! {:movie-depot movie-depot} (dissoc @sample-payload :title))]
      (is (= :invalid (:status result)))
      (is (some #{"title is required"} (:errors result))))))

(deftest title-slug-must-match-tmdb-id
  (with-redefs [pstate/movie-id-by-tmdb-id     (fn [& _] nil)
                pstate/movie-id-by-metadata-id (fn [& _] nil)
                pstate/movie-by-id             (fn [& _] nil)
                depot/put!                     (fn [& _] (throw (ex-info "should not write" {})))]
    (let [result (persistence/save! {:movie-depot movie-depot}
                                    (assoc @sample-payload :title-slug "mismatch" :tmdb-id 66126))]
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
      (let [result (persistence/update! {:movie-depot movie-depot}
                                        {:id               (:id existing)
                                         :monitored        false
                                         :last-search-time "2025-10-10T00:00:00Z"
                                         :tmdb-id          (:tmdb-id existing)})]
        (is (= :updated (:status result)))
        (is (= false (get-in result [:movie :monitored])))
        (is (= "2025-10-10T00:00:00Z" (get-in result [:movie :last-search-time])))
        (is (= false (:monitored @captured)))))))

(deftest update-merges-existing-fields-into-event-payload
  (let [existing (canonical-existing)
        captured (atom nil)
        env      {:clock (constantly "2025-10-21T00:00:00Z") :movie-depot movie-depot}
        patch    {:id                   (:id existing)
                  :monitored            false
                  :minimum-availability "inCinemas"
                  :movie-metadata-id    999
                  :last-search-time     "2025-10-20T12:30:00Z"}]
    (with-redefs [pstate/movie-by-id             (fn [_env id & _] (when (= (:id existing) id) existing))
                  pstate/movie-id-by-metadata-id (fn [_env metadata-id & _]
                                                   (when (= metadata-id (:movie-metadata-id existing)) (:id existing)))
                  depot/update!                  (fn [{:keys [movie]}]
                                                   (reset! captured movie)
                                                   {:status :updated :movie {:id (:id movie)}})]
      (let [result   (persistence/update! env patch)
            expected (-> (merge existing patch)
                         (model/normalize (:clock env))
                         (assoc :id (:id existing)))]
        (is (= expected @captured))
        (is (= :updated (:status result)))
        (is (= (:minimum-availability expected) (get-in result [:movie :minimum-availability])))
        (is (= (:movie-metadata-id expected) (get-in result [:movie :movie-metadata-id])))
        (is (= (:path expected) (get-in result [:movie :path])))
        (is (= (:tmdb-id expected) (get-in result [:movie :tmdb-id])))))))

(deftest update-requires-id
  (with-redefs [pstate/movie-by-id (fn [& _] (throw (ex-info "should not call" {})))]
    (let [result (persistence/update! {:movie-depot movie-depot} {:monitored false})]
      (is (= :invalid (:status result)))
      (is (= ["id must be a positive integer"] (:errors result))))))

(deftest update-requires-mutable-fields
  (let [existing (canonical-existing)]
    (with-redefs [pstate/movie-by-id (fn [_env id & _] (when (= (:id existing) id) existing))]
      (let [result (persistence/update! {:movie-depot movie-depot} {:id (:id existing) :tmdb-id (:tmdb-id existing)})]
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
                                                   (cond (= metadata-id (:movie-metadata-id existing)) (:id existing)
                                                         (= metadata-id 999)                           222
                                                         :else                                         nil))
                  depot/update!                  (fn [& _] (throw (ex-info "should not write" {})))]
      (let [result (persistence/update! {:movie-depot movie-depot} {:id (:id existing) :movie-metadata-id 999})]
        (is (= :duplicate (:status result)))
        (is (= 222 (:existing-id result)))))))

(deftest update-validates-new-values
  (let [existing (canonical-existing)]
    (with-redefs [pstate/movie-by-id             (fn [_env id & _] (when (= (:id existing) id) existing))
                  pstate/movie-id-by-metadata-id (fn [& _] (:id existing))]
      (let [result (persistence/update! {:movie-depot movie-depot}
                                        {:id (:id existing) :quality-profile-id -1 :tmdb-id (:tmdb-id existing)})]
        (is (= :invalid (:status result)))
        (is (seq (:errors result)))))))

(deftest update-requires-depot
  (let [existing (canonical-existing)]
    (with-redefs [pstate/movie-by-id             (fn [_env id & _] (when (= (:id existing) id) existing))
                  pstate/movie-id-by-metadata-id (fn [& _] (:id existing))]
      (let [result (persistence/update! {} {:id (:id existing) :monitored false :tmdb-id (:tmdb-id existing)})]
        (is (= :error (:status result)))
        (is (= ["movie-depot handle not provided"] (:errors result)))))))
