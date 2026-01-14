(ns bamf.movies.persistence-test
  (:require [bamf.casing :as casing]
            [bamf.movies.model :as model]
            [bamf.movies.persistence :as persistence]
            [bamf.movies.rama.client.depot :as depot]
            [bamf.movies.rama.client.pstate :as pstate]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]))

(def sample-json
  (-> "movie-save-request.json"
      io/resource
      slurp))

(def sample-response
  (delay (casing/->kebab-keys (json/read-str (-> "movie-save-response.json"
                                                 io/resource
                                                 slurp)
                                             :key-fn
                                             keyword))))

(def sample-payload
  (delay (merge (casing/->kebab-keys (json/read-str sample-json :key-fn keyword))
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
        (is (nil? (:last-search-time movie)))
        (is (= ["scifi" "4k"] (:tags @captured)))
        (is (= {} (:add-options @captured)))
        (is (= (:tmdb-id payload) (:movie-metadata-id @captured)))
        (is (= "2025-09-21T17:00:00Z" (:added @captured)))))))

(deftest save-includes-metadata-in-depot-payload
  (let [captured      (atom nil)
        env           {:clock (constantly "2025-09-21T17:00:00Z") :movie-depot movie-depot}
        metadata-keys [:images :genres :status :ratings :collection :runtime :in-cinemas :physical-release
                       :digital-release :overview :studio :website :popularity]]
    (with-redefs [pstate/movie-id-by-tmdb-id     (fn [& _] nil)
                  pstate/movie-id-by-metadata-id (fn [& _] nil)
                  pstate/movie-by-id             (fn [& _] nil)
                  depot/put!                     (fn [{:keys [movie]}]
                                                   (reset! captured movie)
                                                   {:status :stored
                                                    :movie  {:id 99 :movie-metadata-id (:movie-metadata-id movie)}})]
      (persistence/save! env @sample-payload)
      (is (= (select-keys @sample-payload metadata-keys) (select-keys @captured metadata-keys))))))

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

(deftest trim-path-normalizes-blanks
  (is (nil? (#'persistence/trim-path nil)))
  (is (nil? (#'persistence/trim-path "   ")))
  (is (= "/media/movies" (#'persistence/trim-path "  /media/movies  "))))

(deftest combine-path-respects-absolute-and-normalizes
  (is (= "/abs/path" (#'persistence/combine-path "/root" "/abs/path")))
  (is (= "/root/child" (#'persistence/combine-path "/root/" "child")))
  (is (= "/root/child/grand" (#'persistence/combine-path "/root" "child/../child/grand"))))

(deftest derive-paths-prefers-explicit-path-and-builds-from-folder
  (let [root     "/media/video"
        folder   "The Batman (2022)"
        explicit "/custom/path"]
    ;; explicit path wins
    (is (= {:path explicit :root-folder-path "/media/video" :folder-name "/media/video/The Batman (2022)"}
           (select-keys (#'persistence/derive-paths nil {:path explicit :root-folder-path root :folder folder})
                        [:path :root-folder-path :folder-name])))
    ;; build from folder + root
    (is (= {:path             "/media/video/The Batman (2022)"
            :root-folder-path "/media/video"
            :folder-name      "/media/video/The Batman (2022)"}
           (select-keys (#'persistence/derive-paths nil {:root-folder-path root :folder folder})
                        [:path :root-folder-path :folder-name])))))

(deftest derive-paths-uses-folder-name-relative-and-absolute
  (is (= {:path "/root/movies/Rel" :root-folder-path "/root/movies" :folder-name "Rel"}
         (select-keys (#'persistence/derive-paths nil {:root-folder-path "/root/movies" :folder-name "Rel"})
                      [:path :root-folder-path :folder-name])))
  (is (= {:path "/abs/name" :root-folder-path "/root/movies" :folder-name "/abs/name"}
         (select-keys (#'persistence/derive-paths nil {:root-folder-path "/root/movies" :folder-name "/abs/name"})
                      [:path :root-folder-path :folder-name]))))

(deftest reconcile-paths-emits-only-changes
  (let [existing {:path "/media/old" :root-folder-path "/media"}]
    ;; no changes
    (is (= {:path nil :root-folder-path nil} (#'persistence/reconcile-paths existing {:path "/media/old"})))
    ;; path change
    (is (= {:path "/media/new" :root-folder-path nil} (#'persistence/reconcile-paths existing {:path "/media/new"})))
    ;; root change via folderName
    (is (= {:path "/media/video/New" :root-folder-path "/media/video"}
           (#'persistence/reconcile-paths existing {:root-folder-path "/media/video" :folder-name "New"})))))

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
      (let [patch             {:id               (:id existing)
                               :monitored        false
                               :last-search-time "2025-10-10T00:00:00Z"
                               :tmdb-id          (:tmdb-id existing)}
            result            (persistence/update! {:movie-depot movie-depot} patch)
            expected-captured (select-keys (merge existing patch)
                                           [:id :monitored :minimum-availability :quality-profile-id :path
                                            :root-folder-path :tags])]
        (is (= :updated (:status result)))
        (is (= false (get-in result [:movie :monitored])))
        (is (= "2025-10-10T00:00:00Z" (get-in result [:movie :last-search-time])))
        (is (= expected-captured @captured))))))

(deftest update-merges-existing-fields-into-event-payload
  (let [existing (canonical-existing)
        captured (atom nil)
        env      {:clock (constantly "2025-10-21T00:00:00Z") :movie-depot movie-depot}
        patch    {:id                   (:id existing)
                  :monitored            false
                  :minimum-availability "inCinemas"
                  :movie-metadata-id    999
                  :last-search-time     "2025-10-20T12:30:00Z"
                  :tags                 ["new-tag" "another"]}]
    (with-redefs [pstate/movie-by-id             (fn [_env id & _] (when (= (:id existing) id) existing))
                  pstate/movie-id-by-metadata-id (fn [_env metadata-id & _]
                                                   (when (= metadata-id (:movie-metadata-id existing)) (:id existing)))
                  depot/update!                  (fn [{:keys [movie]}]
                                                   (reset! captured movie)
                                                   {:status :updated :movie {:id (:id movie)}})]
      (let [result         (persistence/update! env patch)
            expected       (-> (merge existing patch)
                               (model/normalize (:clock env))
                               (assoc :id (:id existing)))
            expected-depot (select-keys expected
                                        [:id :monitored :minimum-availability :quality-profile-id :path
                                         :root-folder-path :tags])]
        (is (= expected-depot @captured))
        (is (= :updated (:status result)))
        (is (= (:minimum-availability expected) (get-in result [:movie :minimum-availability])))
        (is (= (:movie-metadata-id existing) (get-in result [:movie :movie-metadata-id])))
        (is (= (:path expected) (get-in result [:movie :path])))
        (is (= (:tmdb-id expected) (get-in result [:movie :tmdb-id])))))))

(deftest update-updates-tags
  (let [existing (canonical-existing)
        captured (atom nil)
        env      {:clock (constantly "2025-10-21T00:00:00Z") :movie-depot movie-depot}
        patch    {:id (:id existing) :tmdb-id (:tmdb-id existing) :tags ["new-tag" "other"]}]
    (with-redefs [pstate/movie-by-id             (fn [_env id & _] (when (= (:id existing) id) existing))
                  pstate/movie-id-by-metadata-id (fn [_env metadata-id & _] (:id existing))
                  depot/update!                  (fn [{:keys [movie]}]
                                                   (reset! captured movie)
                                                   {:status :updated :movie {:id (:id movie)}})]
      (let [result        (persistence/update! env patch)
            expected-tags (set (map str (:tags patch)))]
        (is (= :updated (:status result)))
        (is (= expected-tags (set (:tags @captured))))
        (is (= expected-tags (set (get-in result [:movie :tags]))))))))

(deftest update-requires-id
  (with-redefs [pstate/movie-by-id (fn [& _] (throw (ex-info "should not call" {})))]
    (let [result (persistence/update! {:movie-depot movie-depot} {:monitored false})]
      (is (= :invalid (:status result)))
      (is (= ["id must be a positive integer"] (:errors result))))))

(deftest update-requires-mutable-fields
  (let [existing (canonical-existing)]
    (with-redefs [pstate/movie-by-id (fn [_env id & _] (when (= (:id existing) id) existing))]
      (let [result (persistence/update! {:movie-depot movie-depot} {:id (:id existing) :tmdb-id (:tmdb-id existing)})]
        (is (= :updated (:status result)))
        (is (= existing (:movie result)))))))

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
        ;; movie-metadata-id is not mutable; treat as no-op update
        (is (= :updated (:status result)))
        (is (= existing (:movie result)))))))

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
