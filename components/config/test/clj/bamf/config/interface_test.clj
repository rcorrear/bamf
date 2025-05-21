(ns bamf.config.interface-test
  (:require [bamf.config.interface :as config]
            [clojure.test :refer [deftest is]]))

(deftest load-config
  (is (= {:aleph {:port 9090} :app-name "bamf" :environment :test} (config/load-config :test))))
