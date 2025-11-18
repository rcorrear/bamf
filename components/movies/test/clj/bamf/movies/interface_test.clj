(ns bamf.movies.interface-test
  (:require [clojure.test :refer [deftest is]]
            [bamf.movies.interface :as movies]
            [bamf.movies.inspection :as inspection]
            [bamf.movies.persistence :as persistence]))

(deftest save-movie!-delegates-to-persistence-with-env
  (let [env      {:movie-depot ::movie-depot}
        movie    {:title "TRON"}
        captured (atom nil)]
    (with-redefs [persistence/save! (fn [env* movie*] (reset! captured [env* movie*]) ::result)]
      (is (= ::result (movies/save-movie! env movie)))
      (is (= [env movie] @captured)))))

(deftest update-movie!-delegates-to-persistence-with-env
  (let [env      {:movie-depot ::movie-depot}
        movie    {:id 9 :monitored false}
        captured (atom nil)]
    (with-redefs [persistence/update! (fn [env* movie*] (reset! captured [env* movie*]) ::updated)]
      (is (= ::updated (movies/update-movie! env movie)))
      (is (= [env movie] @captured)))))

(deftest list-movies-delegates-to-inspection
  (let [env      {:ipc ::ipc}
        query    {:monitored true}
        captured (atom nil)]
    (with-redefs [inspection/list-movies (fn [env* query*] (reset! captured [env* query*]) ::listed)]
      (is (= ::listed (movies/list-movies env query)))
      (is (= [env query] @captured)))))

(deftest get-movie-delegates-to-inspection
  (let [env      {:ipc ::ipc}
        captured (atom nil)]
    (with-redefs [inspection/get-movie (fn [env* id] (reset! captured [env* id]) ::fetched)]
      (is (= ::fetched (movies/get-movie env 9)))
      (is (= [env 9] @captured)))))
