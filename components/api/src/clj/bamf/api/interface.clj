(ns bamf.api.interface
  (:require [bamf.api.core :as core]))

(defn get-api-info
  "Returns information about the current and supported API versions"
  [request]
  (core/get-api-info request))
