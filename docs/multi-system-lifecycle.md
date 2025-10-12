# Multi-System Lifecycle Guide

The `system` component mirrors the original `user` namespace helpers so that every interactive system uses the same start/stop/monitoring logic.

## Shared Component

`bamf.system.interface` exposes the following helpers:

- `start` – calls `donut.system.repl/start` after the caller's `:ensure-loaded` thunk runs and stores the selected environment in a provided atom.
- `stop` / `restart` – delegate to `donut.system.repl` after ensuring project namespaces are loaded.
- `status`, `runtime-state`, `config` – reuse Donut inspection (`donut.system/describe-system`, `donut.system.repl.state/system`).

Each caller passes:

```clj
{:ensure-loaded ensure-fn
 :environment  environment-atom}
```

The component handles everything else.

## System Namespaces

Every system namespace (default BAMF in `user`, plus project-specific variants such as `radarr.dev.system` and `sonarr.dev.system`) defines multimethods that dispatch on a `:system/*` keyword and forward to `bamf.system.interface`:

```clj
(def ^:private environment (atom nil))

(defmulti go dispatch-system)

(defmethod go :system/bamf
  ([] (go :local))
  ([env] (system/go {:ensure-loaded ensure-dev-core-loaded
                     :environment environment}
                    env)))
```

Each namespace keeps its own `ensure-*` function that lazily requires the project’s development core, mirroring the original `user` code.

## Usage

```clj
(require 'user)

(user/go)          ;; defaults to :local and returns :ready-to-rock-and-roll
(user/status)      ;; reports for last started environment
(user/status :test)
(user/runtime-state)
(user/config)
(user/restart)
(user/stop)
```

Project namespaces offer the same API (e.g. `(radarr.dev.system/go :test)`), making it straightforward to extend the platform with additional systems.
