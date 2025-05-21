(ns bamf.dev.core
  {:author "Ricardo Correa"}
  (:require [bamf.config.interface :as config]
            [bamf.rest-api.core :as rest-api]
            [donut.system :as ds]))

(set! *warn-on-reflection* true)

(def ^:private base-system
  {::ds/defs
   {:config  {},
    :runtime-state   {:rest-api #::ds{:start (fn [{:keys [::ds/config]}]
                                               (rest-api/start config)),
                                      :stop (fn [{:keys [::ds/instance]}]
                                              (rest-api/stop instance)),
                                      :config (ds/ref
                                               [:config])}}}})

(defmethod ds/named-system :base [_] base-system)

(defmethod ds/named-system :local
  [_]
  (ds/system :base
             {[:config] (config/load-config :local)}))

(defmethod ds/named-system :test
  [_]
  (ds/system :base
             {[:config] (config/load-config :test),
              [:runtime-state :rest-api] ::disabled}))
