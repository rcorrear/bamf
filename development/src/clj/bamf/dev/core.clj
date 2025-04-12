(ns bamf.dev.core
  {:author "Ricardo Correa"}
  (:require [bamf.config.interface :as config]
            [bamf.rest-api.core :as rest-api]
            [donut.system :as ds]
            [taoensso.telemere :as t]))

(set! *warn-on-reflection* true)

(def ^:private base-system
  {::ds/defs
   {:env        {},
    :app-config {:rest-api #::ds{:start (fn [{:keys [::ds/config]}]
                                          (t/log! {:level :info}
                                                  (format
                                                   "starting %s :router"
                                                   (get-in config
                                                           [:runtime-config
                                                            :environment])))
                                          (rest-api/start config)),
                                 :stop (fn [{:keys [::ds/instance]}]
                                         (rest-api/stop instance)),
                                 :config {:runtime-config
                                          (ds/ref
                                           [:env
                                            :runtime-config])}}}}})

(defmethod ds/named-system :base [_] base-system)

(defmethod ds/named-system :local
  [_]
  (ds/system :base {[:env] (config/load-config :local)}))

(defmethod ds/named-system :test
  [_]
  (ds/system :base
             {[:env] (config/load-config :test),
              [:app-config :rest-api] ::disabled,
              [:app-config :runtime-config] (ds/ref [:env :runtime-config])}))
