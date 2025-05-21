(ns bamf.config.spec
  {:author "Ricardo Correa"}
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [malli.util :as mu]))

(set! *warn-on-reflection* true)

(def ^:private Config
  [:map
   {:closed false}
   [:app-name :string]
   [:environment
    [:enum :local :test :development :staging :production]]])

(defn get-config
  []
  Config)

(defn apply-defaults
  [config]
  (m/decode Config config mt/default-value-transformer))

(defn validate
  ([config] (validate Config config))
  ([spec config]
   (-> (mu/closed-schema spec)
       (m/assert config)
       (me/humanize))
   config))
