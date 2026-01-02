(ns bamf.casing-test
  (:require [bamf.casing :as casing]
            [clojure.test :refer [deftest is testing]]))

(deftest ->kebab-keys-recursively-normalizes
  (let [input    {:MovieId 1 "deepValue" {"InnerValue" 2} :vector [{:pathValue "X"}]}
        expected {:movie-id 1 "deep-value" {"inner-value" 2} :vector [{:path-value "X"}]}]
    (is (= expected (casing/->kebab-keys input)))))

(deftest ->camel-keys-recursively-normalizes
  (let [input    {:movie-id 1 "deep-value" {"inner-value" 2} :vector [{:path-value "X"}]}
        expected {:movieId 1 "deepValue" {"innerValue" 2} :vector [{:pathValue "X"}]}]
    (is (= expected (casing/->camel-keys input)))))

(deftest wrap->kebab->camel-normalizes-request-and-response
  (let [captured (atom nil)
        handler  (fn [req]
                   (reset! captured req)
                   {:status 200 :body {:movie-id 42 :nested-response {:next-action "go"}}})
        wrapped  (casing/wrap->kebab->camel handler)
        response (wrapped {:params {"movieId" 1} :path-params {"movieId" "abc"}})]
    (testing "request keys are kebabed"
      (is (= {"movie-id" 1} (:params @captured)))
      (is (= {"movie-id" "abc"} (:path-params @captured))))
    (testing "response body is camelized" (is (= {:movieId 42 :nestedResponse {:nextAction "go"}} (:body response))))))
