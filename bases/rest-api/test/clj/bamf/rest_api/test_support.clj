(ns bamf.rest-api.test-support)

(def movie-list-route
  ["/api/v3/movie"
   {:name :movies/list
    :get  {:handler    :stubbed
           :parameters {:query [:map [:term string?] [:page {:optional true} pos-int?]]}
           :responses  {200 {:body [:map [:data [:sequential :movie-record]]]}}
           :produces   ["application/json"]
           :consumes   ["application/json"]}}])

(def movie-create-route
  ["/api/v3/movie"
   {:name :movies/create
    :post {:handler    :stubbed
           :parameters {:body :movie-create}
           :responses  {201 {:body [:map [:data :movie-record]]}}
           :produces   ["application/json"]
           :consumes   ["application/json"]}}])

(defn stub-http-component
  "Build a stub http-component entry for the aggregation config.
  Captures invocation contexts in the supplied atom (if provided)."
  ([component-key routes] (stub-http-component component-key routes (atom [])))
  ([component-key routes call-log]
   (let [get-http-api (fn [context] (swap! call-log conj context) routes)]
     [component-key {:component/id component-key :component/http-api get-http-api :component/call-log call-log}])))

(defn http-components
  "Create the map expected by the aggregation pipeline from stub entries."
  [& entries]
  (into {} entries))

(defn rest-api-config
  "Build a minimal rest-api configuration map for aggregation tests."
  [& entries]
  {:environment :test :http-components (apply http-components entries)})
