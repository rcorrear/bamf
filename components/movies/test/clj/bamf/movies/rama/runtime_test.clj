(ns bamf.movies.rama.runtime-test
  (:require [bamf.movies.rama.common :as common]
            [bamf.movies.rama.module.core :as module]
            [bamf.movies.rama.runtime :as runtime]
            [clojure.test :refer [deftest is testing]]
            [com.rpl.rama :as rama]
            [com.rpl.rama.test :as rt]
            [taoensso.telemere :as t]))

(deftest start!-uses-default-config-and-returns-handles
  (let [calls (atom [])
        ipc   (Object.)
        depot (Object.)]
    (with-redefs [rt/create-ipc      (fn [] (swap! calls conj [:create-ipc]) ipc)
                  rt/launch-module!  (fn [arg-ipc module launch-config]
                                       (swap! calls conj [:launch arg-ipc module launch-config])
                                       :launched)
                  rama/foreign-depot (fn [arg-ipc module-name depot-name]
                                       (swap! calls conj [:foreign arg-ipc module-name depot-name])
                                       depot)]
      (is (= {:ipc ipc :movie-depot depot} (runtime/start!)))
      (is (= [[:create-ipc] [:launch ipc module/MovieModule {:tasks 4 :threads 2}]
              [:foreign ipc common/module-name common/movie-depot-name]]
             @calls)))))

(deftest start!-uses-custom-launch-config
  (let [captured (atom nil)]
    (with-redefs [rt/create-ipc      (fn [] :ipc)
                  rt/launch-module!  (fn [_ _ cfg] (reset! captured cfg) :launched)
                  rama/foreign-depot (fn [_ _ _] :depot)]
      (is (= {:ipc :ipc :movie-depot :depot} (runtime/start! {:launch-config {:tasks 1 :threads 1}})))
      (is (= {:tasks 1 :threads 1} @captured)))))

(deftest start!-closes-ipc-on-launch-failure
  (let [ipc    (Object.)
        closed (atom nil)
        thrown (ex-info "boom" {})]
    (with-redefs [rt/create-ipc      (fn [] ipc)
                  rt/launch-module!  (fn [_ _ _] (throw thrown))
                  runtime/close-ipc! (fn [ipc-arg] (reset! closed ipc-arg))
                  t/log!             (fn [& _])]
      (is (thrown? clojure.lang.ExceptionInfo (runtime/start!)))
      (is (= ipc @closed)))))

(deftest stop!-closes-ipc-when-present
  (let [closed (atom [])]
    (with-redefs [runtime/close-ipc! (fn [ipc] (swap! closed conj ipc))
                  t/log!             (fn [& _])]
      (runtime/stop! {:ipc :ipc})
      (runtime/stop! {:ipc nil}))
    (is (= [:ipc] @closed))))
