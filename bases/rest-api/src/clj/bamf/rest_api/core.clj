(ns bamf.rest-api.core
  {:author "Ricardo Correa"}
  (:require [aleph.http :as http]
            [bamf.rest-api.routes :refer [get-routes]]
            [muuntaja.core :as m]
            [reitit.coercion.malli :as rcm]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.spec :as rs]
            [ring.util.response :as response]
            [taoensso.telemere :as t]))

(set! *warn-on-reflection* true)

(defn ^:private not-found
  []
  (response/not-found {:error "Sorry Dave, I'm afraid I can't do that."}))

(defn ^:private router
  [app-config routes]
  (ring/router routes
               {:validate rs/validate,
                :data {:app-config app-config,
                       :muuntaja m/instance,
                       :coercion rcm/coercion,
                       :middleware [muuntaja/format-middleware
                                    rrc/coerce-exceptions-middleware
                                    rrc/coerce-request-middleware
                                    rrc/coerce-response-middleware]}}))

(defn ^:private static-ring-handler
  [app-config]
  (ring/ring-handler (router app-config (get-routes))
                     (ring/routes (ring/create-resource-handler {:path "/", :not-found-handler (not-found)})
                                  (ring/create-default-handler))))

(defn ^:private repl-friendly-ring-handler
  [app-config]
  (fn [request] ((static-ring-handler app-config) request)))

;; DONUT LIFECYCLE FUNCTIONS ↓

(defn start
  [{{:keys [environment aleph]} :runtime-config, :as app-config}]
  (http/start-server
   (if (contains? #{:local :development} environment)
     (do
       (t/log!
        {:level :info}
        (format
         "using reloadable ring handler for handling requests as the environment is '%s'."
         (name environment)))
       (repl-friendly-ring-handler app-config))
     (do
       (t/log!
        {:level :info}
        (format
         "using static ring handler for handling requests as the environment is '%s'."
         (name environment)))
       (static-ring-handler app-config)))
   (merge {:shutdown-executor? true} aleph)))

(defn stop
  [server]
  (.close server)
  (t/log! {:level :info} "stopped server"))

(comment
  (def s (start {:runtime-config
                 {:environment :local :aleph {:port 9090}} :app-name "bamf"}))

  (stop s))
