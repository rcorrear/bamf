# Phase 1 Quickstart

## Prerequisites
- Active branch: `003-split-into-systems`.
- Clojure CLI installed; run commands from workspace root.
- Optional: Radarr/Sonarr project namespaces available when exercising their lifecycle helpers.

## Workflow
1. **Run targeted tests**
   ```bash
   clojure -X:test :nses '[bamf.system.lifecycle-test bamf.dev.system-start-flow-test]'
   ```
   Confirms the shared component mirrors the original `user` behaviour and that `user` delegates via the new multimethods.
2. **Use the shared component via system namespaces**
   ```clj
   (require 'user)

   (user/start)
   (user/status)
   (user/status :test)
   (user/runtime-state)
   (user/config)
   (user/restart)
   (user/stop)
   ```
   Radarr and Sonarr expose the same API under `radarr.dev.system` and `sonarr.dev.system` (Sonarr to be implemented at a later date). Each namespace keeps its own environment atom and ensure function but forwards to `bamf.system.interface`.
3. **Add new systems**
   - Define a namespace that mirrors the existing pattern: create an environment atom, an `ensure-*` function, and multimethods (`go`, `stop`, `restart`, `status`, `runtime-state`, `config`) with dispatch keys such as `:system/<name>`.
   - Call the shared component functions with `{:ensure-loaded ensure-fn :environment env-atom}`.
4. **Full regression**
   ```bash
   clojure -X:test
   ```
   Ensures all suites (bases/components/projects) remain green.

## Observability Checklist
- `ensure-*` functions must log or rethrow meaningful errors when required namespaces are missing.
- Environments are stored in per-system atoms, so `status` without an explicit argument always reflects the last successful `go` call.
- `runtime-state` and `config` still read from `donut.system.repl.state/system`, matching previous behaviour.

## Rollback Plan
- Replace multimethod calls with the original inline helpers in `user` if the shared component causes issues.
- Additional systems (radarr, sonarr) can simply stop calling into the shared component while the default BAMF system continues operating.
