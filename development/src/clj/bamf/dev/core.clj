(ns bamf.dev.core
  {:author "Ricardo Correa"}
  (:require [bamf.config.interface :as config]
            [bamf.movies.interface :as movies]
            [bamf.rest-api.api :as rest-api]
            [donut.system :as ds]
            [com.rpl.rama :as r]
            [com.rpl.rama.test :as rtest]))

(set! *warn-on-reflection* true)

(def ^:private http-components {:components/movies {:component/http-api #'movies/get-http-api}})

(def ^:private base-system
  {::ds/defs {:config        {}
              :runtime-state {:rest-api/server #::ds{:start  (fn [{:keys [::ds/config]}] (rest-api/start config))
                                                     :stop   (fn [{:keys [::ds/instance]}] (rest-api/stop instance))
                                                     :config (ds/ref [:config])}}}})

(defmethod ds/named-system :base [_] base-system)

(defmethod ds/named-system :local
  [_]
  (ds/system
   :base
   {[:config]                        (-> (config/load-config :local)
                                         (assoc :http-components http-components :http/runtime-state {}))
    [:runtime-state :movies/service] #::ds{:start  (fn [{{:keys [rama-ipc]} ::ds/config}] (movies/start rama-ipc))
                                           :stop   (fn [{{:keys [rama-ipc]} ::ds/config}] (movies/stop rama-ipc))
                                           :config {:rama-ipc (ds/ref [:runtime-state :rama-ipc])}}
    [:runtime-state :rama-ipc]       (rtest/create-ipc)}))

(defmethod ds/named-system :test
  [_]
  (ds/system
   :base
   {[:config]                         (-> (config/load-config :test)
                                          (assoc :http-components http-components :http/runtime-state {}))
    [:runtime-state :movies/service]  #::ds{:start  (fn [{{:keys [rama-ipc]} ::ds/config}] (movies/start rama-ipc))
                                            :stop   (fn [{{:keys [rama-ipc]} ::ds/config}] (movies/stop rama-ipc))
                                            :config {:rama-ipc (ds/ref [:runtime-state :rama-ipc])}}
    [:runtime-state :rama-ipc]        (rtest/create-ipc)
    [:runtime-state :rest-api/server] :disabled}))
