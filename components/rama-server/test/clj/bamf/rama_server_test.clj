(ns bamf.rama-server-test
  (:require [bamf.rama-server]
            [clojure.test :refer [deftest is]]))

(deftest namespace-loads (is (find-ns 'bamf.rama-server)))
