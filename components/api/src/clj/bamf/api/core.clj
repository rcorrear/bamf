(ns bamf.api.core)

(defn get-api-info
  "Returns information about the current and supported API versions"
  [_]
  {:current "v3", :deprecated []})
