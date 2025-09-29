# Tasks: Save Movies Persistence

**Input**: Design documents from `/specs/001-develop-bamf-a/`
**Prerequisites**: plan.md (required), research.md, data-model.md, contracts/

## Phase 3.1: Setup
- [X] T001 Scaffold movie component namespaces under `components/movies/src/clj/bamf/movies` (API façade, model helpers, Rama client) and matching test namespaces under `components/movies/test/clj/bamf/movies`; add stubs referencing existing `components/movies/test/resources/movie.json` seed data.
- [X] T002 Ensure `components/movies/test/clj` fixtures load `test/resources/movie.json` and prune/update `components/movies/deps.edn` test alias only if additional test paths are introduced (root `deps.edn` remains unchanged).

## Phase 3.2: Tests First (TDD) ⚠️ MUST COMPLETE BEFORE 3.3
- [X] T003 [P] Author contract test for `POST /api/v3/movie` in `components/api/test/clj/bamf/api/save_movies_contract_test.clj`, asserting JSON schema validation and success response shape from `contracts/save-movies.json`.
- [X] T004 [P] Create unit tests in `components/movies/test/clj/bamf/movies/persistence_test.clj` covering normalization (ISO 8601 UTC, tag set defaults) and duplicate rejection rules from `research.md` & `data-model.md`.
- [X] T005 [P] Implement integration test in `components/movies/test/clj/bamf/movies/save_movies_flow_test.clj` that exercises quickstart flow: successful persistence then duplicate submission returning `400` using `movie.json` seed.

## Phase 3.3: Core Implementation (ONLY after tests are failing)
- [X] T006 Implement `components/movies/src/clj/bamf/movies/model.clj` with validation + normalization helpers that satisfy T004.
- [X] T007 Implement `components/movies/src/clj/bamf/movies/rama_client/depot.clj` defining the `movie.saved` event command and Long id sequencing per data-model indexes.
- [X] T008 Implement `components/movies/src/clj/bamf/movies/rama_client/pstate.clj` with materialized movie record + indexes (`:movie/by-id`, `:movie/by-metadata-id`, `:movie/by-path`, `:movie/by-tag`, `:movie/by-target-system`).
- [X] T009 Implement `components/movies/src/clj/bamf/movies/persistence.clj` orchestrating validation, normalization, duplicate checks, and persistence by delegating to the `rama_client` namespaces to satisfy tests.
- [X] T010 Implement `components/api/src/clj/bamf/api/save_movies.clj` handler function that maps HTTP input to the movie persistence API and returns 201/400 responses (route hooks deferred to later spec).

## Phase 3.4: Integration
- [X] T011 Provide helper in `components/movies/src/clj/bamf/movies/inspection.clj` (or similar) to expose Rama index lookup through `rama_client` for quickstart verification used in T014.
- [X] T012 Remove bespoke in-memory helper and rely on Rama test harness (`com.rpl.rama.test/create-ipc`) for any manual quickstart experiments.

## Phase 3.5: Polish
- [X] T013 [P] Extend documentation (`docs/save-movies.md` or update `quickstart.md`) with payload/response examples and duplicate-handling notes from tests.
- [X] T014 Add high-level verification in `components/movies/test/clj/bamf/movies/save_movies_flow_test.clj` ensuring Rama indexes exposed via `rama_client` surface the stored movie, and run `clojure -X:test` to confirm all suites pass.

## Dependencies
- T002 depends on completion of T001.
- Tests (T003–T005) must run and fail before implementation tasks (T006–T010).
- T006 feeds into T009; T007 and T008 must precede T009.
- T009 must complete before T010.
- T011 depends on Rama state produced by T008/T009.
- T012 relies on the API function from T010 and helper outputs from T011.
- Polish tasks (T013–T014) occur after all prior phases succeed.

## Parallel Example
```
# Execute independent test authoring tasks together:
Task: "T003 Author contract test for POST /api/v3/movie in components/api/test/clj/bamf/api/save_movies_contract_test.clj"
Task: "T004 Create unit tests in components/movies/test/clj/bamf/movies/persistence_test.clj"
Task: "T005 Implement integration test in components/movies/test/clj/bamf/movies/save_movies_flow_test.clj"
```

## Notes
- [P] tasks touch distinct files with no shared state.
- Verify contract/integration tests fail before writing implementation (T006–T010).
- Use quickstart curl commands after T012 to validate end-to-end behavior.
