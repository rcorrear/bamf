# Phase 0 Research Summary

## Decision: Component HTTP Contract
- **Decision**: Polylith components that expose HTTP behavior will provide an optional `get-http-api` function on their interface namespace, returning a vector of Reitit route data maps.
- **Rationale**: Encapsulates routing knowledge within the component that owns the capability, maintains brick independence, and lets the dependency injector compose routes without manual wiring.
- **Alternatives Considered**:
  - Central registry in the REST base: rejected because it reintroduces coupling and manual synchronization.
  - Mandatory interface on every component: rejected because non-HTTP components would export meaningless stubs.

## Decision: Participation Scope
- **Decision**: The Movies component migrates first; additional components will adopt the contract incrementally as they are ready.
- **Rationale**: Limits scope for the initial rollout, provides a working reference implementation, and reduces migration risk.
- **Alternatives Considered**:
  - Migrating all components in one release: rejected due to higher coordination cost and simultaneous behavior changes.

## Decision: Aggregation Timing
- **Decision**: Rest API base collects route vectors during system initialization using the existing donut-party.system dependency map; dynamic reloads are out of scope for now.
- **Rationale**: Matches current startup pipeline, avoids introducing hot-reload complexity, and satisfies the immediate requirement.
- **Alternatives Considered**:
  - File-system or message-triggered reloads: rejected as premature optimization without validated demand.

## Decision: Conflict Handling & Diagnostics
- **Decision**: Aggregation will log and surface duplicate path conflicts without blocking startup and will emit diagnostics when a dependent component omits `get-http-api` despite requiring HTTP exposure.
- **Rationale**: Keeps the platform online while flagging issues for owners, aligning with the constitution's emphasis on observability.
- **Alternatives Considered**:
  - Hard failure on conflicts: rejected because conflicts are unlikely in the first iteration and could cause unnecessary downtime.

## Decision: Compliance Guardrails
- **Decision**: Route declarations produced by components must ensure JSON request/response documentation, align with the existing Rama-based persistence pipeline, and include Malli validation hooks so HTTP handlers stay minimal.
- **Rationale**: Enforces constitutional requirements (JSON contracts, thin handlers, validation) and keeps new routes testable under TDD.
- **Alternatives Considered**:
  - Deferring validation/logging rules to a later phase: rejected because it contradicts governance expectations and would increase rework.

## Follow-up
- Revisit HTTP registration ergonomics once more components export routes. The current explicit `http-components` map keeps dependency wiring clear but requires manual updates; evaluate lighter approaches (e.g., multimethod or macro-assisted discovery) after the modular routing flow stabilizes.
