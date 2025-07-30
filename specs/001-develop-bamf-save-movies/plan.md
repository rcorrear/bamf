# Implementation Plan: Save Movies Persistence

**Branch**: `001-develop-bamf-a` | **Date**: 2025-09-21 | **Spec**: [/home/rcorrear/Projects/clojure/bamf/specs/001-develop-bamf-a/spec.md](</home/rcorrear/Projects/clojure/bamf/specs/001-develop-bamf-a/spec.md>)
**Input**: Feature specification from `/specs/001-develop-bamf-a/spec.md`

## Execution Flow (/plan command scope)
```
1. Loaded feature spec from Input path → OK
2. Completed Technical Context using spec + repo insights → OK
3. Reviewed constitution requirements → OK
4. Constitution gate checked (initial) → PASS
5. Phase 0 research captured in research.md → OK
6. Phase 1 design artifacts generated (data model, contracts, quickstart, agent context) → OK
7. Constitution gate re-check (post-design) → PASS
8. Documented Phase 2 task planning approach (tasks.md to be created via /tasks) → READY
9. STOP - Ready for /tasks command
```

## Summary
Save Movies persistence introduces a Rama-backed movie record that mirrors Radarr payloads, validates required fields, normalizes timestamps, enforces uniqueness on the derived metadata identifier (`MovieMetadataId` sourced from `tmdbId`) and `Path`, and persists canonical data (including deduplicated tag vectors and add-option maps). The plan focuses on depot and pstate design, ensuring indexes for metadata, path, and tags, plus confirmation responses without yet integrating search or download orchestration.

## Technical Context
**Language/Version**: Clojure 1.12.0  
**Primary Dependencies**: Rama (depot + pstate), Ring-style JSON API stack (existing Bamf services)  
**Storage**: Rama depot for events, Rama pstate materializing movie records  
**Testing**: clojure.test with Rama contract/integration harness  
**Target Platform**: Linux server runtime (Clojure JVM)  
**Project Type**: single (services + Rama modules within existing monolith)  
**Performance Goals**: Persist each request within normal Rama transaction latency (<100ms goal for enqueue)  
**Constraints**: Must keep handlers thin (constitution), accept/emit JSON, maintain ≥80% coverage, support system selection parameter propagation  
**Scale/Scope**: Initial Save Movies flow for Radarr-compatible payloads; scope limited to persistence (no search/download dispatch)

**Technical Context: $ARGUMENTS**: Focus on persisting movie data only; Tags stored as deduplicated vectors (indexes still track tag sets); AddOptions stored as Clojure map; Rama Id uses Long type; rely on Rama depot history instead of additional logs.

## Constitution Check
- ✅ API must accept and emit JSON (`FR-001`, constitution II)
- ✅ Handlers remain thin; heavy work delegated to Rama (`Execution Flow #1-5`, constitution III)
- ✅ TDD mandated; plan schedules tests before implementation (constitution IV)
- ✅ Integration focus noted for new Rama contracts (constitution V)
- ✅ System selection propagation: persistence design stores requested target for future routing (captured in data model)  
**Initial Constitution Check**: PASS (no violations)

## Project Structure

### Documentation (this feature)
```
specs/001-develop-bamf-a/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── save-movies.json
└── tasks.md   # to be created by /tasks
```

### Source Code (repository root) – Option 1 (single project)
```
src/
├── bamf/api/save_movies.clj        # request validation + dispatch to Rama
├── bamf/rama/movies/depot.clj      # Rama depot definitions and commands
├── bamf/rama/movies/pstate.clj     # Rama pstate materialization + indexes
├── bamf/models/movie.clj           # shared movie record constructors/validation
└── bamf/services/movie_persistence.clj

test/
├── bamf/api/save_movies_test.clj       # endpoint contract tests (TDD)
├── bamf/rama/movies/depot_test.clj     # depot level tests
└── bamf/rama/movies/pstate_test.clj    # pstate/index behavior tests
```

**Structure Decision**: Option 1 (single project) — aligns with existing monolith and Rama modules.

## Core Implementation Sequence

| Step | Description | Reference |
|------|-------------|-----------|
| 1 | Validate incoming payload against Save Movies contract, ensuring required fields and type constraints | contracts/save-movies.json:1-107 |
| 2 | Normalize timestamps to ISO 8601 UTC and default Tags/AddOptions per research decisions | research.md:8-27 |
| 3 | Enforce duplicate prevention on MovieMetadataId and Path before persisting | data-model.md:30-35 |
| 4 | Persist canonical movie record with Long Id and canonical fields ready for future Rama modules | data-model.md:3-28 |

## Phase 0: Outline & Research
- **Unknowns Addressed**: timestamp normalization, default handling for optional fields, Rama Long id strategy, tag indexing strategy.  
- **Research Summary**: Documented in `research.md` covering ISO 8601 UTC enforcement, empty-set/map defaults, Rama long-id generation (monotonic sequence stored in depot), and tag index approaches.  
- **Outcome**: All clarifications resolved; no outstanding blockers for design.

## Phase 1: Design & Contracts
- **Data Model**: `data-model.md` defines Movie Persistence Record fields, validation, and indexes; includes duplicate detection logic and event schema.  
- **API Contract**: `contracts/save-movies.json` captures POST `/api/v3/movie` payload constraints, response schema, and error cases, mirroring Radarr fields plus Bamf identifiers.  
- **Quickstart**: `quickstart.md` provides step-by-step commands for running tests, posting sample payload, and inspecting Rama state.  
- **Component Interface**: Public entry points live in `components/movies/src/clj/bamf/movies/interface.clj`; avoid defining component APIs under an `api` namespace to stay aligned with Polylith conventions.  
- **Agent Context**: Updated `AGENTS.md` manually after `update-agent-context.sh codex` failed on multiline substitutions; file now reflects Clojure + Rama focus (see repository root).  
- **Post-Design Constitution Check**: PASS — design maintains JSON contract, thin handler, Rama-centric processing, and TDD emphasis.

## Phase 2: Task Planning Approach
- `/tasks` will derive numbered tasks from Phase 1 artifacts: start with contract tests (API + Rama), then implement validations, depot, pstate, indexing, and finally confirmation responses; tasks will tag parallelizable Rama components for concurrent work while keeping handler changes sequenced after validations.

## Phase 3+: Future Implementation
- Phase 3 (/tasks) produces execution checklist.  
- Phase 4 implements Rama persistence, indexes, and API handler guided by tasks.  
- Phase 5 validates via test suite and quickstart scenario before merge.

## Complexity Tracking
| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| *(none)* | | |

## Progress Tracking

**Phase Status**:
- [x] Phase 0: Research complete (/plan command)
- [x] Phase 1: Design complete (/plan command)
- [ ] Phase 2: Task planning complete (/plan command - description ready; tasks pending)
- [ ] Phase 3: Tasks generated (/tasks command)
- [ ] Phase 4: Implementation complete
- [ ] Phase 5: Validation passed

**Gate Status**:
- [x] Initial Constitution Check: PASS
- [x] Post-Design Constitution Check: PASS
- [x] All NEEDS CLARIFICATION resolved
- [ ] Complexity deviations documented

---
*Based on Constitution v2.1.1 - See `/memory/constitution.md`*
