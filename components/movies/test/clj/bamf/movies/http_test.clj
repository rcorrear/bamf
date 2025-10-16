(ns bamf.movies.http-test
  (:require [bamf.movies.http :as http]
            [bamf.movies.persistence :as persistence]
            [clojure.test :refer [deftest is]]))

(deftest list-movies-returns-empty-collection
  (is (= {:status 200 :body {:data []}} (http/list-movies {:query-params {:term "anything"}}))))

(defn- with-save-result
  [result f]
  (let [invocation (atom nil)
        env        {:movie-depot ::movie-depot}]
    (with-redefs [persistence/save! (fn [env* payload] (reset! invocation {:env env* :payload payload}) result)]
      (f invocation env))))

(deftest create-movie-translates-stored-response
  (with-save-result {:status :stored :movie {:id 1}}
                    (fn [invocation env]
                      (is (= {:status 201 :body {:data {:id 1}}}
                             (http/create-movie {:body-params {:title "Foo"} :movies/env env})))
                      (is (= {:env env :payload {:title "Foo"}} @invocation)))))

(deftest create-movie-translates-duplicate-response
  (with-save-result {:status :duplicate :reason "duplicate" :field :tmdbId :existing-id 42}
                    (fn [_ env]
                      (is (= {:status 409 :body {:error "duplicate" :field :tmdbId :existing-id 42}}
                             (http/create-movie {:body {:title "Foo"} :movies/env env}))))))

(deftest create-movie-translates-invalid-response
  (with-save-result {:status :invalid :errors ["bad"]}
                    (fn [_ env]
                      (is (= {:status 422 :body {:errors ["bad"]}}
                             (http/create-movie {:body-params {:title "Foo"} :movies/env env}))))))

(deftest create-movie-translates-error-response
  (with-save-result {:status :error :errors ["boom"]}
                    (fn [_ env]
                      (is (= {:status 500 :body {:errors ["boom"]}}
                             (http/create-movie {:body-params {:title "Foo"} :movies/env env}))))))

(deftest create-movie-handles-unexpected-status
  (with-save-result {:status :weird :errors []}
                    (fn [_ env]
                      (let [response (http/create-movie {:body-params {:title "Foo"} :movies/env env})]
                        (is (= 500 (:status response)))
                        (is (seq (get-in response [:body :errors])))))))
