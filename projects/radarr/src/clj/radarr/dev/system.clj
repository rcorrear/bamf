(ns radarr.dev.system
  "Radarr-specific Donut system wiring that layers static REST API routes on top of the shared base."
  (:require [bamf.config.interface :as config]
            [bamf.movies.interface :as movies]
            [bamf.rest-api.api :as rest-api]
            [donut.system :as ds]
            [radarr.rest-api.static-routes :as static]))

(defn http-components
  ([] (http-components {}))
  ([extra]
   (merge {:components/movies {:component/http-api #'movies/get-http-api}
           :components/radarr {:component/http-api #'static/get-http-api}}
          extra)))

(defmethod ds/named-system ::ds/repl [_] (ds/system :local))

(defmethod ds/named-system :base
  [_]
  {::ds/defs {:config        {}
              :runtime-state {:rest-api/server #::ds{:start  (fn [{:keys [::ds/config]}] (rest-api/start config))
                                                     :stop   (fn [{:keys [::ds/instance]}] (rest-api/stop instance))
                                                     :config (ds/ref [:config])}}}})

(defmethod ds/named-system :local
  [_]
  (ds/system :base
             {[:config] (-> (config/load-config :local)
                            (assoc :http-components (http-components) :http/runtime-state {}))}))

(defmethod ds/named-system :test
  [_]
  (ds/system :base
             {[:config]                         (-> (config/load-config :test)
                                                    (assoc :http-components (http-components) :http/runtime-state {}))
              [:runtime-state :rest-api/server] :disabled}))
