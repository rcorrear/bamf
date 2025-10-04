(ns bamf.rest-api.routes.declaration
  "Validation helpers for component-provided Reitit route declarations."
  (:require [malli.core :as m]
            [malli.registry :as mr]))

(def ^:private http-method-keys [:get :head :options :post :put :patch :delete])

(defn- ensure-vector [routes] (vec (or routes [])))

(defn- produces-json? [produces] (and (vector? produces) (some #(= "application/json" %) produces)))

(defn- has-http-operation? [data] (some #(contains? data %) http-method-keys))

(def ^:private http-operation-schema
  [:map {:closed false} [:handler any?] [:responses map?] [:produces [:ref ::json-produces]]
   [:parameters {:optional true} map?]])

(def ^:private route-data-schema
  [:and
   (into [:map {:closed false} [:name keyword?]]
         (for [method http-method-keys] [method {:optional true} [:ref ::http-operation]])) [:fn has-http-operation?]])

(def ^:private route-data-registry
  (mr/composite-registry m/default-registry
                         (mr/simple-registry {::json-produces  [:and [:vector [:string]] [:fn produces-json?]]
                                              ::http-operation http-operation-schema})))

(def ^:private route-data-schema* (m/schema route-data-schema {:registry route-data-registry}))

(defn- problem->missing
  [{:keys [path in pred]}]
  (let [candidate (some #(when (keyword? %) %) (reverse (or in path)))]
    (or (when (= pred has-http-operation?) :operation)
        (when (= pred produces-json?) :produces)
        (when (empty? in) :operation)
        candidate)))

(defn validate!
  "Ensure every route declaration includes the JSON contract metadata our platform requires.

  Returns the original vector when validation passes. Throws ExceptionInfo tagged with
  :bamf.rest-api.routes/invalid-route and an accompanying :missing set when fields are absent."
  [routes]
  (let [routes (ensure-vector routes)]
    (doseq [route routes
            :let  [data        (second route)
                   explanation (when-not (m/validate route-data-schema* data) (m/explain route-data-schema* data))
                   issues      (or (:problems explanation) (:errors explanation))
                   missing     (->> issues
                                    (keep problem->missing)
                                    (into #{}))]
            :when explanation]
      (throw (ex-info (str "Invalid route declaration for " (first route))
                      {:type :bamf.rest-api.routes/invalid-route :missing missing :route route})))
    (doseq [children (keep #(nth % 2 nil) routes) :when (sequential? children)] (validate! children))
    routes))
