# Tasks: Save request metadata

**Input**: Design documents from `/specs/005-save-request-metadata/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/
**Tests**: Include story-specific tests as requested in quickstart and acceptance criteria.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US4)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare fixtures and test inputs for metadata flows.

- [X] T001 [P] Add invalid metadata fixture (bad types + invalid status values) in `bamf/components/movies/test/resources/movie-save-request-invalid-metadata.json`
Use existing fixtures `components/movies/test/resources/movie-save-request.json` and `components/movies/test/resources/movie-update-request.json` for valid metadata payloads.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared event payload and storage scaffolding required by all stories.

- [X] T002 Extend movie event payload with MovieMetadata fields in `bamf/components/movies/src/clj/bamf/movies/rama/common.clj`
- [X] T003 Add `metadata-by-movie-id` PState schema and declaration in `bamf/components/movies/src/clj/bamf/movies/rama/module/state.clj`
- [X] T004 Add `metadata-by-movie-id` lookup helpers in `bamf/components/movies/src/clj/bamf/movies/rama/client/pstate.clj`
- [X] T005 Run `clojure -X:test` after Rama module/state/pstate scaffolding changes (T002–T004).

**Checkpoint**: Foundation ready—metadata payload and PState scaffolding are in place.

---

## Phase 3: User Story 1 - Save request captures metadata (Priority: P1) 🎯 MVP

**Goal**: Save requests capture MovieMetadata fields for persistence while leaving HTTP responses unchanged.
**Independent Test**: POST a save request with metadata fields; stored metadata reflects the payload; duplicate POST does not mutate metadata.

### Tests for User Story 1 (required)

- [X] T006 [P] [US1] Add persistence test ensuring save! stores metadata in `bamf/components/movies/test/clj/bamf/movies/persistence_test.clj`
- [X] T007 [P] [US1] Add flow test ensuring duplicate POST does not mutate metadata in `bamf/components/movies/test/clj/bamf/movies/save_movies_flow_test.clj`
- [X] T008 [P] [US1] Add Rama depot client test asserting `foreign-append!` receives the full create payload (including MovieMetadata fields, not only movie PState fields) in `bamf/components/movies/test/clj/bamf/movies/rama/client/depot_test.clj`
- [X] T009 [P] [US1] Add Rama module integration test asserting `foreign-append!` receives the full create payload (including MovieMetadata fields) in `bamf/components/movies/test/clj/bamf/movies/rama/module_integration.clj`

### Implementation for User Story 1

- [X] T010 [US1] Persist MovieMetadata fields on create using a `def/ramaop` in `bamf/components/movies/src/clj/bamf/movies/rama/module/create.clj` (NFR-001); no-op when metadata map is empty
- [X] T011 Run `clojure -X:test` after Rama module create changes (T010).
- [X] T012 [US1] Capture MovieMetadata fields from payload and pass to Rama depot in `bamf/components/movies/src/clj/bamf/movies/persistence.clj`
- [X] T013 Run `clojure -X:test` after US1 persistence changes (T012).

**Checkpoint**: User Story 1 independently testable.

---

## Phase 4: User Story 2 - Metadata updates on PUT only (Priority: P2)

**Goal**: PUT updates replace provided metadata keys while keeping others unchanged; `null` values remove keys.
**Independent Test**: PUT with partial metadata updates only provided keys; omitted keys persist; `null` values remove keys from stored metadata.

### Tests for User Story 2 (required)

- [X] T014 [P] [US2] Add persistence test for partial metadata merge + `null` key removal in `bamf/components/movies/test/clj/bamf/movies/persistence_test.clj`
- [X] T015 [P] [US2] Add Rama depot client test asserting `foreign-append!` receives the full update payload (including MovieMetadata fields, not only movie PState fields) in `bamf/components/movies/test/clj/bamf/movies/rama/client/depot_test.clj`
- [X] T016 [P] [US2] Add Rama module integration test asserting `foreign-append!` receives the full update payload (including MovieMetadata fields) in `bamf/components/movies/test/clj/bamf/movies/rama/module_integration.clj`

### Implementation for User Story 2

- [X] T017 [US2] Persist metadata updates using a `def/ramaop` in `bamf/components/movies/src/clj/bamf/movies/rama/module/update.clj` (NFR-001); no-op when metadata map is empty
- [X] T018 [P] Add Telemere `print-event` log for `hashing-by-movie-id` in metadata Rama ops in `bamf/components/movies/src/clj/bamf/movies/rama/module/create.clj` and `bamf/components/movies/src/clj/bamf/movies/rama/module/update.clj`
- [X] T019 [P] Add Telemere `print-event` log for `saving-metadata` in metadata Rama ops in `bamf/components/movies/src/clj/bamf/movies/rama/module/create.clj` and `bamf/components/movies/src/clj/bamf/movies/rama/module/update.clj`
- [X] T020 Run `clojure -X:test` after Rama module update/telemetry changes (T017–T019).
- [X] T021 [US2] Apply metadata merge rules on PUT (replace provided keys, keep unspecified; `null` removes keys) in `bamf/components/movies/src/clj/bamf/movies/persistence.clj`
- [X] T022 Run `clojure -X:test` after US2 persistence changes (T021).

**Checkpoint**: User Stories 1 and 2 independently testable.

---

## Phase 5: User Story 4 - HTTP validation and handling (Priority: P3)

**Goal**: HTTP requests accept/validate metadata fields, invalid payloads return clear errors, and responses include stored metadata while omitting missing keys.
**Independent Test**: POST or PUT metadata; responses include stored metadata; invalid metadata yields 422 with offending field names and no persistence.

### Tests for User Story 4 (required)

- [ ] T023 [P] [US4] Add HTTP create test verifying the create response includes the submitted metadata fields (same values as request payload) in `bamf/components/movies/test/clj/bamf/movies/http_test.clj`
- [ ] T024 [P] [US4] Add HTTP create test for saves without metadata (ensure success and metadata remains empty/default) in `bamf/components/movies/test/clj/bamf/movies/http_test.clj`
- [ ] T025 [P] [US4] Add HTTP create test for unknown metadata keys being ignored while known keys persist in `bamf/components/movies/test/clj/bamf/movies/http_test.clj`
- [ ] T026 [P] [US4] Add HTTP update test for partial metadata merge + `null` key removal in `bamf/components/movies/test/clj/bamf/movies/http_test.clj`
- [ ] T027 [P] [US4] Add HTTP test for invalid metadata types in `bamf/components/movies/test/clj/bamf/movies/http_test.clj`
- [ ] T028 [P] [US4] Add HTTP test for status normalization (case-insensitive) and invalid status rejection in `bamf/components/movies/test/clj/bamf/movies/http_test.clj`
- [ ] T029 [P] [US4] Add persistence test for invalid metadata rejection in `bamf/components/movies/test/clj/bamf/movies/persistence_test.clj`

### Implementation for User Story 4

- [ ] T030 [US4] Add MovieMetadata recognized key set and type validation helpers in `bamf/components/movies/src/clj/bamf/movies/model.clj` (validate both pre- and post-normalization key sets; keys must match in both passes)
- [ ] T031 [US4] Add case-insensitive status normalization helpers in `bamf/components/movies/src/clj/bamf/movies/model.clj`
- [ ] T032 [US4] Extend create/update request schemas to accept camelCase MovieMetadata fields in `bamf/components/movies/src/clj/bamf/movies/http.clj`
- [ ] T033 [US4] Merge `metadata-by-movie-id` into inspection responses for list/get in `bamf/components/movies/src/clj/bamf/movies/inspection.clj`
- [ ] T034 Run `clojure -X:test` after schema/inspection changes (T030–T033).
- [ ] T035 [US4] Merge stored metadata into create responses in `bamf/components/movies/src/clj/bamf/movies/persistence.clj`
- [ ] T036 [P] [US4] Add Telemere instrumentation to HTTP create/update handlers in `bamf/components/movies/src/clj/bamf/movies/http.clj` (include correlation identifiers)
- [ ] T037 [US4] Add metadata validation error messages that name offending fields in `bamf/components/movies/src/clj/bamf/movies/model.clj`
- [ ] T038 Run `clojure -X:test` after US4 implementation task (T037).

**Checkpoint**: All user stories independently testable with HTTP validation coverage.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T039 [P] Verify metadata contract matches implementation in `bamf/specs/005-save-request-metadata/contracts/openapi.yaml`
- [ ] T040 [P] Update verification notes and fixture references in `bamf/specs/005-save-request-metadata/quickstart.md`
- [ ] T041 [P] Document observability scope (print-event logs only; no metrics/traces for this feature) in `bamf/specs/005-save-request-metadata/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → **User Stories (Phase 3+)** → **Polish (Phase 6)**

### User Story Dependencies

- **US1 (P1)**: Starts after Foundational; no dependencies on other stories
- **US2 (P2)**: Starts after Foundational; can run in parallel with US1
- **US4 (P3)**: Starts after Foundational; can run in parallel with US1/US2

### Dependency Graph (Stories)

Foundational → US1
Foundational → US2
Foundational → US4

---

## Parallel Opportunities

- Setup tasks (T001) can run in parallel.
- Test tasks within each story can run in parallel (different files).
- After Foundational, US1/US2/US4 can proceed in parallel if staffed.

---

## Parallel Example: User Story 1

```text
T006 persistence save! test in persistence_test.clj
T007 duplicate POST flow test in save_movies_flow_test.clj
T008 depot client payload test in rama/client/depot_test.clj
```

## Parallel Example: User Story 2

```text
T014 persistence update test in persistence_test.clj
T015 depot client update payload test in rama/client/depot_test.clj
```

## Parallel Example: User Story 4

```text
T023 HTTP create test in http_test.clj
T026 HTTP update test in http_test.clj
T027 HTTP invalid metadata test in http_test.clj
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Validate US1 independently

### Incremental Delivery

1. Setup + Foundational
2. US1 → validate
3. US2 → validate
4. US4 → validate
5. Polish

---

## Notes

- [P] tasks indicate different files with no direct dependencies.
- Each user story is independently testable once its phase completes.
