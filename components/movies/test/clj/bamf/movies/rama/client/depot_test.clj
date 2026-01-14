(ns bamf.movies.rama.client.depot-test
  (:require [bamf.movies.rama.client.depot :as depot]
            [bamf.movies.rama.common :as common]
            [clojure.test :refer [deftest is]]
            [com.rpl.rama]))

(deftest put!-wraps-movie-created-event
  (let [captured (atom nil)
        ack      {:status :stored :movie {:id 42}}]
    (with-redefs [com.rpl.rama/foreign-append! (fn [depot payload mode] (reset! captured [depot payload mode]) ack)]
      (is (= ack (depot/put! {:depot ::movie-depot :movie {:tmdb-id 1 :title "Dune"}})))
      (let [[depot payload mode] @captured]
        (is (= ::movie-depot depot))
        (is (= :ack mode))
        (is (= :movie.created (:event payload)))
        (is (= common/movie-event-version (:version payload)))
        (is (= 1 (:tmdb-id (:payload payload))))
        (is (= "Dune" (:title (:payload payload))))))))

(deftest put!-includes-metadata-fields
  (let [captured (atom nil)
        movie    {:tmdb-id 1 :title "Dune" :genres ["SciFi"] :runtime 120 :status "released"}]
    (with-redefs [com.rpl.rama/foreign-append!
                  (fn [depot payload mode] (reset! captured payload) {:status :stored :movie {:id 42}})]
      (depot/put! {:depot ::movie-depot :movie movie})
      (is (= ["SciFi"] (get-in @captured [:payload :genres])))
      (is (= 120 (get-in @captured [:payload :runtime])))
      (is (= "released" (get-in @captured [:payload :status]))))))

(deftest put!-defaults-when-missing-ack
  (with-redefs [com.rpl.rama/foreign-append! (fn [& _] nil)]
    (is (= {:status :stored :movie {}} (depot/put! {:depot ::movie-depot :movie {:tmdb-id 2}})))))

(deftest update!-wraps-movie-updated-event
  (let [captured (atom nil)
        ack      {:status :updated :movie {:id 77}}]
    (with-redefs [com.rpl.rama/foreign-append! (fn [depot payload mode] (reset! captured [depot payload mode]) ack)]
      (is (= ack (depot/update! {:depot ::movie-depot :movie {:id 77 :tmdb-id 1 :title "Dune"}})))
      (let [[depot payload mode] @captured]
        (is (= ::movie-depot depot))
        (is (= :ack mode))
        (is (= :movie.updated (:event payload)))
        (is (= common/movie-event-version (:version payload)))
        (is (= 77 (:id (:payload payload))))
        (is (= 1 (:tmdb-id (:payload payload))))))))

(deftest update!-defaults-when-missing-ack
  (with-redefs [com.rpl.rama/foreign-append! (fn [& _] nil)]
    (is (= {:status :updated :movie {}} (depot/update! {:depot ::movie-depot :movie {:id 5}})))))
