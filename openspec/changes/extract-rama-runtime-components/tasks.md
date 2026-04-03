## 1. Shared Rama Components

Complete this section first. Later sections depend on these shared boundaries existing.

- [x] 1.1 Create the `bamf.rama-client` component with neutral runtime wrappers and shared foreign-operation helpers for local Rama runtime usage.
- [x] 1.2 Create the `bamf.rama-server` component and its public namespace as the canonical worker-side Rama boundary, even if no generic BAMF helper migrates into it yet.
- [x] 1.3 Add focused tests for runtime ownership semantics, handle unwrapping, and shared client helper behavior.

## 2. Movies Runtime Refactor

Complete this section after Section 1. Section 3 depends on the new movies runtime boundary.

- [x] 2.1 Keep `bamf.movies.interface/start!` and `stop!` as the canonical lifecycle functions and update Radarr callers to use those names.
- [x] 2.2 Refactor movies runtime startup so `start!` accepts a config map containing `:rama` and returns movie-specific handles plus explicit module-ownership metadata.
- [x] 2.3 Update movie PState client code to use the shared Rama client boundary instead of raw `:ipc` assumptions and nested env fallbacks, while keeping depot appends driven by explicit movie depot handles.
- [x] 2.4 Update movies unit and integration tests to cover the new runtime/env shape and movie-module lifecycle behavior.

## 3. Donut Composition And Startup

Complete this section after Sections 1 and 2. It depends on the shared Rama components and canonical movies runtime API.

- [x] 3.1 Introduce a dedicated `bamf.dev.system.rama` helper namespace and explicit Donut graph wiring for a shared Rama node that movies depend on through `ds/ref`.
- [x] 3.2 Refactor BAMF and Radarr system composition so shared graph-building is reused and system-specific differences remain small overlays.
- [x] 3.3 Replace the `bamf.system.interface` startup multimethod and `:go` defmethod flow with explicit startup functions while preserving the current REPL command surface (`start`, `stop`, `restart`, `status`, `config`).

## 4. Verification

Complete this section last after the implementation sections above.

- [x] 4.1 Add or update composition tests that verify Rama is an explicit dependency node and startup no longer relies on `:go` dispatch.
- [x] 4.2 Run the relevant unit and integration test suites for system startup and movies runtime wiring.
- [x] 4.3 Update developer-facing runtime documentation if the startup or composition model changes in user-visible ways.
