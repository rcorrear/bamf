(ns bamf.movies.rama.module-integration
  (:use [com.rpl rama] [com.rpl.rama path])
  (:require [bamf.casing :as casing]
            [bamf.movies.rama.client.pstate :as pstate]
            [bamf.movies.rama.common :as common]
            [bamf.movies.rama.module.constants :refer [movies-etl-name]]
            [bamf.movies.rama.module.core :as mm]
            [bamf.movies.model :as model]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [com.rpl.rama.test :as rtest]))

(defn- movie-payload-from-resource
  [resource-name]
  (let [parsed        (casing/->kebab-keys (json/read-str (slurp (io/resource resource-name)) :key-fn keyword))
        core-keys     [:add-options :added :folder-name :imdb-id :monitored :movie-file-id :path :quality-profile-id
                       :root-folder-path :tags :title :title-slug :tmdb-id :year]
        metadata-keys [:images :genres :sort-title :clean-title :original-title :clean-original-title :original-language
                       :status :last-info-sync :runtime :in-cinemas :physical-release :digital-release :secondary-year
                       :ratings :recommendations :certification :you-tube-trailer-id :studio :overview :website
                       :popularity :collection :minimum-availability]
        base          (merge (select-keys parsed core-keys) (select-keys parsed metadata-keys))
        normalized    (merge base (model/normalize-metadata (model/extract-metadata base)))]
    (delay (-> base
               (merge normalized)
               (assoc :path             (or (:path base) (:folder-name base) "/media/video/movies/sample")
                      :last-search-time nil
                      :monitor          (get-in base [:add-options :monitor])
                      :search-for-movie (get-in base [:add-options :search-for-movie])
                      :tags             (set (or (:tags base) []))
                      :target-system    "radarr")
               (dissoc :added :add-options :id)
               common/map->MoviePayload))))

(def sample-movie-row (movie-payload-from-resource "movie-save-request.json"))
(def response-movie-row (movie-payload-from-resource "movie-save-response.json"))

(def http-shaped-payload
  (delay (let [parsed (casing/->kebab-keys
                       (json/read-str (slurp (io/resource "movie-save-request.json")) :key-fn keyword))]
           (model/normalize (assoc parsed :target-system "radarr") (constantly "2025-12-14T03:09:54Z")))))

(def ^:private metadata-keys
  [:images :genres :sort-title :clean-title :original-title :clean-original-title :original-language :status
   :last-info-sync :runtime :in-cinemas :physical-release :digital-release :secondary-year :ratings :recommendations
   :certification :you-tube-trailer-id :studio :overview :website :popularity :collection :year :minimum-availability])

(defn- with-module
  [f]
  (let [ipc (rtest/create-ipc)]
    (try (rtest/launch-module! ipc mm/MovieModule {:tasks 4 :threads 2})
         (f ipc)
         (finally (try (.close ipc) (catch Exception _))))))

(deftest save-movie
  (with-module
   (fn [ipc]
     (let [module-name       (get-module-name mm/MovieModule)
           rama-env          {:movies/env {:ipc ipc}}
           tmdb-id           (:tmdb-id @sample-movie-row)
           movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
           ack-response      (foreign-append! movie-saves-depot (common/movie-created-event @sample-movie-row) :ack)
           ack-movie         (when ack-response (get ack-response movies-etl-name))
           movie-id          (or (get-in ack-movie [:movie :id]) (pstate/movie-id-by-tmdb-id rama-env tmdb-id))
           saved             (pstate/movie-by-id rama-env movie-id)]
       (is (pos? movie-id))
       (when ack-movie (is (= :stored (:status ack-movie))) (is (= java.lang.Long (class movie-id))))
       (is (map? saved))
       (is (= movie-id (pstate/movie-id-by-tmdb-id rama-env tmdb-id)))
       (is (contains? (pstate/movie-ids-by-monitor rama-env (:monitor @sample-movie-row)) movie-id))
       (is (contains? (pstate/movie-ids-by-target-system rama-env (:target-system @sample-movie-row)) movie-id))
       (is (contains? (pstate/movie-ids-by-monitored rama-env) movie-id))
       (doseq [tag (:tags @sample-movie-row)] (is (contains? (pstate/movie-ids-by-tag rama-env tag) movie-id)))))))

(deftest save-movie-persists-metadata
  (with-module (fn [ipc]
                 (let [module-name       (get-module-name mm/MovieModule)
                       rama-env          {:movies/env {:ipc ipc}}
                       movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
                       payload           @sample-movie-row
                       ack-response      (foreign-append! movie-saves-depot (common/movie-created-event payload) :ack)
                       ack-movie         (when ack-response (get ack-response movies-etl-name))
                       movie-id          (or (get-in ack-movie [:movie :id])
                                             (pstate/movie-id-by-tmdb-id rama-env (:tmdb-id payload)))
                       stored            (pstate/metadata-by-movie-id rama-env movie-id)
                       expected          (->> (select-keys payload metadata-keys)
                                              (remove (comp nil? val))
                                              (into {}))]
                   (is (pos? movie-id))
                   (is (= expected stored))))))

(deftest update-movie-updates-metadata
  (with-module
   (fn [ipc]
     (let [module-name       (get-module-name mm/MovieModule)
           rama-env          {:movies/env {:ipc ipc}}
           movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
           payload           @sample-movie-row
           ack-response      (foreign-append! movie-saves-depot (common/movie-created-event payload) :ack)
           ack-movie         (when ack-response (get ack-response movies-etl-name))
           movie-id          (or (get-in ack-movie [:movie :id])
                                 (pstate/movie-id-by-tmdb-id rama-env (:tmdb-id payload)))
           existing          (pstate/metadata-by-movie-id rama-env movie-id)
           patch             {:genres ["Mystery"] :overview nil}
           expected          (-> existing
                                 (assoc :genres ["Mystery"])
                                 (dissoc :overview))
           update-payload    (-> @response-movie-row
                                 (assoc :id movie-id :metadata expected)
                                 (merge patch))]
       (is (pos? movie-id))
       (is (seq existing))
       (foreign-append! movie-saves-depot (common/movie-updated-event update-payload) :ack)
       (is (= expected (pstate/metadata-by-movie-id rama-env movie-id)))
       (foreign-append! movie-saves-depot (common/movie-updated-event {:id movie-id :metadata {}}) :ack)
       (is (nil? (pstate/metadata-by-movie-id rama-env movie-id)))))))

(deftest save-movie-from-http-shaped-payload
  (with-module
   (fn [ipc]
     (let [module-name       (get-module-name mm/MovieModule)
           rama-env          {:movies/env {:ipc ipc}}
           movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
           payload           (assoc @http-shaped-payload :tags ["movie"])
           ack-response      (foreign-append! movie-saves-depot (common/movie-created-event payload) :ack)
           ack-movie         (when ack-response (get ack-response movies-etl-name))
           movie-id          (or (get-in ack-movie [:movie :id])
                                 (pstate/movie-id-by-tmdb-id rama-env (:tmdb-id payload)))
           stored            (pstate/movie-by-id rama-env movie-id)]
       (is (pos? movie-id))
       (is (= (:tmdb-id payload) (:tmdb-id stored)))
       (is (= (:quality-profile-id payload) (:quality-profile-id stored)))
       (is (= (:monitored payload) (:monitored stored)))
       (is (= (set (:tags payload)) (set (:tags stored))))
       (is (contains? (or (pstate/movie-ids-by-target-system rama-env "radarr") #{}) movie-id))
       (is (contains? (or (pstate/movie-ids-by-tag rama-env "movie") #{}) movie-id))))))

(deftest create-coerces-vector-tags
  (with-module (fn [ipc]
                 (let [module-name       (get-module-name mm/MovieModule)
                       rama-env          {:movies/env {:ipc ipc}}
                       movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
                       payload           (-> @sample-movie-row
                                             (assoc :tmdb-id 99001 :tags ["alpha" "beta"]))
                       ack-response      (foreign-append! movie-saves-depot (common/movie-created-event payload) :ack)
                       ack-movie         (when ack-response (get ack-response movies-etl-name))
                       movie-id          (or (get-in ack-movie [:movie :id])
                                             (pstate/movie-id-by-tmdb-id rama-env (:tmdb-id payload)))
                       stored            (pstate/movie-by-id rama-env movie-id)]
                   (is (pos? movie-id))
                   (is (= #{"alpha" "beta"} (set (:tags stored))))
                   (is (contains? (or (pstate/movie-ids-by-tag rama-env "alpha") #{}) movie-id))
                   (is (contains? (or (pstate/movie-ids-by-tag rama-env "beta") #{}) movie-id))))))

(deftest create-accepts-set-tags
  (with-module (fn [ipc]
                 (let [module-name       (get-module-name mm/MovieModule)
                       rama-env          {:movies/env {:ipc ipc}}
                       movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
                       payload           (-> @sample-movie-row
                                             (assoc :tmdb-id 99002 :tags #{"gamma" "delta"}))
                       ack-response      (foreign-append! movie-saves-depot (common/movie-created-event payload) :ack)
                       ack-movie         (when ack-response (get ack-response movies-etl-name))
                       movie-id          (or (get-in ack-movie [:movie :id])
                                             (pstate/movie-id-by-tmdb-id rama-env (:tmdb-id payload)))
                       stored            (pstate/movie-by-id rama-env movie-id)]
                   (is (pos? movie-id))
                   (is (= #{"gamma" "delta"} (set (:tags stored))))
                   (is (contains? (or (pstate/movie-ids-by-tag rama-env "gamma") #{}) movie-id))
                   (is (contains? (or (pstate/movie-ids-by-tag rama-env "delta") #{}) movie-id))))))

(deftest update-movie
  (with-module
   (fn [ipc]
     (let [module-name       (get-module-name mm/MovieModule)
           rama-env          {:movies/env {:ipc ipc}}
           tmdb-id           (:tmdb-id @sample-movie-row)
           movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
           ack-response      (foreign-append! movie-saves-depot (common/movie-created-event @sample-movie-row) :ack)
           ack-movie         (when ack-response (get ack-response movies-etl-name))
           movie-id          (or (get-in ack-movie [:movie :id]) (pstate/movie-id-by-tmdb-id rama-env tmdb-id))
           existing-before   (pstate/movie-by-id rama-env movie-id)
           update-payload    (-> @response-movie-row
                                 (assoc :id               movie-id
                                        :tmdb-id          tmdb-id
                                        :monitored        false
                                        :last-search-time "2025-10-10T00:00:00Z"
                                        ;; attempt to mutate disallowed fields
                                        :title            "should-not-change"
                                        :year             3000
                                        :imdb-id          "tt0000000"))
           update-response   (foreign-append! movie-saves-depot (common/movie-updated-event update-payload) :ack)
           ack-update        (when update-response (get update-response movies-etl-name))
           updated           (pstate/movie-by-id rama-env movie-id)
           monitored-set     (or (pstate/movie-ids-by-monitored rama-env) #{})]
       (when ack-update (is (= :updated (:status ack-update))) (is (= movie-id (get-in ack-update [:movie :id]))))
       (is (= movie-id (pstate/movie-id-by-tmdb-id rama-env tmdb-id)))
       (let [allowed-fields   [:last-search-time :monitored :path :quality-profile-id :root-folder-path]
             expected-updated (-> @response-movie-row
                                  (assoc :tmdb-id          tmdb-id
                                         :last-search-time (java.time.Instant/parse "2025-10-10T00:00:00Z")
                                         :monitored        false)
                                  (select-keys allowed-fields))
             expected-allowed expected-updated
             disallowed-keys  (remove (set allowed-fields) (keys existing-before))]
         (is (= expected-allowed (select-keys updated (keys expected-allowed))))
         (doseq [k disallowed-keys] (is (= (get existing-before k) (get updated k)))))
       (is (not (contains? monitored-set movie-id)))))))

(deftest monitored-index-updates
  (with-module
   (fn [ipc]
     (let [module-name       (get-module-name mm/MovieModule)
           rama-env          {:movies/env {:ipc ipc}}
           tmdb-id           (:tmdb-id @sample-movie-row)
           movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
           ack-response      (foreign-append! movie-saves-depot (common/movie-created-event @sample-movie-row) :ack)
           ack-movie         (when ack-response (get ack-response movies-etl-name))
           movie-id          (or (get-in ack-movie [:movie :id]) (pstate/movie-id-by-tmdb-id rama-env tmdb-id))]
       (is (contains? (pstate/movie-ids-by-monitored rama-env) movie-id))
       (let [update-payload (-> @response-movie-row
                                (assoc :id movie-id :monitored false))]
         (foreign-append! movie-saves-depot (common/movie-updated-event update-payload) :ack))
       (let [monitored-set (or (pstate/movie-ids-by-monitored rama-env) #{})]
         (is (not (contains? monitored-set movie-id))))
       (let [update-payload (-> @response-movie-row
                                (assoc :id movie-id :monitored true))]
         (foreign-append! movie-saves-depot (common/movie-updated-event update-payload) :ack))
       (let [monitored-set (or (pstate/movie-ids-by-monitored rama-env) #{})]
         (is (contains? monitored-set movie-id)))))))

(deftest tags-index-updates-from-empty-to-non-empty
  (with-module
   (fn [ipc]
     (let [module-name       (get-module-name mm/MovieModule)
           rama-env          {:movies/env {:ipc ipc}}
           tmdb-id           (:tmdb-id @sample-movie-row)
           movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
           ack-response      (foreign-append! movie-saves-depot (common/movie-created-event @sample-movie-row) :ack)
           ack-movie         (when ack-response (get ack-response movies-etl-name))
           movie-id          (or (get-in ack-movie [:movie :id]) (pstate/movie-id-by-tmdb-id rama-env tmdb-id))
           original-tags     (:tags ack-movie)
           new-tags          #{"new-tag"}]
       ;; original tags indexed on create
       (doseq [tag original-tags] (is (contains? (pstate/movie-ids-by-tag rama-env tag) movie-id)))
       ;; update tags
       (let [update-payload (-> @response-movie-row
                                (assoc :id movie-id :tags new-tags))]
         (foreign-append! movie-saves-depot (common/movie-updated-event update-payload) :ack))
       ;; old tags removed
       (doseq [tag original-tags] (is (not (contains? (or (pstate/movie-ids-by-tag rama-env tag) #{}) movie-id))))
       ;; new tags added
       (is (contains? (or (pstate/movie-ids-by-tag rama-env "new-tag") #{}) movie-id))
       ;; movie row contains new tags
       (is (= new-tags (get (pstate/movie-by-id rama-env movie-id) :tags)))))))

(deftest tags-index-updates-from-empty-to-empty
  (with-module
   (fn [ipc]
     (let [module-name       (get-module-name mm/MovieModule)
           rama-env          {:movies/env {:ipc ipc}}
           tmdb-id           (:tmdb-id @sample-movie-row)
           movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
           ack-response      (foreign-append! movie-saves-depot (common/movie-created-event @sample-movie-row) :ack)
           ack-movie         (when ack-response (get ack-response movies-etl-name))
           movie-id          (or (get-in ack-movie [:movie :id]) (pstate/movie-id-by-tmdb-id rama-env tmdb-id))
           original-tags     (:tags ack-movie)
           new-tags          #{}]
       ;; original tags indexed on create
       (doseq [tag original-tags] (is (contains? (pstate/movie-ids-by-tag rama-env tag) movie-id)))
       ;; update tags
       (let [update-payload (-> @response-movie-row
                                (assoc :id movie-id :tags new-tags))]
         (foreign-append! movie-saves-depot (common/movie-updated-event update-payload) :ack))
       ;; movie row contains no tags
       (is (= #{} (get (pstate/movie-by-id rama-env movie-id) :tags)))))))

(deftest tags-index-updates-from-non-empty-to-non-empty
  (with-module
   (fn [ipc]
     (let [module-name       (get-module-name mm/MovieModule)
           rama-env          {:movies/env {:ipc ipc}}
           tmdb-id           (:tmdb-id @sample-movie-row)
           movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
           ack-response      (foreign-append! movie-saves-depot
                                              (assoc (common/movie-created-event @sample-movie-row) :tags #{"old-tag"})
                                              :ack)
           ack-movie         (when ack-response (get ack-response movies-etl-name))
           movie-id          (or (get-in ack-movie [:movie :id]) (pstate/movie-id-by-tmdb-id rama-env tmdb-id))
           original-tags     (:tags ack-movie)
           new-tags          #{"new-tag"}]
       ;; original tags indexed on create
       (doseq [tag original-tags] (is (contains? (pstate/movie-ids-by-tag rama-env tag) movie-id)))
       ;; update tags
       (let [update-payload (-> @response-movie-row
                                (assoc :id movie-id :tags new-tags))]
         (foreign-append! movie-saves-depot (common/movie-updated-event update-payload) :ack))
       ;; old tags removed
       (doseq [tag original-tags] (is (not (contains? (or (pstate/movie-ids-by-tag rama-env tag) #{}) movie-id))))
       ;; new tags added
       (is (contains? (or (pstate/movie-ids-by-tag rama-env "new-tag") #{}) movie-id))
       ;; movie row contains new tags
       (is (= new-tags (get (pstate/movie-by-id rama-env movie-id) :tags)))))))

(deftest tags-index-updates-from-non-empty-to-empty
  (with-module
   (fn [ipc]
     (let [module-name       (get-module-name mm/MovieModule)
           rama-env          {:movies/env {:ipc ipc}}
           tmdb-id           (:tmdb-id @sample-movie-row)
           movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
           ack-response      (foreign-append! movie-saves-depot
                                              (assoc (common/movie-created-event @sample-movie-row) :tags #{"old-tag"})
                                              :ack)
           ack-movie         (when ack-response (get ack-response movies-etl-name))
           movie-id          (or (get-in ack-movie [:movie :id]) (pstate/movie-id-by-tmdb-id rama-env tmdb-id))
           original-tags     (:tags ack-movie)
           new-tags          #{}]
       ;; original tags indexed on create
       (doseq [tag original-tags] (is (contains? (pstate/movie-ids-by-tag rama-env tag) movie-id)))
       ;; update tags
       (let [update-payload (-> @response-movie-row
                                (assoc :id movie-id :tags new-tags))]
         (foreign-append! movie-saves-depot (common/movie-updated-event update-payload) :ack))
       ;; old tags removed
       (doseq [tag original-tags] (is (not (contains? (or (pstate/movie-ids-by-tag rama-env tag) #{}) movie-id))))
       ;; movie row contains no tags
       (is (= #{} (get (pstate/movie-by-id rama-env movie-id) :tags)))))))
