(ns bamf.rest-api.routes-test
  (:require [bamf.rest-api.test-support :as ts]
            [clojure.test :refer [deftest is testing]]))

(deftest route-declarations-enforce-json-shape
  (testing "Route declaration validation rejects missing JSON metadata"
    (let [incomplete-route [(first ts/movie-list-route)
                            (-> ts/movie-list-route
                                second
                                (dissoc :name)
                                (update :get dissoc :produces))]
          validate!        (try (requiring-resolve 'bamf.rest-api.routes.declaration/validate!)
                                (catch Throwable _ nil))]
      (is (some? validate!) "Implement validate! in bases/rest-api/src/clj/bamf/rest_api/routes/declaration.clj")
      (when validate!
        (let [thrown (try (validate! [incomplete-route]) nil (catch clojure.lang.ExceptionInfo ex ex))]
          (is (instance? clojure.lang.ExceptionInfo thrown)
              "Validation should throw ExceptionInfo when required JSON metadata is missing")
          (is (= :bamf.rest-api.routes/invalid-route (:type (ex-data thrown)))
              "Exception data should tag invalid route type")
          (is (= #{:name :produces} (:missing (ex-data thrown))) "Validation should report the specific missing keys"))
        (is (= [ts/movie-list-route ts/movie-create-route] (validate! [ts/movie-list-route ts/movie-create-route]))
            "validate! should return the original route vector when metadata is complete")))))
