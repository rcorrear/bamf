(ns bamf.movies.inspection-test
  (:require [bamf.movies.inspection :as inspection]
            [bamf.movies.rama.client.pstate :as pstate]
            [clojure.test :refer [deftest is testing]]))

(deftest list-defaults-to-target-system
  (testing "defaults to radarr target when no filters supplied"
    (with-redefs [pstate/movie-ids-by-target-system (fn [_ target] (is (= "radarr" target)) #{5 2})
                  pstate/movie-by-id                (fn [_ id & _] {:title (str "Movie" id)})
                  pstate/metadata-by-movie-id       (fn [& _] nil)]
      (is (= {:status :ok :movies [{:id 2 :title "Movie2"} {:id 5 :title "Movie5"}]}
             (inspection/list-movies {:movies/env {}} {}))))))

(deftest list-tmdb-filter
  (testing "returns movie when tmdb-id provided"
    (with-redefs [pstate/movie-id-by-tmdb-id  (fn [_ tmdb-id] (is (= 55 tmdb-id)) 9)
                  pstate/movie-by-id          (fn [_ id & _] (when (= 9 id) {:title "Movie55"}))
                  pstate/metadata-by-movie-id (fn [& _] nil)]
      (is (= {:status :ok :movies [{:id 9 :title "Movie55"}]}
             (inspection/list-movies {:movies/env {}} {:tmdb-id "55"}))))))
