(ns bamf.dev.system
  {:author "Ricardo Correa"}
  (:require [bamf.config.interface :as config]
            [bamf.movies.interface :as movies]
            [bamf.rest-api.api :as rest-api]
            [donut.system :as ds]))

(set! *warn-on-reflection* true)

(defn http-components
  ([] (http-components {}))
  ([extra] (merge {:components/movies {:component/http-api #'movies/get-http-api}} extra)))

(defmethod ds/named-system ::ds/repl [_] (ds/system :local))

(defmethod ds/named-system :base
  [_]
  {::ds/defs {:config        {}
              :runtime-state {:movies/env      #::ds{:start (fn [_] (movies/start!))
                                                     :stop  (fn [deps] (movies/stop! (::ds/instance deps)))}
                              :rest-api/server #::ds{:start      (fn [deps]
                                                                   (let [system-config (::ds/config deps)
                                                                         movies-env    (:movies/env deps)
                                                                         cfg           (assoc system-config
                                                                                              :http/runtime-state
                                                                                              {:movies/env movies-env})]
                                                                     (rest-api/start cfg)))
                                                     :stop       (fn [deps] (rest-api/stop (::ds/instance deps)))
                                                     :config     (ds/ref [:config])
                                                     :movies/env (ds/ref [:runtime-state :movies/env])}}}})

(defmethod ds/named-system :local
  [_]
  (ds/system :base
             {[:config] (-> (config/load-config :local)
                            (assoc :http-components (http-components)))}))

(defmethod ds/named-system :test
  [_]
  (ds/system :base
             {[:config]                         (-> (config/load-config :test)
                                                    (assoc :http-components (http-components)))
              [:runtime-state :rest-api/server] :disabled}))
