(ns bamf.config.interface
  (:require [bamf.config.core :as core]))

(set! *warn-on-reflection* true)

(defn apply-defaults [config] (core/apply-defaults config))

(defn load-config [environment] (core/load-config environment))

(defn validate [config] (core/validate config))
