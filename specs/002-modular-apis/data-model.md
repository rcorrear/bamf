# Phase 1 Data Model

## Component
- **Identifier**: Polylith component keyword (e.g., `:components/movies`).
- **Dependencies**: Set of component/base references declared through `deps.edn` / donut-party.system.
- **HTTP Capability**: Optional function reference `get-http-api` when the component owns public HTTP routes.
- **Metadata**: Version, ownership team, and documentation links for diagnostics output.

## HttpRouteDeclaration
- **owner** (`keyword`): Component that produced the route.
- **routes** (`vector` of `map`): Reitit-compatible forms including path, method, handler, interceptors, and Malli schemas.
- **contracts** (`map`): JSON request/response schema references to align with OpenAPI expectations.
- **diagnostics** (`map`): Flags for missing schemas, incomplete docs, or deprecated paths.

## AggregatedRouteCatalog
- **source-components** (`set`): Components contributing routes during initialization.
- **assembled-routes** (`vector`): Flattened Reitit route tree consumed by the REST API base.
- **conflicts** (`vector`): Detected duplicates with owning component hints.
- **generation-timestamp** (`inst`): When the catalog was composed; assists future hot-reload support.

## DiagnosticsEvent
- **type** (`enum`): `:missing-contract`, `:duplicate-route`, `:invalid-schema`.
- **component** (`keyword`): Offending component.
- **details** (`map`): Explanation for operators.
- **action** (`enum`): `:warn`, `:fail` (current iteration uses `:warn`).

## Runtime Configuration
- **dependency-graph**: Donut-party.system map describing base/component wiring.
- **environment** (`enum`): `:dev`, `:test`, `:prod`; drives logging verbosity and diagnostics sink.
- **logging** (`map`): Telemere configuration ensuring route aggregation emits structured events.
