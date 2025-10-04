# Phase 0 Research

## Decision: Centralize lifecycle orchestration in the `system` component
- **Rationale**: A dedicated Polylith component keeps lifecycle logic independent from project-specific namespaces (user→bamf default, radarr, sonarr) while enabling reuse and dependency injection via donut-party.system.
- **Alternatives Considered**:
  - *Duplicate lifecycle functions per project namespace*: rejected because it violates brick independence and increases maintenance burden.
  - *Embed lifecycle orchestration inside bases*: rejected because multiple projects (CLI/dev) need the same capabilities outside a single base.

## Decision: Dispatch lifecycle operations via multimethod keyed by `[system environment]`
- **Rationale**: Multimethods provide declarative dispatch for existing and future systems, allowing tailored configuration per environment without conditional branching (including the default `[:system/bamf env]` combinations).
- **Alternatives Considered**:
  - *Protocol per system*: rejected because protocols require compile-time implementation and lack environment-specific dispatch.
  - *Dynamic case statements*: rejected due to poor extensibility and higher risk of missing new systems.

## Decision: Expose monitoring through existing `status`, `runtime-state`, and `config` functions
- **Rationale**: These functions already deliver the observability operators expect; reusing them ensures consistent outputs and minimizes new telemetry plumbing.
- **Alternatives Considered**:
  - *Introduce new monitoring interface*: rejected because it duplicates capabilities and delays delivery.
  - *Rely solely on Telemere logs*: rejected; logs alone do not provide structured inspection for automation.

## Decision: Use donut-party.system dependency graphs to register system lifecycles
- **Rationale**: Donut-party ensures configuration remains declarative; each project namespace can request the `system` brick and supply its system key.
- **Alternatives Considered**:
  - *Manual lifecycle factories per project*: rejected because it bypasses DI and risks tight coupling between projects and component internals.

## Decision: Enforce TDD with lifecycle contract tests before implementation
- **Rationale**: Aligns with constitution's test-first mandate and protects against regressions where `start` should complete without exposing errors.
- **Alternatives Considered**:
  - *Implement then backfill tests*: rejected; violates constitution and risks incomplete coverage.
