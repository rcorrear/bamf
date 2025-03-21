(ns bamf.config.spec
  {:author "Ricardo Correa"}
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [malli.util :as mu]))

(set! *warn-on-reflection* true)

(def ^:private Config
  [:map
   [:runtime-config
    [:map
     [:aleph
      [:map
       [:port :int]]]
     [:app-name :string]
     [:environment {:default :local}
      [:enum :local :test :development :staging :production]]]]])

(defn apply-defaults
  [config]
  (m/decode Config config mt/default-value-transformer))

(defn validate
  [config]
  (-> (mu/closed-schema Config)
      (m/assert config)
      (me/humanize))
  config)
