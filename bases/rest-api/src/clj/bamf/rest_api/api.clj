(ns bamf.rest-api.api
  "Public entry points for the REST API base."
  (:require [bamf.rest-api.core :as core]))

(defn get-routes
  "Delegate to `bamf.rest-api.core/get-routes`."
  ([] (core/get-routes))
  ([catalog] (core/get-routes catalog)))

(defn start "Delegate to `bamf.rest-api.core/start`." [config] (core/start config))

(defn stop "Delegate to `bamf.rest-api.core/stop`." [server] (core/stop server))
