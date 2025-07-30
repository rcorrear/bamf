(ns bamf.api.interface-test
  (:require [bamf.api.interface :as api]
            [clojure.test :as test :refer [deftest is]]))

(deftest get-api-info (is (= {:current "v3" :deprecated []} (api/get-api-info {}))))
