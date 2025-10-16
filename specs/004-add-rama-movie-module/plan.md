# Implementation Plan: Rama Movie Module

**Branch**: `004-add-rama-movie-module` | **Date**: 2025-10-16 | **Spec**: specs/004-add-rama-movie-module/spec.md
**Input**: Feature specification from `/specs/004-add-rama-movie-module/spec.md`

## Summary

Implement a Rama-backed movie catalog module that mirrors the Movies schema, generates unique ids via `ModuleUniqueIdPState`, emits versioned `movie-created-event`/`movie-updated-event` envelopes carrying full canonical records, and exposes create/update/read operations required by ingestion, curation, and scheduling flows.

## Technical Context

**Language/Version**: Clojure 1.12.0  
**Primary Dependencies**: Rama (module + p-states), donut-party.system, Telemere  
**Storage**: Rama Movies p-state backed by ModuleUniqueIdPState id generation  
**Testing**: `clojure -X:test` with Polylith component/unit tests  
**Target Platform**: JVM service deployed on Linux hosts  
**Project Type**: Polylith backend (event-driven services)  
**Performance Goals**: Persisted records visible to downstream schedulers within 5s of ingestion (SC-002)  
**Constraints**: Enforce numeric validation, deduplicate by path, emit audit-friendly events, optional `LastSearchTime`  
**Scale/Scope**: Library-scale movie catalog powering ingestion, curation, and scheduling across BAMF modules

## Constitution Check

- [x] **Specification Assets**: `spec.md` and this `plan.md` exist; `tasks.md` will be produced before implementation begins (P1).
- [x] **Event-Driven Persistence**: Versioned `movie-created`/`movie-updated` events with full payloads will back the Movies p-state; see research.md for depot + ModuleUniqueId wiring (P2).
- [x] **Component Boundaries**: Movies Polylith component owns Rama module; interfaces exposed via `bamf.movies.interface`; wiring updates go through `donut-party.system` in `components/system` (P3).
- [x] **Telemetry Coverage**: Telemere will emit structured `:movies/create-*` and `:movies/update-*` events across validation, persistence, and depot failures per research.md (P4).
- [x] **Testing & Coverage**: Expand component tests plus Rama flow tests under `components/movies/test`; ensure `clojure -X:test` passes and cover new user stories (P5).
- [x] **HTTP Contract Alignment**: REST API in `bases/rest-api` continues to expose movie endpoints with Radarr-compatible payloads; any surface changes will update contracts (Quality Standards).

## Project Structure

### Documentation (this feature)

```
specs/004-add-rama-movie-module/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
```

### Source Code (repository root)

```
components/
├── movies/
│   ├── src/clj/bamf/movies/        # Rama movie module implementation + interface
│   │   ├── rama/
│   │   │   ├── common.clj          # Shared Rama constants/helpers
│   │   │   ├── module.clj          # Rama module + p-state definitions
│   │   │   └── client/             # Depot + p-state client namespaces
│   │   │       ├── depot.clj
│   │   │       └── pstate.clj
│   └── test/clj/bamf/movies/       # Component and flow tests
├── config/
│   └── src/clj/bamf/config/        # Runtime configuration + DI bindings
└── system/
    └── src/clj/bamf/system/        # donut-party.system lifecycle wiring

bases/
└── rest-api/
    ├── src/clj/bamf/rest_api/      # HTTP endpoints delegating to movie interface
    └── test/clj/bamf/rest_api/     # Contract tests for HTTP surfaces

development/
└── rama/
    └── scripts/                    # Rama dev tooling and helpers
```

**Structure Decision**: Feature work concentrates on `components/movies` Rama module plus supporting wiring in `components/system` and surface updates through `bases/rest-api`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | No constitution gate violations requiring exceptions | N/A |
