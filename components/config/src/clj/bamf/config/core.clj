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
  (let [file (str "config"
                  (when-not (= :production environment)
                    (str "-" (name environment)))
                  ".edn")]
    (t/log! {:level :info} (format "loading config '%s'." file))
    (t/log! {:level :info} (format "classpath '%s'." (java.lang.System/getProperty "java.class.path")))
    (->> file
         io/resource
         read-config
         apply-defaults
         validate)))
