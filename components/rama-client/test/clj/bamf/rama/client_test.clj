(ns bamf.rama.client-test
  (:require [bamf.rama.client :as client]
            [clojure.test :refer [deftest is]])
  (:import (java.io Closeable)))

(deftest local-and-cluster-runtime-wrap-underlying-handles
  (let [ipc     (Object.)
        manager (Object.)
        local   (client/local-runtime ipc)
        remote  (client/cluster-runtime manager)]
    (is (= :ipc (client/kind local)))
    (is (= ipc (client/handle local)))
    (is (= :cluster (client/kind remote)))
    (is (= manager (client/handle remote)))))

(deftest handle-supports-plain-maps
  (is (= :handle (client/handle {:handle :handle})))
  (is (= :cluster (client/kind {:kind :cluster :handle :h}))))

(deftest close!-closes-closeable-handles
  (let [closed  (atom [])
        owned   (reify
                 Closeable
                   (close [_] (swap! closed conj :owned)))
        unowned (reify
                 Closeable
                   (close [_] (swap! closed conj :unowned)))]
    (client/close! (client/local-runtime owned))
    (client/close! (client/local-runtime unowned))
    (client/close! {:handle owned})
    (is (= [:owned :unowned :owned] @closed))))

(deftest foreign-helpers-unwrap-runtime-handle
  (let [captured (atom [])]
    (with-redefs [com.rpl.rama/foreign-depot        (fn [handle module-name depot-name]
                                                      (swap! captured conj [:depot handle module-name depot-name])
                                                      :depot)
                  com.rpl.rama/foreign-pstate       (fn [handle module-name pstate-name]
                                                      (swap! captured conj [:pstate handle module-name pstate-name])
                                                      :pstate)
                  com.rpl.rama/foreign-query        (fn [handle module-name query-name]
                                                      (swap! captured conj [:query handle module-name query-name])
                                                      :query)
                  com.rpl.rama/foreign-invoke-query (fn [query-client]
                                                      (swap! captured conj [:invoke query-client])
                                                      :invoked)]
      (let [runtime (client/local-runtime :ipc)]
        (is (= :depot (client/foreign-depot runtime "module" "*depot")))
        (is (= :pstate (client/foreign-pstate runtime "module" "$$state")))
        (is (= :query (client/foreign-query runtime "module" "query-all")))
        (is (= :invoked (client/foreign-invoke-query :query-client))))
      (is (= [[:depot :ipc "module" "*depot"] [:pstate :ipc "module" "$$state"] [:query :ipc "module" "query-all"]
              [:invoke :query-client]]
             @captured)))))
