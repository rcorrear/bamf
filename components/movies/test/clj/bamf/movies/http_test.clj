(ns bamf.movies.http-test
  (:require [bamf.movies.http :as http]
            [bamf.movies.persistence :as persistence]
            [bamf.movies.runtime :as runtime]
            [clojure.test :refer [deftest is testing]]))

(deftest list-movies-returns-empty-collection
  (is (= {:status 200 :body {:data []}} (http/list-movies {:query-params {:term "anything"}}))))

(defn- with-save-result
  [result f]
  (let [invocation (atom nil)]
    (with-redefs [runtime/env       (fn [] :stub-env)
                  persistence/save! (fn [env payload] (reset! invocation {:env env :payload payload}) result)]
      (f invocation))))

(deftest create-movie-translates-stored-response
  (with-save-result {:status :stored :movie {:id 1}}
                    (fn [invocation]
                      (is (= {:status 201 :body {:data {:id 1}}} (http/create-movie {:body-params {:title "Foo"}})))
                      (is (= {:env :stub-env :payload {:title "Foo"}} @invocation)))))

(deftest create-movie-translates-duplicate-response
  (with-save-result {:status :duplicate :reason "duplicate" :field :tmdbId :existing-id 42}
                    (fn [_]
                      (is (= {:status 409 :body {:error "duplicate" :field :tmdbId :existing-id 42}}
                             (http/create-movie {:body {:title "Foo"}}))))))

(deftest create-movie-translates-invalid-response
  (with-save-result
   {:status :invalid :errors ["bad"]}
   (fn [_] (is (= {:status 422 :body {:errors ["bad"]}} (http/create-movie {:body-params {:title "Foo"}}))))))

(deftest create-movie-translates-error-response
  (with-save-result
   {:status :error :errors ["boom"]}
   (fn [_] (is (= {:status 500 :body {:errors ["boom"]}} (http/create-movie {:body-params {:title "Foo"}}))))))

(deftest create-movie-handles-unexpected-status
  (with-save-result {:status :weird :errors []}
                    (fn [_]
                      (let [response (http/create-movie {:body-params {:title "Foo"}})]
                        (is (= 500 (:status response)))
                        (is (seq (get-in response [:body :errors])))))))
