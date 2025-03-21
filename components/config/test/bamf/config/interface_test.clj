(ns bamf.config.interface-test
  (:require [clojure.test :refer [deftest is]]
            [bamf.config.interface :as config]))

(deftest hello
  (is (= (config/hello "test") "Hello test!")))
