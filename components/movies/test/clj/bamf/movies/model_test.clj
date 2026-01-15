(ns bamf.movies.model-test
  (:require [bamf.movies.model :as model]
            [clojure.test :refer [deftest is testing]])
  (:import (java.time OffsetDateTime)))

(deftest iso-utc-parses-and-truncates
  (testing "string timestamps are parsed, truncated to seconds, and kept in UTC"
    (is (= "2024-07-16T12:30:45Z" (model/->iso-utc "2024-07-16T12:30:45.987Z" #(throw (ex-info "unused" {}))))))
  (testing "offset timestamps are converted to UTC and truncated"
    (let [dt (OffsetDateTime/parse "2024-01-01T00:00:00.999+02:00")]
      (is (= "2023-12-31T22:00:00Z" (model/->iso-utc dt #(throw (ex-info "unused" {}))))))))

(deftest iso-utc-rejects-sentinel
  (testing "0001-01-01 placeholder values are treated as nil"
    (is (nil? (model/->iso-utc "0001-01-01T00:00:00" (constantly nil))))
    (is (nil? (model/->iso-utc "0001-01-01T00:00:00Z" (constantly nil))))))

(deftest iso-utc-fallbacks
  (testing "falls back when primary value is nil"
    (is (= "2025-01-01T00:00:00Z" (model/->iso-utc nil (constantly "2025-01-01T00:00:00Z")))))
  (testing "returns nil when both value and fallback are nil" (is (nil? (model/->iso-utc nil (constantly nil))))))

(deftest validate-required-fields
  (testing "missing required fields surface readable errors"
    (let [errors (set (model/validate {}))]
      (is (contains? errors "title is required"))
      (is (contains? errors "monitored is required"))
      (is (contains? errors "qualityProfileId is required"))
      (is (contains? errors "minimumAvailability is required"))
      (is (contains? errors "tmdbId is required"))))
  (testing "titleSlug must match tmdbId"
    (is (= ["titleSlug must match tmdbId"]
           (model/validate {:title                "X"
                            :monitored            true
                            :quality-profile-id   1
                            :minimum-availability "released"
                            :tmdb-id              5
                            :title-slug           "nope"})))))

(defn- normalize*
  ([movie] (normalize* movie (constantly "2024-01-01T00:00:00.123Z")))
  ([movie clock] (model/normalize movie clock)))

(deftest normalize-defaults-and-coercions
  (testing "applies defaults, coercions, and canonical forms"
    (let [movie      {:title                " Foo "
                      :monitored            true
                      :quality-profile-id   2
                      :minimum-availability "released"
                      :tmdb-id              42
                      :tags                 ["SciFi" "007" "scifi"]
                      :path                 "  /media/movies/Dune "
                      :target-system        "  RADARR  "}
          normalized (normalize* movie)]
      (is (= "2024-01-01T00:00:00Z" (:added normalized)))
      (is (nil? (:last-search-time normalized)))
      (is (= 42 (:tmdb-id normalized)))
      (is (= "/media/movies/Dune" (:path normalized)))
      (is (= "radarr" (:target-system normalized)))
      (is (= #{"scifi" "7"} (set (:tags normalized))))
      (is (= 0 (:size-on-disk normalized)))
      (is (= {} (:add-options normalized))))))

(deftest normalize-retains-provided-last-search-and-size
  (let [movie      {:title                "X"
                    :monitored            false
                    :quality-profile-id   3
                    :minimum-availability "released"
                    :tmdb-id              99
                    :last-search-time     "2024-02-02T10:10:10.555Z"
                    :size-on-disk         12345}
        normalized (normalize* movie)]
    (is (= "2024-02-02T10:10:10Z" (:last-search-time normalized)))
    (is (= 12345 (:size-on-disk normalized)))))

(deftest normalize-respects-explicit-target-system
  (let [movie      {:title                "X"
                    :monitored            true
                    :quality-profile-id   1
                    :minimum-availability "released"
                    :tmdb-id              7
                    :target-system        " Jellyfin "}
        normalized (normalize* movie)]
    (is (= "jellyfin" (:target-system normalized)))))
