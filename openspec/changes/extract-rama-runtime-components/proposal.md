## Why

BAMF's current Donut wiring hides too much runtime structure behind `:go` startup indirection and a movies component that creates the application's Rama IPC itself. That makes the system graph harder to reason about, couples movie module lifecycle to global runtime ownership, and blocks BAMF from adopting the cleaner runtime boundaries already proven in Omni.

## What Changes

- Add dedicated `bamf.rama-client` and `bamf.rama-server` components with the same responsibility split used in Omni.
- Change BAMF runtime composition so Rama is an explicit Donut-managed dependency rather than something the movies component creates internally.
- Keep `bamf.movies.interface/start!` and `stop!` as the canonical movies lifecycle names and update Radarr wiring to use the same interface names as BAMF.
- Change the movies runtime boundary so it depends on a provided Rama runtime and owns only movie-module deployment/connect behavior.
- Replace the current `:go` startup indirection with explicit runtime startup functions that load the relevant system namespace and start the selected Donut system directly.
- Keep BAMF and Radarr composition aligned through shared graph-building patterns and small per-system overlays rather than mostly duplicated startup wiring.

## Capabilities

### New Capabilities
- `rama-runtime-components`: Provide neutral client-side and server-side Rama components that support local IPC-backed runtime ownership and downstream foreign-handle operations without exposing raw `:ipc` assumptions at every consumer boundary.
- `runtime-composition`: Compose BAMF runtime startup around explicit Donut graph nodes and plain startup functions, including an explicit Rama dependency and per-system overlays without `:go` dispatch indirection.
- `movie-module-runtime`: Start, connect, and stop the movies module from a provided Rama runtime while keeping movie persistence and inspection APIs dependent on a declared Rama runtime handle rather than component-owned IPC startup.

### Modified Capabilities
- None.

## Impact

- Affected code: `components/system`, `development/src/clj/user.clj`, `development/src/clj/bamf/dev/system.clj`, `projects/radarr/src/clj/user.clj`, `projects/radarr/src/clj/radarr/dev/system.clj`, and the movies Rama runtime/client namespaces.
- New code: `bamf.rama-client`, `bamf.rama-server`, and likely dedicated dev/runtime composition helper namespaces for Rama and movies.
- Runtime behavior: Donut systems will expose Rama as a first-class dependency, startup will no longer route through a `:go` defmethod, and the existing Radarr/BAMF movies runtime naming mismatch will be removed by keeping `start!` / `stop!` canonical.
- Testing: New unit and composition tests will be needed for neutral Rama runtime handles, movies runtime dependency wiring, and startup/composition behavior.
