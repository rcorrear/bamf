# Implementation Plan: Split into systems

**Branch**: `003-split-into-systems` | **Date**: 2025-10-03 | **Spec**: `/home/rcorrear/Projects/clojure/bamf/specs/003-split-into-systems/spec.md`
**Input**: Feature specification from `/specs/003-split-into-systems/spec.md`

## Summary
- Shape a shared `components/system` Polylith component around a single interface namespace that wraps Donut System lifecycle helpers, tracks the active runtime atom, and exposes `ensure-ns-loaded` for per-project bootstrapping.
- Register lightweight `defmethod` wrappers in project entry-points (`development/src/clj/user.clj`, `projects/radarr/src/clj/user.clj`) that load their Donut namespaces and delegate to the shared `:go` implementation.
- Provide Donut wiring under `development/src/clj/bamf/dev/system.clj` so REPL sessions and tests reuse the same `http-components`, Rama IPC harness, and Aleph bootstrap; Sonarr wiring remains a follow-up once its project namespace is ready.

## Technical Context
**Language/Version**: Clojure 1.12.0  
**Primary Dependencies**: donut-party/system, donut-party/system.repl, com.taoensso/telemere (for error telemetry), Rama test harness for in-memory IPC.  
**Storage**: Rama depots already provisioned; this work layers lifecycle orchestration only.  
**Testing**: `clojure.test` executed via `clojure -X:test`; dedicated lifecycle coverage still to be added.  
**Target Platform**: JVM-based Polylith workspace.  
**Project Type**: Single repo with bases/components/projects layout.  
**Performance Goals**: Keep `start` responsive for interactive REPL use; avoid additional overhead beyond Donut startup.  
**Constraints**: Preserve Polylith brick independence, keep lifecycle wrappers pure outside of Donut calls, respect the constitution's TDD preference (tests pending), and continue emitting Telemere data on startup failures.

**Scale/Scope**: Default BAMF system and Radarr overlay are implemented; Sonarr and additional systems will adopt the same pattern later.

## Constitution Check
- **Brick Independency**: Lifecycle logic centralised in `components/system`; project namespaces remain thin wrappers. → PASS
- **API Interface**: No external HTTP changes; REPL APIs remain stable (`user/go`, etc.). → PASS
- **Minimal Request Processing**: Component delegates to Donut without extra orchestration layers. → PASS
- **Test-First Enforcement**: Regression coverage still outstanding; mark as follow-up to restore full constitution compliance. → FOLLOW-UP
- **Integration Testing Focus**: Manual REPL checks verified behaviour; automated integration tests to be added. → FOLLOW-UP
- **Additional Constraints**: Telemere logging on start failures retained; Rama IPC reuse confirmed. → PASS

## Project Structure

### Documentation (this feature)
```
specs/003-split-into-systems/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── system-lifecycle.md
└── tasks.md
```

### Source Code (workspace excerpt)
```
workspace root/
├── bases/
│   └── rest-api/
├── components/
│   ├── api/
│   ├── config/
│   ├── movies/
│   └── system/                 # shared lifecycle helpers
├── development/
│   └── src/clj/
│       ├── bamf/dev/system.clj # Donut wiring used by shared start
│       └── user.clj            # default BAMF entry-point
├── projects/
│   ├── radarr/
│   │   └── src/clj/
│   │       ├── radarr/dev/system.clj
│   │       └── user.clj
│   └── sonarr/                 # deps scaffold only (wiring pending)
├── docs/
├── specs/
└── tasks/
```

## Phase 0: Research Recap
1. Evaluated options for multi-system orchestration and settled on centralising the Donut helpers in a dedicated component to avoid duplication.  
2. Reconfirmed that simple multimethod dispatch on `:system` + shared `:go` handler met extensibility needs without extra registry layers.  
3. Logged alternatives and decisions in `research.md`.

**Output**: `/home/rcorrear/Projects/clojure/bamf/specs/003-split-into-systems/research.md`

## Phase 1: Design Snapshot
1. Documented the minimal contract (see updated `contracts/system-lifecycle.md`) describing the shared `:go` handler, runtime tracking atom, and delegation expectations.  
2. Captured current data assumptions: runtime atom + Donut state accessors; older profile/environment helpers deferred.  
3. Quickstart still highlights REPL usage; follow-up required to remove Sonarr references once wiring lands.

**Outputs**:
- `/home/rcorrear/Projects/clojure/bamf/specs/003-split-into-systems/contracts/system-lifecycle.md`
- `/home/rcorrear/Projects/clojure/bamf/specs/003-split-into-systems/quickstart.md`

## Phase 2: Task Planning Approach
- Task list now focuses on confirming shared component wiring, updating project entry points, and tracking follow-ups (tests, Sonarr support).  
- Parallelism is minimal given the small surface area; updates typically touch one namespace at a time.  
- Remaining tasks emphasise backfilling automated tests and extending the pattern to additional systems.

**Output**: `/home/rcorrear/Projects/clojure/bamf/specs/003-split-into-systems/tasks.md`

## Phase 3+: Remaining Work
- Add Sonarr lifecycle wiring mirroring Radarr once its namespace is in place.  
- Backfill contract/integration tests to satisfy constitution requirements.  
- Refresh `quickstart.md` and `data-model.md` after the above adjustments.

## Complexity Tracking
*No constitutional deviations beyond outstanding test coverage.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| Pending automated tests | Implementation landed ahead of test scaffolding; revisit in follow-up | Blocking ship on new test scaffolding would delay shared lifecycle adoption |

## Progress Tracking
**Phase Status**:
- [x] Phase 0: Research complete (/plan command)
- [x] Phase 1: Design snapshot documented
- [x] Phase 2: Task plan captured
- [x] Phase 3: Tasks generated (/tasks command)
- [x] Phase 4: Implementation complete
- [ ] Phase 5: Validation passed (automated coverage pending)

**Gate Status**:
- [x] Initial Constitution Check: PASS
- [x] Post-Design Constitution Check: PASS
- [x] All NEEDS CLARIFICATION resolved
- [ ] Follow-up tests implemented

---
*Based on Constitution v2.1.1 — see `/memory/constitution.md`*
