(ns bamf.system.donut-test
  (:require [bamf.dev.system]
            [bamf.dev.system.movies :as system.movies]
            [clojure.test :refer [deftest is]]
            [donut.system :as ds]))

(deftest bamf-base-system-exposes-explicit-rama-dependency
  (let [system (ds/named-system :base)]
    (is (some? (get-in system [::ds/defs :runtime-state :rama])))
    (is (= (ds/ref [:runtime-state :rama]) (get-in system [::ds/defs :runtime-state :movies/env :donut.system/rama])))
    (is (= (ds/ref [:runtime-state :movies/env])
           (get-in system [::ds/defs :runtime-state :rest-api/server :movies/env])))))

(deftest movies-node-starts-from-donut-namespaced-rama-dependency
  (let [start-fn (get-in (ds/named-system :base) [::ds/defs :runtime-state :movies/env :donut.system/start])
        captured (atom nil)]
    (with-redefs [system.movies/start! (fn [opts] (reset! captured opts) ::started)]
      (is (= ::started (start-fn {::ds/rama ::rama})))
      (is (= {:rama ::rama} @captured)))))
