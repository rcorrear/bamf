(ns bamf.rest-api.routes
  "High-level orchestration for aggregating component-provided HTTP routes."
  (:require [bamf.rest-api.routes.catalog :as catalog]
            [bamf.rest-api.routes.component :as component]))

(defn aggregate
  "Gather HTTP routes from configured components.

  Expects a map with keys:
  - :http-components → map of component keyword to config entry (see component/from-config)
  - :runtime-state   → donut-system runtime data made available to components

  Returns a catalog map containing :routes and :source-components."
  [{:keys [http-components runtime-state]}]
  (let [components    (->> (or http-components {})
                           (sort-by key)
                           (map (fn [[component-id entry]] (component/from-config component-id entry))))
        runtime-state (or runtime-state {})]
    (catalog/build components runtime-state)))

