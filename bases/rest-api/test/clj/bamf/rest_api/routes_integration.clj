(ns bamf.rest-api.routes-integration
  (:require [bamf.rest-api.test-support :as ts]
            [clojure.test :refer [deftest is testing]]
            [reitit.ring :as ring]))

(defn- resolve-aggregate [] (try (requiring-resolve 'bamf.rest-api.routes/aggregate) (catch Throwable _ nil)))

(deftest aggregates-component-routes
  (testing "Aggregates component routes into a single catalog"
    (let [aggregate (resolve-aggregate)
          call-log  (atom [])
          config    (ts/rest-api-config
                     (ts/stub-http-component :components/movies [ts/movie-list-route ts/movie-create-route] call-log))]
      (is (some? aggregate) "Implement aggregate in bases/rest-api/src/clj/bamf/rest_api/routes.clj")
      (when aggregate
        (let [catalog (aggregate {:http-components (:http-components config) :runtime-state {:env :test}})]
          (is (= [ts/movie-list-route ts/movie-create-route] (:routes catalog))
              "Catalog should contain the routes returned by component get-http-api")
          (is (= #{:components/movies} (:source-components catalog)) "Catalog should record contributing component ids")
          (is (= [{:component/id :components/movies :runtime-state {:env :test}}]
                 (map #(select-keys % [:component/id :runtime-state]) @call-log))
              "Aggregator should pass runtime context when invoking get-http-api"))))))

(deftest skips-components-without-contract
  (testing "Components without get-http-api are ignored"
    (let [aggregate (resolve-aggregate)
          config    {:http-components {:components/config {:component/id :components/config}}}]
      (is (some? aggregate) "Implement aggregate in bases/rest-api/src/clj/bamf/rest_api/routes.clj")
      (when aggregate
        (let [catalog (aggregate {:http-components (:http-components config) :runtime-state {}})]
          (is (empty? (:routes catalog)) "Missing get-http-api should yield no routes")
          (is (= #{} (:source-components catalog)) "No components recorded"))))))

(deftest duplicate-routes-rely-on-reitit
  (testing "Reitit router throws when duplicate routes are aggregated"
    (let [aggregate (resolve-aggregate)
          config    (ts/rest-api-config (ts/stub-http-component :components/movies [ts/movie-list-route])
                                        (ts/stub-http-component :components/alt [ts/movie-list-route]))]
      (is (some? aggregate) "Implement aggregate in bases/rest-api/src/clj/bamf/rest_api/routes.clj")
      (when aggregate
        (let [catalog (aggregate {:http-components (:http-components config) :runtime-state {}})
              ex      (try (ring/router (:routes catalog)) nil (catch Exception e e))]
          (is (instance? Exception ex) "Reitit should throw when duplicate path/name pairs are present"))))))
