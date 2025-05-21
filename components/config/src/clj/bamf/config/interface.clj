(ns bamf.config.interface
  (:require [bamf.config.core :as core]))

(set! *warn-on-reflection* true)

(defn load-config [environment] (core/load-config environment))

(defn validate [spec config] (core/validate spec config))
