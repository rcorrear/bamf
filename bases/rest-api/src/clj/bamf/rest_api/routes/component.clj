(ns bamf.rest-api.routes.component "Utilities for building consistent metadata about HTTP-capable components.")

(def ^:private default-context {})

(defn from-config
  "Coerce the config entry found under :http-components into a canonical component map.

  component-id - keyword identifying the component (e.g. :components/movies)
  config-entry - map containing optional keys:
                 :component/http-api      → fn that returns Reitit route data
                 :component/context       → static map merged into the invocation context
                 :component/context-fn    → (fn [runtime-state] ...) for dynamic context
  Returns a map with normalized keys used during aggregation.
  Missing entries default to sensible values so downstream code can assume presence."
  [component-id config-entry]
  {:pre [(keyword? component-id)]}
  (let [{:component/keys [http-api context context-fn] :or {context default-context}} (or config-entry {})]
    {:component/id         component-id
     :component/http-api   http-api
     :component/context    context
     :component/context-fn context-fn}))

(defn http-capable?
  "True when the component exposes a callable get-http-api function."
  [component]
  (ifn? (:component/http-api component)))

(defn invocation-context
  "Build the map supplied to component get-http-api functions.

  Runtime state is merged with any static or dynamic context declared in config."
  [component runtime-state]
  (let [dynamic-context (if-let [context-fn (:component/context-fn component)]
                          (or (context-fn runtime-state) default-context)
                          default-context)]
    (merge {:component/id (:component/id component) :runtime-state runtime-state}
           dynamic-context
           (:component/context component))))

(defn invoke-http-api
  "Invoke the component's get-http-api if present, returning the declared routes.

  Returns nil when the component isn't HTTP-capable so callers can filter results."
  [component runtime-state]
  (when (http-capable? component)
    (let [context (invocation-context component runtime-state)] ((:component/http-api component) context))))
