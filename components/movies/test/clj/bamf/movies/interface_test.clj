(ns bamf.movies.interface-test
  (:require [clojure.test :refer [deftest is]]
            [bamf.movies.interface :as movies]
            [bamf.movies.persistence :as persistence]))

(deftest save-movie!-delegates-to-persistence-with-env
  (let [env      {:movie-depot ::movie-depot}
        movie    {:title "TRON"}
        captured (atom nil)]
    (with-redefs [persistence/save! (fn [env* movie*] (reset! captured [env* movie*]) ::result)]
      (is (= ::result (movies/save-movie! env movie)))
      (is (= [env movie] @captured)))))
