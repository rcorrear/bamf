(ns radarr.rest-api.static-routes
  "Radarr-specific static REST API routes that complement component-provided catalog entries."
  (:require [ring.util.mime-type :as mime]))

(def json-media-type (mime/default-mime-types "json"))

(def json-media [json-media-type])

(def api-info {:current "v3" :deprecated []})

(defn info-route
  "Expose platform metadata about the Radarr API entry point."
  []
  ["/api"
   {:name :static/api
    :get  {:name      :static/api
           :handler   (fn [_] {:status 200 :body api-info})
           :responses {200 {:body {:current string? :deprecated [:sequential string?]}}}
           :produces  json-media}}])

(defn routes "Return the static route vector Radarr contributes to the REST API base." [] [(info-route)])

(defn get-http-api "Produce the configuration entry consumed by bamf.rest-api.routes/aggregate." [_] (routes))

(comment
  (require '[bamf.rest-api.routes.declaration :as routes])
  (routes/validate! (routes)))
