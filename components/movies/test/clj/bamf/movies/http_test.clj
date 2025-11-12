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

(defn- with-update-result
  [result f]
  (let [invocation (atom nil)
        env        {:movie-depot ::movie-depot}]
    (with-redefs [persistence/update! (fn [env* payload] (reset! invocation {:env env* :payload payload}) result)]
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

(deftest update-movie-translates-updated-response
  (with-update-result {:status :updated :movie {:id 9 :monitored false}}
                      (fn [invocation env]
                        (is (= {:status 200 :body {:data {:id 9 :monitored false}}}
                               (http/update-movie
                                {:body-params {:monitored false} :path-params {:id 9} :movies/env env})))
                        (is (= {:env env :payload {:monitored false :id 9}} @invocation)))))

(deftest update-movie-translates-not-found
  (with-update-result {:status :not-found :movie-id 44}
                      (fn [_ env]
                        (is (= {:status 404 :body {:errors ["Movie 44 not found"]}}
                               (http/update-movie {:body {:monitored true} :path-params {:id 44} :movies/env env}))))))

(deftest update-movie-translates-duplicate-response
  (with-update-result {:status :duplicate :reason "duplicate" :field :tmdbId :existing-id 42}
                      (fn [_ env]
                        (is (= {:status 409 :body {:error "duplicate" :field :tmdbId :existing-id 42}}
                               (http/update-movie
                                {:body {:movieMetadataId 1} :path-params {:id 1} :movies/env env}))))))

(deftest update-movie-translates-invalid-response
  (with-update-result {:status :invalid :errors ["bad"]}
                      (fn [_ env]
                        (is (= {:status 422 :body {:errors ["bad"]}}
                               (http/update-movie {:body {:monitored true} :path-params {:id 3} :movies/env env}))))))

(deftest update-movie-translates-error-response
  (with-update-result {:status :error :errors ["boom"]}
                      (fn [_ env]
                        (is (= {:status 500 :body {:errors ["boom"]}}
                               (http/update-movie
                                {:body-params {:monitored true} :path-params {:id 5} :movies/env env}))))))

(deftest update-movie-handles-unexpected-status
  (with-update-result {:status :unknown}
                      (fn [_ env]
                        (let [response (http/update-movie
                                        {:body {:monitored true} :path-params {:id 5} :movies/env env})]
                          (is (= 500 (:status response)))
                          (is (seq (get-in response [:body :errors])))))))
