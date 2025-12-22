(ns bamf.movies.http-test
  (:require [bamf.movies.http :as http]
            [bamf.movies.inspection :as inspection]
            [bamf.movies.persistence :as persistence]
            [clojure.test :refer [deftest is]]))

(deftest list-movies-pulls-from-inspection
  (let [env {:movies :env}]
    (with-redefs [inspection/list-movies
                  (fn [env* query] (is (= env env*)) (is (= {} query)) {:status :ok :movies [{:id 1}]})]
      (let [response (http/list-movies {:movies/env env :parameters {:query {}}})]
        (is (= 200 (:status response)))
        (is (= [1] (map :id (:body response))))
        (is (every? #(zero? (:size-on-disk %)) (:body response)))))))

(deftest list-movies-passes-tmdb-filter
  (let [env {:movies :env}]
    (with-redefs [inspection/list-movies
                  (fn [env* query] (is (= env env*)) (is (= {:tmdb-id 42} query)) {:status :ok :movies [{:id 2}]})]
      (let [response (http/list-movies {:movies/env env :parameters {:query {:tmdb-id 42}}})]
        (is (= 200 (:status response)))
        (is (= [2] (map :id (:body response))))))))

(deftest get-movie-translates-found
  (let [env {:movies :env}]
    (with-redefs [inspection/get-movie (fn [env* id]
                                         (is (= env env*))
                                         (is (= 9 id))
                                         {:status :ok :movie {:id 9 :tmdb-id 1 :added "2024-01-01T00:00:00Z"}})]
      (let [response (http/get-movie {:movies/env env :path-params {:id 9}})]
        (is (= 200 (:status response)))
        (is (= 9 (get-in response [:body :id])))
        (is (nil? (get-in response [:body :last-search-time])))
        (is (nil? (get-in response [:body :target-system])))))))

(deftest get-movie-translates-not-found
  (let [env {:movies :env}]
    (with-redefs [inspection/get-movie (fn [_ _] {:status :not-found :movie-id 22})]
      (is (= {:status 404 :body {:errors ["Movie 22 not found"]}}
             (http/get-movie {:movies/env env :path-params {:id 22}}))))))

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
  (with-save-result {:status :stored :movie {:id 1 :tmdb-id 77 :added "2024-01-01T00:00:00Z" :target-system "radarr"}}
                    (fn [invocation env]
                      (let [response (http/create-movie {:body-params {:title "Foo"} :movies/env env})]
                        (is (= 201 (:status response)))
                        (is (= {:id 1 :movie-metadata-id 77 :size-on-disk 0}
                               (select-keys (:body response) [:id :movie-metadata-id :size-on-disk])))
                        (is (not (contains? (:body response) :last-search-time)))
                        (is (nil? (get-in response [:body :target-system]))))
                      (is (= {:env env :payload {:title "Foo"}} @invocation)))))

(deftest create-movie-translates-duplicate-response
  (with-save-result {:status :duplicate :reason "duplicate" :field :tmdb-id :existing-id 42}
                    (fn [_ env]
                      (is (= {:status 409 :body {:error "duplicate" :field "tmdbId" :existing-id 42}}
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
  (with-update-result
   {:status :updated :movie {:id 9 :monitored false :last-search-time "2024-01-02T00:00:00Z" :size-on-disk nil}}
   (fn [invocation env]
     (let [response
           (http/update-movie
            {:body-params {:monitored false} :path-params {:id 9} :movies/env env :query-params {:move-files true}})]
       (is (= 200 (:status response)))
       (is (= {:id 9 :monitored false :last-search-time "2024-01-02T00:00:00Z" :size-on-disk 0}
              (select-keys (:body response) [:id :monitored :last-search-time :size-on-disk]))))
     (is (= {:env env :payload {:monitored false :id 9 :move-files true}} @invocation)))))

(deftest update-movie-translates-not-found
  (with-update-result
   {:status :not-found :movie-id 44}
   (fn [_ env]
     (is (= {:status 404 :body {:errors ["Movie 44 not found"]}}
            (http/update-movie
             {:body {:monitored true} :path-params {:id 44} :movies/env env :query-params {:move-files false}}))))))

(deftest update-movie-translates-duplicate-response
  (with-update-result
   {:status :duplicate :reason "duplicate" :field :tmdb-id :existing-id 42}
   (fn [_ env]
     (is (= {:status 409 :body {:error "duplicate" :field "tmdbId" :existing-id 42}}
            (http/update-movie
             {:body {:movie-metadata-id 1} :path-params {:id 1} :movies/env env :query-params {:move-files true}}))))))

(deftest update-movie-translates-invalid-response
  (with-update-result
   {:status :invalid :errors ["bad"]}
   (fn [_ env]
     (is (= {:status 422 :body {:errors ["bad"]}}
            (http/update-movie
             {:body {:monitored true} :path-params {:id 3} :movies/env env :query-params {:move-files false}}))))))

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
