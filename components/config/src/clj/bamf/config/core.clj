(ns bamf.config.core
  (:require [aero.core :refer [read-config]]
            [bamf.config.spec :as spec]
            [clojure.java.io :as io]
            ;; [taoensso.telemere :as t]
  ))

(defn validate
  ([config] (validate (spec/get-spec) config))
  ([spec config]
   ;; (t/log! {:level :info}
   ;;         (format "validating '%s' to make sure it's good!" config))
   (spec/validate spec config)))

(defn load-config
  [environment]
  (let [file (str "config" (when-not (= :production environment) (str "-" (name environment))) ".edn")]
    ;; (t/log! {:level :info} (format "loading config '%s'." file))
    (->> file
         io/resource
         read-config
         spec/apply-defaults
         validate)))
