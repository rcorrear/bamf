# Tasks: Component-Provided HTTP Routing

**Input**: Design documents from `/specs/002-modular-apis/`
**Prerequisites**: plan.md (required), research.md, data-model.md, contracts/, quickstart.md

## Execution Flow (main)
```
1. Read plan.md for architecture, tech stack, and DI constraints
2. Cross-reference research.md and quickstart.md for routing workflow and test ordering
3. Map Component, HttpRouteDeclaration, and AggregatedRouteCatalog entities to workspace files; defer DiagnosticsEvent per updated scope (Reitit handles errors)
4. Draft setup → tests → models → services → integration → polish tasks, tagging [P] where files do not collide
5. Validate coverage: contract, user story, documentation requirements, and follow-up items are reflected
```

## Phase 3.1: Setup
- [X] T001 Confirm baseline failures from repo root with `clojure -X:test :nses '[bamf.rest-api.routes-test]'`
- [X] T002 [P] Create shared route fixture helpers in `bases/rest-api/test/clj/bamf/rest_api/test_support.clj` (mock component registry)

## Phase 3.2: Tests First (TDD) ⚠️ MUST COMPLETE BEFORE 3.3
- [X] T003 [P] Flesh out `get-http-api` contract coverage in `bases/rest-api/test/clj/bamf/rest_api/routes_test.clj` (assert route vector shape, Malli schemas, JSON produces/consumes)
- [X] T004 [P] Add aggregated routing integration specs in `bases/rest-api/test/clj/bamf/rest_api/routes_integration_test.clj` (Movies routes exposed, components without `get-http-api` skipped, Reitit duplicate handling)

## Phase 3.3: Core Implementation (ONLY after tests are failing)
- [X] T005 [P] Define `Component` metadata builder in `bases/rest-api/src/clj/bamf/rest_api/routes/component.clj`
- [X] T006 [P] Define `HttpRouteDeclaration` schema & validation helpers in `bases/rest-api/src/clj/bamf/rest_api/routes/declaration.clj`
- [X] T007 [P] Build `AggregatedRouteCatalog` constructor in `bases/rest-api/src/clj/bamf/rest_api/routes/catalog.clj`
- [X] T008 Implement component route aggregation pipeline in `bases/rest-api/src/clj/bamf/rest_api/routes.clj` (collect `get-http-api`, rely on Reitit exceptions for conflicts)
- [X] T009 Update `bases/rest-api/src/clj/bamf/rest_api/api.clj` to merge aggregated routes with existing `/api` info endpoint
- [X] T010 Implement Movies HTTP handlers delegating to persistence in `components/movies/src/clj/bamf/movies/http.clj`
- [X] T011 Expose `get-http-api` from `components/movies/src/clj/bamf/movies/interface.clj` using new handlers and Malli specs

## Phase 3.4: Integration
- [X] T012 Wire aggregated catalog into router assembly in `bases/rest-api/src/clj/bamf/rest_api/core.clj` (runtime-state injection, middleware awareness)
- [X] T013 Extend `development/src/clj/bamf/dev/core.clj` with component HTTP map and inject `get-http-api` references into the rest-api base system
- [X] T014 Expand `bases/rest-api/src/clj/bamf/rest_api/spec.clj` to validate aggregated routing config keys (http-components)

## Phase 3.5: Polish
- [X] T015 [P] Add unit coverage for Movies HTTP handlers in `components/movies/test/clj/bamf/movies/http_test.clj`
- [X] T016 [P] Document modular route export workflow and tradeoffs in `docs/modular-http-routing.md` (component checklist + rollback notes)
- [X] T017 Log follow-up to revisit HTTP registration strategy in `specs/002-modular-apis/research.md`
- [X] T018 Run full regression with `clojure -X:test` from repo root after integrations land

## Dependencies
- T002 ← T001
- T003 ← T002
- T004 ← T002, T003
- T005, T006, T007 ← T003
- T008 ← T005, T006, T007
- T009 ← T008
- T010 ← T003
- T011 ← T010
- T012 ← T008, T009, T011
- T013 ← T011
- T014 ← T012, T013
- T015 ← T010, T011
- T016 ← T012, T013
- T017 ← T012, T013, T016
- T018 ← T012, T013, T014, T015, T016, T017

## Parallel Example
```
# After T002, author contract and integration tests together:
task-agent run --ids T003,T004

# After T003, tackle independent model files in parallel:
task-agent run --ids T005,T006,T007
```
