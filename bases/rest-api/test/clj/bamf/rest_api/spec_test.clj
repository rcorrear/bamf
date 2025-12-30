(ns bamf.rest-api.spec-test
  (:require [bamf.rest-api.spec :as raspec]
            [clojure.test :refer [deftest is]]))

(deftest get-spec-describes-config-shape
  (let [spec (raspec/get-spec)]
    (is (= [:map {:closed false} [:aleph [:map [:port :int]]]
            [:http-components {:optional true}
             [:map-of keyword?
              [:map {:closed false} [:component/http-api ifn?] [:component/context {:optional true} map?]
               [:component/context-fn {:optional true} ifn?]]]] [:http/runtime-state {:optional true} map?]]
           spec))))
