(ns bamf.rest-api.spec {:author "Ricardo Correa"})

(def ^:private component-entry-spec
  [:map {:closed false} [:component/http-api ifn?] [:component/context {:optional true} map?]
   [:component/context-fn {:optional true} ifn?]])

(defn get-spec
  []
  [:map {:closed false} [:aleph [:map [:port :int]]]
   [:http-components {:optional true} [:map-of keyword? component-entry-spec]]
   [:http/runtime-state {:optional true} map?]])
