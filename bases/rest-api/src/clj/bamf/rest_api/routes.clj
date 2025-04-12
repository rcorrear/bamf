(ns bamf.rest-api.routes
  (:require [bamf.api.interface :as api]))

(defn get-routes
  []
  ["/api"
   {:get {:handler (fn [req] {:status 200
                              :body (api/get-api-info req)}),
          :responses {200 {:body {:current string?
                                  :deprecated [:sequential string?]}}}}}])
