(ns bamf.config.core
  (:require [aero.core :refer [read-config]]
            [bamf.config.spec :as spec]
            [clojure.java.io :as io]
            [taoensso.telemere :as t]))

(defn apply-defaults
  [config]
  (spec/validate config))

(defn validate
  [config]
  (t/log! {:level :info}
          (format "validating '%s' to make sure it's good!" config))
  (spec/validate config))

(defn load-config
  [environment]
  (let [file (str "config/config"
                  (when-not (= :production environment)
                    (str "-" (name environment)))
                  ".edn")]
    (t/log! {:level :info} (format "loading config '%s'." file))
    (->> file
         io/resource
         read-config
         apply-defaults
         validate)))
