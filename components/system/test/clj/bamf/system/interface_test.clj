(ns bamf.system.interface-test
  (:require [clojure.test :refer [deftest is testing]]
            [bamf.system.interface :as system]
            [donut.system :as ds]
            [donut.system.repl :as dsr]
            [donut.system.repl.state :as state])
  (:import (clojure.lang ExceptionInfo)))

(deftest ensure-ns-loaded-existing
  (testing "returns nil when namespace is already loaded" (is (nil? (system/ensure-ns-loaded 'clojure.string)))))

(deftest ensure-ns-loaded-missing
  (testing "throws informative exception when namespace cannot be found"
    (is
     (thrown-with-msg? ExceptionInfo #"is unavailable" (system/ensure-ns-loaded 'bamf.this-namespace-does-not-exist)))))

(deftest start-go-success
  (testing "shared :go handler returns ready keyword and updates runtime"
    (let [started-env   (atom nil)
          described-env (atom nil)]
      (with-redefs [dsr/start          (fn [env] (reset! started-env env) :ok)
                    ds/system          (fn [env] (reset! described-env env) {:env env})
                    ds/describe-system identity]
        (is (= :ready-to-rock-and-roll (system/start {:system :go :environment :local})))
        (is (= :local @started-env) "donut.system.repl/start receives the environment keyword")
        (is (= {:env :local} (system/status)) "status reflects the remembered runtime environment")
        (is (= :local @described-env) "donut.system/system invoked with environment captured from start")))))

(deftest start-go-failure
  (testing "shared :go handler rethrows ExceptionInfo and preserves runtime atom"
    (with-redefs [dsr/start          (fn [_] (throw (ex-info "boom" {:reason :fail})))
                  ds/system          (fn [env] {:env env})
                  ds/describe-system identity]
      (is (thrown? ExceptionInfo (system/start {:system :go :environment :local})))
      (is (= {:env :local} (system/status)) "runtime atom still tracks attempted environment after failure"))))

(deftest stop-delegates-to-donut-repl
  (testing "stop forwards to donut.system.repl/stop"
    (with-redefs [dsr/stop (constantly :stopped)] (is (= :stopped (system/stop))))))

(deftest restart-delegates-to-donut-repl
  (testing "restart forwards to donut.system.repl/restart"
    (with-redefs [dsr/restart (constantly :restarted)] (is (= :restarted (system/restart))))))

(deftest runtime-state-and-config
  (testing "runtime-state and config read from donut.system state"
    (with-redefs [state/system {::ds/instances {:runtime-state {:foo :bar} :config {:baz :qux}}}]
      (is (= {:foo :bar} (system/runtime-state)))
      (is (= {:baz :qux} (system/config))))))
