(ns bamf.rest-api.routes.catalog
  "Construct the aggregated catalog of routes contributed by HTTP-capable components."
  (:require [bamf.rest-api.routes.component :as component]
            [bamf.rest-api.routes.declaration :as declaration]))

(defn baseline "Empty catalog baseline." [] {:source-components #{} :routes []})

(defn- add-component
  [catalog component runtime-state]
  (let [routes (component/invoke-http-api component runtime-state)]
    (if (seq routes)
      (let [validated (declaration/validate! routes)]
        (-> catalog
            (update :source-components conj (:component/id component))
            (update :routes into validated)))
      catalog)))

(defn build
  "Reduce a sequence of component metadata entries into an aggregated catalog."
  [components runtime-state]
  (reduce (fn [catalog component] (add-component catalog component runtime-state)) (baseline) components))
