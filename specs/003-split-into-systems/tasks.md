# Tasks: Split into systems

**Input**: Design documents from `/specs/003-split-into-systems/`
**Prerequisites**: plan.md, contracts/system-lifecycle.md, quickstart.md (pending refresh)

## Phase 3.1: Shared Component (complete)
- [x] T001 Create `components/system/src/clj/bamf/system/interface.clj` with `ensure-ns-loaded`, `current-runtime` atom, and `start` multimethod delegating to the shared `:go` handler.
- [x] T002 Update root `deps.edn` to register `bamf/system` for the `:dev` and `:test` aliases so the component is available in REPL and test profiles.

## Phase 3.2: Project Integration (complete)
- [x] T003 Add `development/src/clj/bamf/dev/system.clj` to define Donut named systems (`:base`, `:local`, `:test`) and supply `http-components` plus Rama IPC test harness.
- [x] T004 Replace helpers in `development/src/clj/user.clj` with a `system/start` defmethod for `:bamf` and thin wrappers that call the shared component.
- [x] T005 Wire Radarr project entry points (`projects/radarr/src/clj/user.clj`, `projects/radarr/src/clj/radarr/dev/system.clj`) to rely on the shared component and expose static routes via `radarr.rest-api.static-routes`.

## Phase 3.3: Documentation & Tooling (partially complete)
- [x] T006 Capture maintainer workflow in `docs/multi-system-lifecycle.md` and ensure README references the shared lifecycle story.
- [ ] T007 Refresh `specs/003-split-into-systems/quickstart.md` to remove nonexistent tests (`bamf.system.lifecycle-test`, `bamf.dev.system-start-flow-test`) and Sonarr references until wiring lands.
- [ ] T008 Trim `specs/003-split-into-systems/data-model.md` to reflect the minimal runtime atom + Donut accessors (remove unused profile/environment helpers).

## Phase 3.4: Follow-ups (open)
- [ ] T009 Add Sonarr lifecycle wiring mirroring Radarr once the project namespace is scaffolded.
- [ ] T010 Backfill automated coverage for the shared component (unit tests for `system/start` dispatch + integration exercise through `user` namespace).

## Dependencies
- T002 ← T001
- T003 ← T001, T002
- T004 ← T003
- T005 ← T003
- T006 ← T004, T005
- T007 ← T006
- T008 ← T006
- T009 ← T005
- T010 ← T004, T005

## Parallel Opportunities
```
# After completing component wiring (T001–T003), doc updates can run while
# integration follow-ups progress:
task-agent run --ids T006,T009,T010
```
