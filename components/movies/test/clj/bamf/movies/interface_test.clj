(ns bamf.movies.interface-test
  (:require [clojure.test :refer [deftest is]]
            [com.rpl.rama :as rama]
            [bamf.movies.interface :as movies]
            [bamf.movies.persistence :as persistence]))

(deftest save-movie!-delegates-to-persistence-with-env
  (let [env      {:movie-depot ::movie-depot}
        movie    {:title "TRON"}
        captured (atom nil)]
    (with-redefs [persistence/save! (fn [env* movie*] (reset! captured [env* movie*]) ::result)]
      (is (= ::result (movies/save-movie! env movie)))
      (is (= [env movie] @captured)))))

(deftest save-movie!-uses-runtime-ipc
  (let [movie          {:title "Dune"}
        captured-env   (atom nil)
        captured-depot (atom nil)]
    (with-redefs [rama/foreign-depot (fn [ipc module depot] (reset! captured-depot [ipc module depot]) ::depot)
                  persistence/save!  (fn [env* movie*] (reset! captured-env [env* movie*]) ::result-with-ipc-env)]
      (movies/start ::ipc)
      (try (is (= ::result-with-ipc-env (movies/save-movie! movie)))
           (is (= [::ipc "MovieModule" "*movie-saves-depot"] @captured-depot))
           (is (= [{:movie-depot ::depot} movie] @captured-env))
           (finally (movies/stop))))))

(deftest save-movie!-without-start-throws
  (movies/stop)
  (is (thrown? IllegalStateException (movies/save-movie! {:title "Nope"}))))
