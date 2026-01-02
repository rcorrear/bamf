# Implementation Plan: Save request metadata

**Branch**: `[005-save-request-metadata]` | **Date**: 2026-01-13 | **Spec**: `/home/rcorrear/Projects/clojure/bamf/specs/005-save-request-metadata/spec.md`
**Input**: Feature specification from `/specs/005-save-request-metadata/spec.md`

## Summary

Persist MovieMetadata from save requests into the `metadata-by-movie-id` Rama PState with PUT-only updates and `null` removals, while keeping HTTP behavior unchanged until US4 delivers request validation and response merging. Emit Telemere logs for metadata operations and run `clojure -X:test` after behavior-changing steps.

## Technical Context

**Language/Version**: Clojure 1.12.0  
**Primary Dependencies**: Rama, Donut System, Reitit, Aleph, Muuntaja/Charred, Malli, Telemere  
**Storage**: Rama PStates (`movies`, `metadata-by-movie-id`)  
**Testing**: `clojure -X:test` (clojure.test/kaocha)  
**Target Platform**: JVM service (Linux server)  
**Project Type**: Backend service (Polylith workspace)  
**Performance Goals**: No explicit targets for this feature  
**Constraints**: Event-driven persistence via Rama ops, Malli request validation, Telemere logging, JSON-only HTTP contracts  
**Scale/Scope**: Single feature affecting save/update metadata flows

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **P1 Specification-First Delivery**: Spec/plan/tasks live under `specs/005-save-request-metadata/`. PASS.
- **P2 Event-Driven Persistence**: Metadata persistence handled by Rama ops in create/update modules. PASS.
- **P3 Polylith Component Integrity**: Changes scoped to Movies component and standard interfaces. PASS.
- **P4 Telemetry-Backed Operations**: Telemere `print-event` logs required for metadata ops. PASS.
- **P5 Tested, Repeatable Delivery**: Story tests plus `clojure -X:test` after changes. PASS.
- **Quality Standards**: JSON contracts, Malli validation, contracts/quickstart updates planned. PASS.

**Gate Result**: PASS (no violations).

## Project Structure

### Documentation (this feature)

```text
specs/005-save-request-metadata/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
bases/
└── rest-api/
    └── test/clj/bamf/rest_api/

components/
└── movies/
    ├── src/clj/bamf/movies/
    │   ├── http.clj
    │   ├── inspection.clj
    │   ├── model.clj
    │   ├── persistence.clj
    │   └── rama/
    │       ├── client/pstate.clj
    │       ├── common.clj
    │       └── module/
    │           ├── create.clj
    │           ├── state.clj
    │           └── update.clj
    ├── test/clj/bamf/movies/
    │   ├── http_test.clj
    │   ├── persistence_test.clj
    │   ├── rama/client/depot_test.clj
    │   └── rama/module_integration.clj
    └── test/resources/
```

**Structure Decision**: Polylith workspace; feature changes live in the Movies component (plus its tests/resources) and REST API tests where needed.

## Complexity Tracking

None.
