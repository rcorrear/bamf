# Contract: Component `get-http-api`

## Purpose
Expose public HTTP routes owned by a component so the REST API base can aggregate them into the external handler.

## Producer
Component interface namespace (e.g., `components.movies.interface`).

## Consumer
`bases.rest-api` during dependency graph assembly.

## Signature (conceptual)
```clojure
(get-http-api component)
=> [route]
```
- `component`: Context map supplied by donut-party.system when constructing components. Must include routes, component name, and any shared dependencies the component needs to build handlers.
- Return value: Vector of Reitit route maps.

## Route Map Shape
```clojure
["/api/v3/movie"
 {:name :movies/list
  :get {:handler components.movies.http/list-movies
        :parameters {:query [:map
                              [:term string?]
                              [:page {:optional true} pos-int?]]}
        :responses {200 {:body [:map [:data [:sequential movie-record]]]}}
        :produces ["application/json"]
        :consumes ["application/json"]
        :middleware [components.movies.http/logged]}}]
```

### Requirements
- Every route must declare Malli request/response schemas to satisfy API validation rules.
- Handlers must return JSON-friendly data structures and offload persistence to Rama modules.
- Route metadata must include ownership and documentation so the REST API can export OpenAPI entries.

## Error Signalling
- Components unable to provide routes should return an empty vector and log a structured Telemere warning describing the reason.
- Components that do not implement `get-http-api` are treated as non-HTTP contributors.

## Observability
- A duplicate or erroneous route declaration will throw when fed to reititi.

## Example: Movies Component Export
```clojure
(defn get-http-api [_]
  [
   ["/api/v3/movie"
    {:name :movies/list
     :get {:handler list-movies
           :parameters {:query [:map
                                 [:term string?]
                                 [:page {:optional true} pos-int?]]}
           :responses {200 {:body [:map [:data [:sequential movie-record]]]}}
           :produces ["application/json"]}}]
   ["/api/v3/movie"
    {:name :movies/create
     :post {:handler create-movie
            :parameters {:body movie-create}
            :responses {201 {:body [:map [:data movie-record]]}}
            :consumes ["application/json"]
            :produces ["application/json"]}}]])
```

Consumers (rest-api) merge the returned routes into their Reitit router definition before building the Ring handler.
