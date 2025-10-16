# Tasks: Rama Movie Module

**Input**: Design documents from `/specs/004-add-rama-movie-module/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare dependencies and namespace scaffolding required by all user stories.

- [X] T001 Add ModuleUniqueIdPState + Rama module dependencies in components/movies/deps.edn to enable Rama movie module wiring.
- [X] T002 [P] Create components/movies/src/clj/bamf/movies/rama/common.clj with shared Rama constants and helper functions.
- [X] T003 [P] Relocate Rama client namespaces into components/movies/src/clj/bamf/movies/rama/client/ and update ns declarations/imports.
- [X] T004 [P] Scaffold components/movies/src/clj/bamf/movies/rama/module.clj for MovieModule depot and p-state definitions.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish Rama module wiring (indexes + ModuleUniqueId ack) and provide a clear env injection path that all user stories rely on.

- [X] T005 Implement ModuleUniqueId-aware Rama module ack + env injection so `depot/put!` receives generated ids without relying on a global runtime namespace.
- [X] T006 [P] Define Rama MovieModule depot, ModuleUniqueIdPState binding, and indexes (id/path/metadata/monitored/target-system) in components/movies/src/clj/bamf/movies/rama/module.clj.

---

## Phase 3: User Story 1 - Capture Movies in Rama Catalog (Priority: P1) 🎯 MVP

**Goal**: Persist newly discovered movies with canonical schema, unique identifiers, and movie-created-event emission.

**Independent Test**: Trigger a discovery write and verify the stored record contains all required fields, a generated id, and a published `movie-created-event`.

### Tests for User Story 1

- [X] T007 [P] [US1] Add ModuleUniqueId + movie-created-event coverage to components/movies/test/clj/bamf/movies/save_movies_flow_test.clj.
- [X] T008 [P] [US1] Extend duplicate detection + optional lastSearchTime tests in components/movies/test/clj/bamf/movies/persistence_test.clj.

### Implementation for User Story 1

- [X] T009 [P] [US1] Implement versioned movie-created-event envelope in components/movies/src/clj/bamf/movies/rama/client/depot.clj.
- [X] T010 [P] [US1] Normalize payload fields (timestamps, targetSystem, numeric ids) in components/movies/src/clj/bamf/movies/model.clj.
- [X] T011 [US1] Refactor components/movies/src/clj/bamf/movies/persistence.clj save! to assign ModuleUniqueId, enforce duplicate guards, log Telemere :movies/create-* events, and emit movie-created-event.
- [X] T012 [US1] Update POST /api/v3/movie handler in components/movies/src/clj/bamf/movies/http.clj to map new persistence statuses to contracts/movie-module.json responses.
- [X] T013 [US1] Ensure components/movies/src/clj/bamf/movies/interface.clj delegates create flow through the injected Movies env bindings.

**Checkpoint**: User Story 1 is independently demonstrable once POST creation emits `movie-created-event` and returns canonical data.

---

## Phase 4: User Story 2 - Maintain Movie Monitoring Settings (Priority: P2)

**Goal**: Support updates to monitoring, quality, availability, metadata, and last search fields while emitting movie-updated-event.

**Independent Test**: Submit an update to an existing movie and confirm the persisted record reflects changes and a `movie-updated-event` is published.

### Tests for User Story 2

- [ ] T014 [P] [US2] Cover movie-updated-event merge behaviour in components/movies/test/clj/bamf/movies/persistence_test.clj.
- [ ] T015 [P] [US2] Add HTTP PATCH translation scenarios to components/movies/test/clj/bamf/movies/http_test.clj.

### Implementation for User Story 2

- [ ] T016 [US2] Add movie-updated-event builder emitting :movie.updated with full payload in components/movies/src/clj/bamf/movies/rama/client/depot.clj.
- [ ] T017 [US2] Implement update! command with validation, Telemere :movies/update-* logs, and movie-updated-event emission in components/movies/src/clj/bamf/movies/persistence.clj.
- [ ] T018 [US2] Expose update-movie! facade in components/movies/src/clj/bamf/movies/interface.clj delegating to persistence/update!.
- [ ] T019 [US2] Implement PATCH /api/v3/movie/{id} route + schema coercion in components/movies/src/clj/bamf/movies/http.clj per contracts/movie-module.json.

**Checkpoint**: User Story 2 is independently demonstrable when PATCH updates emit `movie-updated-event` and return updated records.

---

## Phase 5: User Story 3 - Provide Movie Data to Dependents (Priority: P3)

**Goal**: Deliver read APIs to retrieve movies by id, path, and monitored status for schedulers and reporting.

**Independent Test**: Query by id and monitored status and confirm responses include all Movies schema fields with current lastSearchTime values.

### Tests for User Story 3

- [ ] T020 [P] [US3] Add read query coverage (by-id, by-path, monitored filter) in components/movies/test/clj/bamf/movies/interface_test.clj.
- [ ] T021 [P] [US3] Extend GET contract cases for list/detail responses in components/movies/test/clj/bamf/movies/http_test.clj.

### Implementation for User Story 3

- [ ] T022 [US3] Implement Rama lookup helpers (by-id/path/monitored/target-system) in components/movies/src/clj/bamf/movies/inspection.clj.
- [ ] T023 [US3] Update GET /api/v3/movie handler in components/movies/src/clj/bamf/movies/http.clj to support monitored filtering and path lookups.
- [ ] T024 [US3] Expose read API functions from components/movies/src/clj/bamf/movies/interface.clj for downstream consumers.

**Checkpoint**: User Story 3 is independently demonstrable once GET endpoints return accurate datasets for schedulers/reporting.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Align documentation, contracts, and verification across stories.

- [ ] T025 [P] Document movie-created-event and movie-updated-event quickstart steps in specs/004-add-rama-movie-module/quickstart.md.
- [ ] T026 [P] Sync OpenAPI schema changes in specs/004-add-rama-movie-module/contracts/movie-module.json with implemented endpoints.
- [ ] T027 Update docs/save-movies.md to reflect new Rama events, update workflow, and read capabilities.

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → User Story phases → Polish; each stage depends on completion of the previous one.
- User stories follow priority order: US1 (P1) → US2 (P2) → US3 (P3); US2 depends on US1’s persistence scaffolding, US3 depends on foundational Rama lookups.
- Movie-updated-event implementation (T017–T020) depends on movie-created-event infrastructure (T010–T013).
- Read endpoints (T022–T024) depend on Rama module indexes defined in T006.

## Parallel Execution Examples

- After Phase 2, T007, T008, and T009 can proceed in parallel since they touch distinct files for US1.
- Within US2, T014 and T015 can run concurrently while T017 builds on T016.
- For US3, T020 and T021 can execute in parallel before integrating implementation tasks.

## Implementation Strategy

- **MVP First**: Complete Phases 1–3 (Setup, Foundational, US1) to deliver create capability and `movie-created-event`.
- **Incremental Delivery**: Add US2 for update support, then US3 for read APIs, validating each story independently via their checkpoints.
- **Testing Cadence**: Execute `clojure -X:test` after each user story phase to confirm regressions are caught early.
