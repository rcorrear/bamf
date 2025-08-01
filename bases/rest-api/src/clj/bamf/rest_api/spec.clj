(ns bamf.rest-api.spec
  {:author "Ricardo Correa"})

(defn get-spec
  []
  [:map
   {:closed false}
   [:aleph
    [:map
     [:port :int]]]])
