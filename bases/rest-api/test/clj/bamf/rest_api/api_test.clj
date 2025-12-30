(ns bamf.rest-api.api-test
  (:require [bamf.rest-api.api :as api]
            [bamf.rest-api.core :as core]
            [clojure.test :refer [deftest is]]))

(deftest delegates-to-core-get-routes
  (let [calls (atom [])]
    (with-redefs [core/get-routes (fn
                                    ([] (swap! calls conj [:no-arg]) :no-arg)
                                    ([catalog] (swap! calls conj [:with catalog]) :with-arg))]
      (is (= :no-arg (api/get-routes)))
      (is (= :with-arg (api/get-routes {:routes []})))
      (is (= [[:no-arg] [:with {:routes []}]] @calls)))))

(deftest delegates-to-core-start-and-stop
  (let [calls (atom [])]
    (with-redefs [core/start (fn [cfg] (swap! calls conj [:start cfg]) :started)
                  core/stop  (fn [server] (swap! calls conj [:stop server]) :stopped)]
      (is (= :started (api/start {:aleph {:port 1}})))
      (is (= :stopped (api/stop :server)))
      (is (= [[:start {:aleph {:port 1}}] [:stop :server]] @calls)))))
