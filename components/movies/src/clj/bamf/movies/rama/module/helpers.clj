(ns bamf.movies.rama.module.helpers
  "Shared helper functions for the Rama movie module."
  (:require [taoensso.telemere :as t]))

(defn print-it [level args] (t/log! level args))

(defn print-event
  ([level event reason] (print-it level {:event event :reason reason}))
  ([level event reason target value] (print-it level {:reason reason :event event :target target :value value})))

(defn- type-name [value] (if (nil? value) "nil" (.getName (class value))))

(defn log-map-value-types
  "Log value types for a map in a single event without mutating the Rama tuple stream."
  [value]
  (if (map? value)
    (let [types (reduce-kv (fn [acc k v] (assoc acc k (type-name v))) {} value)]
      (print-it :debug {:event :rama/debug :reason :map-value-types :types types}))
    (print-it :debug {:event :rama/debug :reason :map-value-types :value value :type (type-name value)})))

(defn ->instant
  [value]
  (cond (instance? java.time.Instant value) value
        (string? value)                     (try (java.time.Instant/parse value) (catch Exception _ nil))
        :else                               nil))
