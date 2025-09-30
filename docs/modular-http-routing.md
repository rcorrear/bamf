# Modular HTTP Routing

## Overview
- Polylith components expose HTTP routes via an optional `get-http-api` function on their interface namespace.
- The REST API base aggregates these route vectors at startup using `bamf.rest-api.routes/aggregate` and feeds them into the Reitit router.
- Reitit continues to raise on duplicate path/name collisions; no additional diagnostics are emitted in this iteration.

## Component Responsibilities
- Define handlers under the component (e.g. `components/movies/src/clj/bamf/movies/http.clj`) that remain thin and delegate to the existing persistence/runtime layers.
- Export Malli-aware route definitions from `get-http-api`, including `:parameters`, `:responses`, and JSON `:produces`/`:consumes` metadata for every HTTP verb.
- Keep handler tests close to the component (`components/movies/test/.../http_test.clj`) so the contract is covered before aggregation runs.

## REST API Integration
- `bamf.rest-api.routes` normalizes component metadata, invokes `get-http-api`, validates each route via `routes/declaration/validate!`, and collects them into a catalog.
- `bamf.rest-api.api/get-routes` prepends the existing `/api` info endpoint to the aggregated component routes.
- `bamf.rest-api.core/start` loads the catalog once per server boot and passes it to the router so both static and REPL-friendly handlers share the same composed routes.

## Configuration
- `development/src/clj/bamf/dev/core.clj` maintains the authoritative `http-components` map. Each key is a component keyword that maps to `{:component/http-api #'component.interface/get-http-api}`.
- Additional context can be supplied later via `:component/context` or `:component/context-fn`. For now, route functions rely on their existing component lifecycle state.
- The rest-api config spec (`bamf.rest-api.spec/get-spec`) now validates optional `:http-components` and `:http/runtime-state` keys while keeping room for future expansion.

## Trade-offs & Follow-ups
- The explicit `http-components` registry keeps dependency injection transparent but requires manual updates as new components expose HTTP routes.
- We opted not to add a protocol/multimethod discovery layer; evaluating a lighter-weight registration mechanic is captured in research follow-up (see specs/002-modular-apis/research.md).
- Diagnostics beyond Reitit exceptions are deferred. If richer telemetry is needed, extend `routes/declaration` or introduce a post-aggregation auditing step.

## Usage Checklist
1. Implement handlers + tests under the component, delegating to the persistence/runtime APIs.
2. Export the Reitit route vector through `get-http-api` with Malli schemas and JSON metadata.
3. Update `http-components` with the new component entry and restart the system (or refresh in the REPL) to pick up routes.
4. Run `clojure -X:test :nses '[bamf.rest-api.routes-test bamf.rest-api.routes-integration-test]'` to confirm contracts remain satisfied before full suite execution.
