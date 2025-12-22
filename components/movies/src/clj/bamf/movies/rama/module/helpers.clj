(ns bamf.movies.rama.module.helpers
  "Shared helper functions for the Rama movie module."
  (:require [taoensso.telemere :as t]))

(defn print-it [level args] (t/log! level args))

(defn print-event
  ([level event reason] (print-it level {:event event :reason reason}))
  ([level event reason target value] (print-it level {:reason reason :event event :target target :value value})))

(defn ->instant
  [value]
  (cond (instance? java.time.Instant value) value
        (string? value)                     (try (java.time.Instant/parse value) (catch Exception _ nil))
        :else                               nil))
