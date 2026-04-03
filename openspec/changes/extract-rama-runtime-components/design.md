## Context

BAMF currently mixes three concerns in its development/runtime wiring:

- REPL startup dispatch uses a `:go` defmethod indirection in `bamf.system.interface`.
- Donut graph construction lives inside per-system `*.dev.system` namespaces with substantial duplication between BAMF and Radarr.
- The movies component starts the application's Rama IPC itself, so a domain component owns global runtime creation rather than depending on an explicit Rama node in the system graph.
- Radarr currently references `bamf.movies.interface/start-runtime!` and `stop-runtime!`, while BAMF exposes `start!` and `stop!` at the movies runtime boundary. That mismatch is already present and needs to be normalized as part of this refactor.

That shape was workable for a single Rama-backed component, but it does not scale cleanly. It obscures runtime ownership, encourages raw `:ipc` assumptions across movie client code, and makes it harder to reuse the clearer runtime split already implemented in Omni.

This change is cross-cutting because it affects runtime composition, public startup entrypoints, movies runtime boundaries, and the introduction of new shared components.

## Goals / Non-Goals

**Goals:**
- Introduce `bamf.rama-client` as the neutral application-side Rama runtime/client boundary.
- Introduce `bamf.rama-server` as the server-side helper boundary for module worker code and task-thread integrations.
- Keep `start!` and `stop!` as the canonical movies lifecycle entrypoints and make BAMF/Radarr agree on them.
- Make Rama an explicit Donut-managed dependency that other components can depend on.
- Remove the `:go` startup defmethod indirection in favor of explicit startup functions that load a dev namespace and start a selected Donut system directly.
- Restructure movies runtime wiring so movie module lifecycle depends on a provided Rama runtime and owns only movie-module deployment/connect behavior.
- Reduce BAMF/Radarr startup duplication by moving toward shared graph-building and small overlays.

**Non-Goals:**
- Adding full cluster deployment or a `:containerized` BAMF system in this change.
- Migrating every future Rama-backed component in one pass beyond the current movies runtime path.
- Introducing a framework-agnostic runtime abstraction above Donut.
- Reworking movie domain behavior, HTTP contracts, or persistence semantics unrelated to runtime ownership.

## Decisions

### Decision: Introduce a neutral `bamf.rama-client` boundary

Create a dedicated component whose job is to wrap local IPC and future remote/cluster connections behind a neutral runtime handle. It will expose helpers analogous to Omni's `rama-client`: `handle`, `owns-runtime?`, `close!`, and `foreign-*` operations.

Rationale:
- Removes raw `:ipc` assumptions from downstream code.
- Separates runtime ownership from component behavior.
- Leaves room for future runtime modes without rewriting every caller.

Alternatives considered:
- Keep Rama helpers under `bamf.movies.rama.client`: rejected because it makes a shared runtime concern look movie-specific.
- Pass raw IPC everywhere: rejected because it hard-codes one runtime mode and leaks transport details through the codebase.

### Decision: Introduce `bamf.rama-server` for worker-side integrations

Add a dedicated server-side component for module/task-thread helpers that belong inside Rama worker execution contexts. BAMF does not currently have a generic helper to migrate out of `bamf.movies.rama.module.*`, so the initial implementation is to establish the Polylith component and public namespace as the canonical worker-side boundary, mirroring Omni's split.

Rationale:
- Matches the split already proven in Omni.
- Prevents worker-side utilities from being buried in a specific module namespace.
- Gives future cross-module task-thread integrations a stable home.

Alternatives considered:
- Delay `bamf.rama-server` until BAMF has more worker-side helpers: rejected because the user explicitly wants the same boundary split as Omni, and creating it now keeps responsibilities clear.
- Keep worker-side helpers inside `bamf.movies.rama.module.*`: rejected because those helpers are infrastructure concerns, not movie-domain behavior.

### Decision: Keep `start!` / `stop!` as the canonical movies lifecycle names

Keep `bamf.movies.interface/start!` and `bamf.movies.interface/stop!` as the canonical lifecycle names for Donut/runtime composition. Radarr system code should be updated to call those names instead of the non-existent `start-runtime!` / `stop-runtime!` variants.

Rationale:
- Fixes the current BAMF/Radarr mismatch directly.
- Matches the user's preference and the long-term direction where runtime management lives in Donut composition instead of in specially named domain lifecycle APIs.
- Avoids renaming churn on a public component boundary that already has working callers in BAMF.

Alternatives considered:
- Rename the interface to `start-runtime!` / `stop-runtime!`: rejected because the extra naming does not add enough value if Donut becomes the primary runtime owner.
- Add different names in BAMF and Radarr: rejected because the refactor is trying to eliminate that divergence.

### Decision: Make Rama an explicit Donut node and dependency

The Donut graph should gain an explicit Rama runtime node, and movies runtime nodes should depend on it through `ds/ref` rather than creating IPC internally.

Rationale:
- Makes runtime ownership visible in the system graph.
- Allows multiple components to depend on the same runtime.
- Clarifies which layer owns app runtime creation and shutdown.

Alternatives considered:
- Keep Rama startup hidden inside `movies/start!`: rejected because it conflates domain lifecycle with system runtime ownership.
- Store a Rama handle under generic config/runtime maps without a graph node: rejected because it weakens dependency visibility and testability.

### Decision: Move startup control to explicit functions, not `:go` dispatch

Replace the current startup chain of per-system defmethods plus a `:go` defmethod with explicit functions that:
1. load the relevant dev namespace,
2. select the named Donut system,
3. start it directly through `donut.system.repl`.

This decision explicitly includes removing the `bamf.system.interface/start` multimethod and the `:go` defmethod path as the control mechanism for startup.

Rationale:
- Startup is an operation, not a system identity.
- The control flow becomes easier to trace and test.
- It reduces the amount of hidden behavior registered through global multimethod dispatch.

Alternatives considered:
- Keep the existing multimethod design and only rename `:go`: rejected because the indirection remains.
- Eliminate all shared startup code and let each `user` namespace manage itself: rejected because BAMF still benefits from a shared runtime facade.

### Decision: Keep movie module ownership separate from runtime ownership

The movies runtime boundary should accept a config map containing at least `:rama` and optional launch/runtime settings, and return movie-specific handles such as the movie depot and module ownership metadata. Runtime ownership (`owns-runtime?`) and module ownership (`owns-module?`) are separate concerns and should remain modeled separately.

Rationale:
- A component may deploy/update a module without owning the global runtime.
- Future runtime modes can attach to an already-running environment while preserving movie behavior.
- It avoids the current mixed env shape that alternates between `:ipc`, nested `:movies/env`, and `:runtime-state`.

Alternatives considered:
- Collapse runtime and module ownership into one env map without explicit semantics: rejected because it hides lifecycle intent and complicates shutdown behavior.

### Decision: Use dedicated dev/runtime helper namespaces now

Introduce dedicated dev/runtime helper namespaces for explicit graph dependencies as part of the refactor, including a `bamf.dev.system.rama` helper namespace. This keeps Donut nodes thin and avoids rebuilding the same inline runtime lambdas in each system namespace.

Rationale:
- Resolves the current open question instead of leaving the shape implicit.
- Supports shared composition between BAMF and Radarr.
- Keeps the Donut graph readable as explicit runtime boundaries multiply.

Alternatives considered:
- Keep Rama node construction inline until a second runtime mode exists: rejected because the graph refactor already creates enough shared runtime structure to justify a dedicated helper namespace now.

### Decision: Do not preserve raw `:ipc` env fallback compatibility in production code

The refactor should update internal callers and tests to the new neutral runtime/env shape rather than keeping the current nested raw `:ipc` fallback chain in production movie client code. Temporary compatibility shims are acceptable only where needed to keep the migration incremental inside the same change.

Rationale:
- The current fallback chain is part of the problem this change is trying to remove.
- Preserving it indefinitely would weaken the new runtime boundary.

Alternatives considered:
- Preserve all old env shapes permanently: rejected because it keeps the old implicit contract alive.

### Decision: Defer config-report and diagnostics split to a follow-up change

This change remains focused on runtime ownership, startup control, and component boundaries. Adopting Omni's config-report/diagnostics split is explicitly deferred.

Rationale:
- Prevents this refactor from expanding into a broader startup-observability change.
- Keeps the current implementation target aligned with the user's immediate goals.

## Risks / Trade-offs

- [Risk] Existing movie helpers and tests assume raw `:ipc` or nested `:movies/env` shapes.
  Mitigation: Migrate callers to the neutral Rama runtime incrementally and add compatibility-focused tests around the new client boundary.

- [Risk] BAMF and Radarr may keep drifting if graph-building is only partially shared.
  Mitigation: Extract shared composition helpers in the same change and keep per-system overlays limited to route/component registration differences.

- [Risk] Introducing `bamf.rama-server` now may feel premature if BAMF has limited current worker-side usage.
  Mitigation: Keep the initial surface area intentionally small and infrastructure-focused.

- [Risk] Startup refactors can break REPL ergonomics.
  Mitigation: Preserve the current user-facing commands (`start`, `stop`, `restart`, `status`, `config`) while simplifying the internal control flow.

## Migration Plan

1. Add `bamf.rama-client` and move neutral Rama handle/client helpers there.
2. Add `bamf.rama-server` and establish the worker-side boundary component and public namespace.
3. Update Radarr and shared callers to use the canonical `bamf.movies.interface/start!` and `stop!` names.
4. Refactor movies runtime code to accept a provided Rama runtime and construct movie-specific handles from it.
5. Update Donut graph composition so Rama is an explicit dependency node and movies depend on it.
6. Replace the `bamf.system.interface` startup multimethod and `:go` dispatch with explicit startup functions while preserving existing REPL command names.
7. Update BAMF and Radarr wiring to use the shared composition approach and verify tests.

Rollback strategy:
- Revert to the previous `movies`-owned IPC startup path and the old startup facade if runtime composition regressions appear before the change is adopted broadly.

## Open Questions

- None at this stage.
