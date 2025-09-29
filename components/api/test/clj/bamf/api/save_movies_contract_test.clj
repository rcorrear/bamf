(ns bamf.api.save-movies-contract-test
  (:require [bamf.api.save-movies :as api]
            [clojure.test :refer [deftest is]]))

(def valid-payload
  {:title               "Dune"
   :titleSlug           "12345"
   :qualityProfileId    1
   :path                "/movies/Dune (2021)"
   :rootFolderPath      "/movies"
   :monitored           true
   :tmdbId              12345
   :movieMetadataId     0
   :movieFileId         0
   :minimumAvailability "released"
   :tags                ["scifi" "4k"]
   :addOptions          {:searchForMovie true}
   :targetSystem        "radarr"})

(defn request [payload] {:body payload})

(defn validator
  [payload]
  (let [errors (cond-> []
                 (nil? (:path payload)) (conj "path is required")
                 (and (:titleSlug payload) (:tmdbId payload) (not= (str (:tmdbId payload)) (:titleSlug payload)))
                 (conj "titleSlug must match tmdbId"))]
    (when (seq errors) errors)))

(deftest save-movie-success-contract
  (let [call      (atom nil)
        persisted (assoc valid-payload
                         :id              42
                         :movieMetadataId 12345
                         :added           "2025-09-21T12:00:00Z"
                         :lastSearchTime  "2025-09-21T12:00:00Z")
        resp      (api/save-movie {:movie/validate validator
                                   :movie/save!    (fn [movie] (reset! call movie) {:status :stored :movie persisted})}
                                  (request valid-payload))
        body      (:body resp)]
    (is (= 201 (:status resp)))
    (is (= (dissoc persisted :targetSystem) body))
    (is (= valid-payload @call))))

(deftest save-movie-duplicate-contract
  (let [resp (api/save-movie
              {:movie/validate validator
               :movie/save!    (fn [_movie]
                                 {:status :duplicate :existing-id 7 :reason "duplicate-metadata" :field :tmdbId})}
              (request valid-payload))]
    (is (= 400 (:status resp)))
    (is (= {:message "Movie already exists"
            :reason  "duplicate-metadata"
            :errors  {"tmdbId" ["Movie already exists (id 7)"]}}
           (:body resp)))))

(deftest save-movie-invalid-contract
  (let [resp (api/save-movie {:movie/validate validator
                              :movie/save!    (fn [_movie] {:status :invalid :errors ["invalid"]})}
                             (request valid-payload))]
    (is (= 400 (:status resp)))
    (is (= {:message "Validation failed" :errors ["invalid"]} (:body resp)))))

(deftest save-movie-required-fields
  (let [resp (api/save-movie {:movie/validate validator
                              :movie/save!    (fn [_movie] (throw (ex-info "should not save" {})))}
                             (request (dissoc valid-payload :path)))]
    (is (= 400 (:status resp)))
    (is (= {:message "Validation failed" :errors ["path is required"]} (:body resp)))))

(deftest save-movie-title-slug-must-match-tmdb
  (let [resp (api/save-movie {:movie/validate validator
                              :movie/save!    (fn [_movie] (throw (ex-info "should not save" {})))}
                             (request (assoc valid-payload :titleSlug "not-matching")))]
    (is (= 400 (:status resp)))
    (is (= {:message "Validation failed" :errors ["titleSlug must match tmdbId"]} (:body resp)))))
