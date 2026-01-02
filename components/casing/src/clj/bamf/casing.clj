(ns bamf.casing
  "Recursive key casing helpers shared across BAMF.

  - `->kebab-keys` turns map keys into kebab-case (keywords stay keywords, strings stay strings).
  - `->camel-keys` turns map keys into camelCase (keywords stay keywords, strings stay strings).
  - `wrap->kebab->camel` is a Ring middleware that normalizes request maps to kebab-case
    and camelizes response bodies."
  (:require [camel-snake-kebab.core :as csk]
            [clojure.walk :as walk]))

(defn- format-keys
  [format-fn data]
  (letfn [(fmt-key [k]
            (cond (keyword? k) (-> k
                                   name
                                   format-fn
                                   keyword)
                  (string? k)  (format-fn k)
                  :else        k))]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map (fn [[k v]] [(fmt-key k) v]) x)) x)) data)))

(defn ->kebab-keys "Recursively convert map keys to kebab-case." [data] (format-keys csk/->kebab-case data))

(defn ->camel-keys "Recursively convert map keys to camelCase." [data] (format-keys csk/->camelCase data))

(defn wrap->kebab->camel
  "Ring middleware that normalizes incoming request keys to kebab-case and camelizes response bodies."
  [handler]
  (fn [request]
    (let [normalized (-> request
                         (update :params ->kebab-keys)
                         (update :body-params ->kebab-keys)
                         (update :form-params ->kebab-keys)
                         (update :query-params ->kebab-keys)
                         (update :path-params ->kebab-keys)
                         (update :parameters ->kebab-keys))]
      (-> (handler normalized)
          (update :body ->camel-keys)))))
