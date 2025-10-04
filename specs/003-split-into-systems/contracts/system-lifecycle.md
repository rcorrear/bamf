# Contract: `system` component lifecycle interface

## Purpose
Expose a shared lifecycle interface that project namespaces (user→bamf default, radarr, sonarr, future systems) can call to manage runtime systems consistently.

## Producer
`system.interface`

## Consumer
Project namespaces such as `projects.dev/system.clj`, `projects.radarr/system.clj`, `projects.sonarr/system.clj`, or REPL tooling.

## Signatures (conceptual)
```clojure
(start  {:system system-key :environment env-key})
=> :ready-to-rock-and-roll ; success keyword returned by shared implementation

(stop)
=> (donut.system.repl/stop)

(restart)
=> (donut.system.repl/restart)

(status)
=> (donut.system/describe-system (donut.system/system env-key))

(runtime-state)
=> (:runtime-state (::donut.system/instances donut.system.repl.state/system))

(config)
=> (:config (::donut.system/instances donut.system.repl.state/system))
```
- `system-key`: Keyword naming the target system entry point (e.g., `:bamf`, `:radarr`).
- `env-key`: Keyword environment forwarded to Donut System (e.g., `:local`, `:test`).

## Dispatch Rules
- `start` is a multimethod whose dispatch value is the `:system` key in the runtime map.
- Each project namespace registers a `defmethod` for its system key. Implementations typically load the project-specific Donut namespace and then re-dispatch by setting `:system :go` (handled by the shared implementation).
- The shared `:go` implementation starts the Donut system, tracks the active runtime atom, and returns `:ready-to-rock-and-roll` on success. Failures raise the original exception after logging the Telemere payload.
- `stop`, `restart`, `status`, `runtime-state`, and `config` are thin wrappers over Donut utilities and do not use multimethod dispatch.

## Requirements
- `start` MUST set the `current-runtime` atom so subsequent `status`/`runtime-state`/`config` calls reflect the latest environment.
- `start` MUST return `:ready-to-rock-and-roll` when Donut reports a successful launch and MUST rethrow `ExceptionInfo` values so callers can react to failures.
- Project-specific `defmethod` bodies MUST ensure their Donut namespaces are loadable (via `ensure-ns-loaded`) before delegating to the shared `:go` handler.
- `stop`, `restart`, `status`, `runtime-state`, and `config` MUST remain side-effect free beyond their delegated Donut calls (i.e., no additional global state or logging is introduced here).

## Observability
- The shared `:go` implementation logs Telemere events at error level when Donut raises an exception during startup. Successful launches do not emit additional telemetry beyond Donut's own instrumentation.
- Callers that require richer telemetry should instrument their project-specific `defmethod` bodies around the delegation point.

## Example Dispatch Registration
```clojure
(ns user
  (:require [bamf.system.interface :as system]))

(defmethod system/start :bamf
  [{:as runtime :keys [environment]}]
  (system/ensure-ns-loaded 'bamf.dev.system)
  (system/start (assoc runtime :system :go :environment environment)))

;; After registering the method, callers can do:
;; (system/start {:system :bamf :environment :local})
;; => :ready-to-rock-and-roll
```
