# Implementation Plan: Component-Provided HTTP Routing

**Branch**: `002-modular-apis` | **Date**: 2025-09-28 | **Spec**: `/home/rcorrear/Projects/clojure/bamf/specs/002-modular-apis/spec.md`
**Input**: Feature specification from `/specs/002-modular-apis/spec.md`

## Summary
- Components that expose HTTP routes will add an optional `get-http-api` interface returning Reitit route data, starting with the Movies component.
- The REST API base gathers all available route vectors during dependency injection and merges them into a single router.
- Conflict handling is hard-fail, depending on reitit throwing exceptions; future hot reloads remain out of scope.

## Technical Context
**Language/Version**: Clojure 1.12.0 (Polylith workspace)  
**Primary Dependencies**: donut-party.system (DI), Reitit (routing), Ring, Rama (persistence), Malli (validation), Telemere (logging)  
**Storage**: No new storage; reuse existing Rama event streams managed by component handlers  
**Testing**: `clojure.test` executed via `clojure -X:test` with TDD-first failing tests  
**Target Platform**: JVM-based backend service  
**Project Type**: single (backend Monolith/Polylith)  
**Performance Goals**: Minimal handler work with routing merge performed once at startup  
**Constraints**: Obey BAMF constitution—JSON-only APIs, thin handlers delegating to Rama, Malli validation, Telemere observability, TDD workflow  
**Scale/Scope**: Initial rollout limited to Movies component export; additional components migrate iteratively  
**User Notes**: No additional implementation directives supplied via `$ARGUMENTS`

## Constitution Check
- **JSON Contracts**: Component routes must declare JSON request/response schemas to satisfy Section II.  
- **Minimal Handlers**: Aggregated routes keep handlers thin and delegate persistence to Rama per Section III.  
- **TDD Enforcement**: Failing contract tests created before implementation per Section IV.  
- **Validation & Logging**: Malli validation and Telemere logging wired into aggregation diagnostics.  
- **Dependency Injection**: Route exports respect brick independence via donut-party.system (Section I).

**Result**: PASS — No constitutional violations identified; Complexity Tracking remains empty.

## Project Structure

### Documentation (this feature)
```
specs/002-modular-apis/
├── plan.md              # This file (/plan command output)
├── research.md          # Phase 0 output (/plan command)
├── data-model.md        # Phase 1 output (/plan command)
├── quickstart.md        # Phase 1 output (/plan command)
├── contracts/           # Phase 1 output (/plan command)
└── tasks.md             # Phase 2 output (/tasks command - NOT created by /plan)
```

### Source Code (repository root)
```
workspace root/
├── bases/
│   └── rest-api/
├── components/
│   ├── api/
│   ├── config/
│   └── movies/
├── development/
├── docs/
├── projects/
├── specs/
└── src/
```

**Structure Decision**: Polylith workspace; behaviour lives within component/base bricks and their associated `test/` directories (e.g., `bases/rest-api/test`).

## Phase 0: Outline & Research
1. Identified contract ownership, aggregation timing, conflict handling, and compliance guardrails.  
2. Documented findings in `research.md` with decision/rationale/alternative breakdowns.  
3. Confirmed no outstanding unknowns remain before moving to design.

**Output**: `/home/rcorrear/Projects/clojure/bamf/specs/002-modular-apis/research.md`

## Phase 1: Design & Contracts
1. Modeled entities (`Component`, `HttpRouteDeclaration`, `AggregatedRouteCatalog`, `DiagnosticsEvent`) in `data-model.md`.  
2. Authored contract `contracts/get-http-api.md` describing the interface and required Reitit map shape.  
3. Added failing contract tests in `bases/rest-api/test/bamf/rest_api/routes_test.clj` to demand aggregation, diagnostics, and conflict detection.  
4. Captured developer quickstart covering test execution, implementation checkpoints, observability, and rollback steps.  
5. Updated Codex agent context via `.specify/scripts/bash/update-agent-context.sh codex` to capture the current stack.

**Output**: Data model, contract docs, quickstart, and failing tests prepared under `/home/rcorrear/Projects/clojure/bamf/specs/002-modular-apis/` and `bases/rest-api/test`.

## Phase 2: Task Planning Approach
- Use `/tasks` to convert design artifacts into actionable work.  
- Derive tasks for implementing `get-http-api` in Movies, building aggregation logic in REST base, adding diagnostics, and satisfying failing tests.  
- Maintain TDD order by tackling contract tests before implementation; mark independent component updates as parallelizable.

**Estimated Output**: Numbered plan (~25 tasks) covering tests, route export implementation, diagnostics, and documentation updates.

## Phase 3+: Future Implementation
**Phase 3**: Task execution (/tasks command creates tasks.md)  
**Phase 4**: Implementation (follow tasks.md with TDD)  
**Phase 5**: Validation (run tests, quickstart, performance checks)

## Complexity Tracking
*No constitutional violations to justify.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|

## Progress Tracking
**Phase Status**:
- [x] Phase 0: Research complete (/plan command)
- [x] Phase 1: Design complete (/plan command)
- [x] Phase 2: Task planning complete (/plan command - describe approach only)
- [ ] Phase 3: Tasks generated (/tasks command)
