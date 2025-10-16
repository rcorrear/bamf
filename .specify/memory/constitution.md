<!--
Sync Impact Report
Version: 0.0.1 → 1.0.0
Modified Principles:
- I. Brick independency → P3. Polylith Component Integrity
- II. API Interface → Quality Standards (HTTP contract enforcement)
- III. Minimal request processing → P2. Event-Driven Persistence
- IV. Test-First (NON-NEGOTIABLE) → P5. Tested, Repeatable Delivery
- V. Integration Testing → P5. Tested, Repeatable Delivery
Added Sections: Core Principles (rewritten), Delivery Workflow, Quality Standards, Governance (expanded)
Removed Sections: Additional Constraints, Quality Gates (content merged into principles and standards)
Templates requiring updates:
- ✅ .specify/templates/plan-template.md
- ✅ .specify/templates/spec-template.md
- ✅ .specify/templates/tasks-template.md
Follow-up TODOs:
- TODO(RATIFICATION_DATE): Provide original adoption date.
-->

# BAMF Constitution

## Core Principles

### P1. Specification-First Delivery
All feature work MUST begin with an approved specification, plan, and task list under `specs/<feature>/`.
Specifications MUST articulate user value, prioritized user stories, and acceptance criteria before implementation begins.
Plans and task lists MUST preserve user-story independence, reference concrete file paths, and document constitution gates prior to coding.

### P2. Event-Driven Persistence
All persistence in BAMF MUST be modeled and executed as domain events handled by Rama modules.
Create, update, and delete operations MUST emit explicit events whose payloads capture the persisted record so downstream systems receive the canonical state change.
Events MUST be idempotent, auditable, and versioned; direct mutable storage writes outside an event flow are prohibited.
Future persistence work MUST document new events (e.g., `movie-created-event`, `movie-updated-event`) before implementation.

### P3. Polylith Component Integrity
Polylith components MUST expose behavior only through their interface namespaces; cross-component calls require interface usage, not direct internals.
Shared data structures MUST be namespace-qualified keywords or contracts published in `components/*/spec.clj`.
Dependency injection through `donut-party.system` MUST be used when components collaborate; new components MUST justify their boundaries and own their Rama persistence.

### P4. Telemetry-Backed Operations
Every externally visible action MUST emit structured telemetry using Telemere so lifecycle, performance, and failure modes remain observable.
Instrumentation MUST accompany new events, HTTP endpoints, background jobs, and persistence handlers, including correlation identifiers for end-to-end tracing.
Logging MUST remain comprehensive enough to debug asynchronous workflows in production without replaying events manually.

### P5. Tested, Repeatable Delivery
Automated tests MUST cover every user story before feature completion; red-green-refactor remains the default flow.
Global test coverage MUST stay at or above 80%; no commit may reduce coverage below this threshold.
`clojure -X:test` and any additional checks named in plans or specs MUST pass before merging or releasing.

## Delivery Workflow
1. Draft or update the feature specification in `/specs/<feature>/spec.md`, aligning stories with the principles above.
2. Produce `plan.md` via `/speckit.plan`, documenting constitution gates, component ownership, required events, and telemetry expectations.
3. Develop `tasks.md` via `/speckit.tasks`, organizing implementation in user-story order with event emission and validation work called out explicitly.
4. Execute implementation iteratively in Clojure/Polylith, ensuring each completed story can be demonstrated, tested, and deployed independently.
5. Record configuration updates so APIs can target the correct downstream system (Radarr, Sonarr, or others) through component wiring.

## Quality Standards
- HTTP APIs MUST accept JSON, produce JSON, and remain contract-compatible with the Radarr and Sonarr V3 OpenAPI specifications.
- Request payloads MUST be validated with Malli schemas before invoking persistence or business logic.
- Persistence designs MUST catalog the events they emit, the Rama p-states they touch, and replay expectations.
- Observability work MUST specify metrics, logs, and traces added, along with owners for dashboard updates.
- Documentation updates (README, quickstarts, contracts) MUST accompany behavior that alters external expectations.

## Governance
The BAMF Constitution supersedes conflicting process guidance within this repository.
Amendments require documented rationale, migration steps, and a version bump recorded below.
Compliance is verified during specification review, plan gating, pull-request review, and release readiness checks.
Version numbers follow semantic rules: MAJOR for breaking principle changes, MINOR for new principles or sections, PATCH for clarifications.

**Version**: 1.0.0 | **Ratified**: TODO(RATIFICATION_DATE): Provide original adoption date. | **Last Amended**: 2025-10-16
