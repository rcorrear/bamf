(ns bamf.movies.rama.client.pstate-test
  (:require [bamf.movies.rama.client.pstate :as pstate]
            [bamf.movies.rama.common :as common]
            [bamf.rama.interface :as rama]
            [clojure.test :refer [deftest is]]))

(deftest movie-lookups-use-shared-rama-client-boundary
  (let [calls (atom [])]
    (with-redefs [rama/foreign-pstate     (fn [runtime module-name pstate-name]
                                            (swap! calls conj [:pstate runtime module-name pstate-name])
                                            :pstate)
                  rama/foreign-select-one (fn
                                            ([path pstate]
                                             (swap! calls conj [:select path pstate nil])
                                             (case path
                                               [:keypath 99] {:id 99 :title "Movie99"}
                                               [:keypath 77] {:genres ["Mystery"]}
                                               42))
                                            ([path pstate opts]
                                             (swap! calls conj [:select path pstate opts])
                                             (case path
                                               [:keypath 99] {:id 99 :title "Movie99"}
                                               [:keypath 77] {:genres ["Mystery"]}
                                               42)))]
      (with-redefs [com.rpl.rama.path/keypath (fn [k] [:keypath k])]
        (is (= {:id 99 :title "Movie99"} (pstate/movie-by-id {:rama :runtime} 99)))
        (is (= {:genres ["Mystery"]} (pstate/metadata-by-movie-id {:rama :runtime} 77)))
        (is (= 42 (pstate/movie-id-by-tmdb-id {:rama :runtime} 438631))))
      (is (= [[:pstate :runtime common/module-name common/movie-by-id-pstate-name] [:select [:keypath 99] :pstate nil]
              [:pstate :runtime common/module-name common/metadata-by-movie-id-pstate-name]
              [:select [:keypath 77] :pstate nil]
              [:pstate :runtime common/module-name common/movies-id-by-tmdb-id-pstate-name]
              [:select [:keypath 438631] :pstate nil]]
             @calls)))))

(deftest pstate-lookups-require-rama-runtime
  (is (thrown-with-msg? IllegalStateException #"missing :rama runtime" (pstate/movie-by-id {} 1))))
