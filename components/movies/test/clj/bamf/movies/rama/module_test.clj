(ns bamf.movies.rama.module-test
  (:use [com.rpl rama] [com.rpl.rama path])
  (:require [bamf.movies.rama.client.pstate :as pstate]
            [bamf.movies.rama.common :as common]
            [bamf.movies.rama.module :as mm]
            [camel-snake-kebab.core :as csk]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [com.rpl.rama.test :as rtest]
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

(defn- movie-payload-from-resource
  [resource-name]
  (let [parsed (kebabize-keys (json/read-str (slurp (io/resource resource-name)) :key-fn keyword))
        base   (select-keys parsed
                            [:add-options :added :imdb-id :minimum-availability :monitored :movie-file-id
                             :quality-profile-id :root-folder-path :tags :title :title-slug :tmdb-id :year])]
    (delay (-> base
               (assoc :last-search-time  nil
                      :monitor           (get-in base [:add-options :monitor])
                      :movie-metadata-id (:tmdb-id parsed)
                      :search-for-movie  (get-in base [:add-options :search-for-movie])
                      :tags              (set (or (not-empty (:tags base)) #{"tag1" "tag2"}))
                      :target-system     "radarr")
               (dissoc :added :add-options :id :path)
               common/map->MoviePayload))))

(def sample-movie-row (movie-payload-from-resource "movie-request.json"))
(def response-movie-row (movie-payload-from-resource "movie-response.json"))

(defn- wait-for-value
  "Poll the supplied function until it returns a truthy value or times out."
  [timeout-ms f]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (if-let [result (f)]
        result
        (do (Thread/sleep 50)
            (when (> (System/currentTimeMillis) deadline)
              (throw (ex-info "Timed out waiting for Rama mutation" {:timeout-ms timeout-ms})))
            (recur))))))

(defn- append-with-retry
  [depot event]
  (try (foreign-append! depot event :ack)
       (catch Exception ex (let [data (ex-data ex)] (if (= :ack-timeout (:reason data)) nil (throw ex))))))

(deftest save-movie
  (let [ipc (rtest/create-ipc)]
    (try (rtest/launch-module! ipc mm/MovieModule {:tasks 4 :threads 2})
         (let [module-name       (get-module-name mm/MovieModule)
               rama-env          {:movies/env {:ipc ipc}}
               tmdb-id           (:tmdb-id @sample-movie-row)
               movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
               ack-response      (append-with-retry movie-saves-depot (common/movie-created-event @sample-movie-row))
               ack-movie         (when ack-response (get ack-response common/movies-etl-name))
               movie-id          (or (get-in ack-movie [:movie :id])
                                     (wait-for-value 10000 #(pstate/movie-id-by-tmdb-id rama-env tmdb-id)))
               saved             (wait-for-value 10000 #(pstate/movie-by-id rama-env movie-id))]
           (is (pos? movie-id))
           (when ack-movie (is (= :stored (:status ack-movie))) (is (= java.lang.Long (class movie-id))))
           (is (map? saved))
           (when ack-movie (is (= movie-id (get-in ack-movie [:movie :id]))))
           (is (= movie-id (pstate/movie-id-by-tmdb-id rama-env tmdb-id)))
           (is (= movie-id (pstate/movie-id-by-metadata-id rama-env (:movie-metadata-id @sample-movie-row))))
           (is (contains? (pstate/movie-ids-by-monitor rama-env (:monitor @sample-movie-row)) movie-id))
           (is (contains? (pstate/movie-ids-by-target-system rama-env (:target-system @sample-movie-row)) movie-id))
           (is (contains? (pstate/movie-ids-by-monitored rama-env) movie-id))
           (doseq [tag (:tags @sample-movie-row)] (is (contains? (pstate/movie-ids-by-tag rama-env tag) movie-id))))
         (finally (try (.close ipc) (catch Exception _))))))

(deftest update-movie
  (let [ipc (rtest/create-ipc)]
    (try (rtest/launch-module! ipc mm/MovieModule {:tasks 4 :threads 2})
         (let [module-name       (get-module-name mm/MovieModule)
               rama-env          {:movies/env {:ipc ipc}}
               tmdb-id           (:tmdb-id @sample-movie-row)
               original-meta-id  (:movie-metadata-id @sample-movie-row)
               movie-saves-depot (foreign-depot ipc module-name common/movie-depot-name)
               ack-response      (append-with-retry movie-saves-depot (common/movie-created-event @sample-movie-row))
               ack-movie         (when ack-response (get ack-response common/movies-etl-name))
               movie-id          (or (get-in ack-movie [:movie :id])
                                     (wait-for-value 10000 #(pstate/movie-id-by-tmdb-id rama-env tmdb-id)))
               _ (wait-for-value 10000 #(pstate/movie-by-id rama-env movie-id))
               new-metadata-id   (+ 100 original-meta-id)
               update-payload    (assoc @response-movie-row
                                        :id                movie-id
                                        :tmdb-id           tmdb-id
                                        :movie-metadata-id new-metadata-id
                                        :monitored         false
                                        :last-search-time  "2025-10-10T00:00:00Z")
               update-response   (append-with-retry movie-saves-depot (common/movie-updated-event update-payload))
               ack-update        (when update-response (get update-response common/movies-etl-name))
               updated           (wait-for-value 10000 #(pstate/movie-by-id rama-env movie-id))
               monitored-set     (or (pstate/movie-ids-by-monitored rama-env) #{})]
           (when ack-update (is (= :updated (:status ack-update))) (is (= movie-id (get-in ack-update [:movie :id]))))
           (is (= movie-id (pstate/movie-id-by-tmdb-id rama-env tmdb-id)))
           ;; movieMetadataId should remain unchanged during update
           (is (= movie-id (pstate/movie-id-by-metadata-id rama-env original-meta-id)))
           (is (nil? (pstate/movie-id-by-metadata-id rama-env new-metadata-id)))
           (let [expected-updated (-> @response-movie-row
                                      (assoc :tmdb-id           tmdb-id
                                             :movie-metadata-id original-meta-id
                                             :last-search-time  (java.time.Instant/parse "2025-10-10T00:00:00Z")))
                 expected-subset  (select-keys expected-updated
                                               [:minimum-availability :quality-profile-id :root-folder-path :tmdb-id
                                                :movie-metadata-id :last-search-time])]
             (is (= expected-subset (select-keys updated (keys expected-subset)))))
           (is (not (contains? monitored-set movie-id))))
         (finally (try (.close ipc) (catch Exception _))))))
