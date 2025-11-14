(ns bamf.rest-api.core
  {:author "Ricardo Correa"}
  (:require [aleph.http :as http]
            [bamf.rest-api.routes :as routes]
            [bamf.rest-api.spec :as raspec]
            [muuntaja.core :as m]
            [reitit.coercion.malli :as rcm]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.spec :as rs]
            [ring.logger :as logger]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.util.response :as response]
            [taoensso.telemere :as t]))

(set! *warn-on-reflection* true)

(def ^:private validate-config*
  (delay
    (try
      (requiring-resolve 'bamf.config.interface/validate)
      (catch Throwable cause
        (throw
         (ex-info
          "bamf.config.interface/validate is unavailable. Ensure your active project includes the bamf/config component when starting the REST API."
          {:component :bamf/config :missing-var 'bamf.config.interface/validate}
          cause))))))

(defn- validate-config [spec cfg] ((force validate-config*) spec cfg))

(defn get-routes
  "Return the aggregated Reitit route vector from a catalog map.

  The catalog is expected to come from `bamf.rest-api.routes/aggregate` and contain :routes.
  When no catalog is supplied, defaults to an empty vector so the router can still be built."
  ([] (get-routes nil))
  ([catalog] (vec (:routes catalog []))))

(defn- not-found [] (response/not-found {:error "Sorry Dave, I'm afraid I can't do that."}))

(defn- router
  [runtime-state catalog]
  (ring/router (get-routes catalog)
               {:validate rs/validate
                :data     {:runtime-state runtime-state
                           :muuntaja      m/instance
                           :coercion      rcm/coercion
                           :middleware    [muuntaja/format-middleware rrc/coerce-exceptions-middleware
                                           rrc/coerce-request-middleware rrc/coerce-response-middleware
                                           wrap-stacktrace]}}))

(defn- static-ring-handler
  [runtime-state catalog]
  (ring/ring-handler (router runtime-state catalog)
                     (ring/routes (ring/create-resource-handler {:path "/" :not-found-handler (not-found)})
                                  (ring/create-default-handler))))

(defn- repl-friendly-ring-handler
  [runtime-state catalog]
  (fn [request] ((static-ring-handler runtime-state catalog) request)))

(defn- handler
  [environment cfg catalog]
  (if (contains? #{:local :development} environment)
    (do (t/log! {:level :info}
                (format "using reloadable ring handler for handling requests as the environment is '%s'."
                        (name environment)))
        (repl-friendly-ring-handler cfg catalog))
    (do (t/log! {:level :info}
                (format "using static ring handler for handling requests as the environment is '%s'."
                        (name environment)))
        (static-ring-handler cfg catalog))))

(defn- wrap-with-telemere
  ([handler] (wrap-with-telemere handler nil))
  ([handler options]
   (-> handler
       (logger/wrap-with-logger (merge options
                                       {:log-fn (fn [{:keys [level throwable message]}]
                                                  (if throwable (t/error! throwable) (t/log! level message)))})))))

(defn start
  "Start the REST API HTTP server with the provided Donut configuration map."
  [cfg]
  (validate-config (raspec/get-spec) cfg)
  (let [aleph       (cfg :aleph)
        environment (cfg :environment)
        catalog     (routes/aggregate {:http-components (cfg :http-components)
                                       :runtime-state   (cfg :http/runtime-state)})
        app         (handler environment cfg catalog)
        handler     (wrap-with-telemere app {:log-exceptions? false})]
    (http/start-server handler (merge {:shutdown-executor? true} aleph))))

(defn stop "Stop the running Aleph server instance." [server] (.close server) (t/log! {:level :info} "stopped server"))
