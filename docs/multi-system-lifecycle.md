# Multi-System Lifecycle Guide

The `system` component provides the shared REPL lifecycle helpers used by BAMF and project-specific systems such as Radarr.

## Shared Component

`bamf.system.interface` exposes the following helpers:

- `start` – ensures the requested dev namespace is loaded, stores the selected runtime, and calls `donut.system.repl/start`.
- `stop` / `restart` – delegate to `donut.system.repl`.
- `status`, `runtime-state`, `config` – reuse Donut inspection (`donut.system/describe-system`, `donut.system.repl.state/system`).

Each caller passes:

```clj
{:dev-ns      'bamf.dev.system
 :environment :local}
```

The component handles namespace loading, startup, and status tracking.

## System Namespaces

Each system namespace defines the Donut named systems for that runtime and uses shared Donut graph helpers from the `system` component. BAMF and Radarr both compose:

- an explicit Donut-managed Rama runtime node
- a movies runtime node that depends on `:rama`
- a REST API server node that depends on `:movies/env`

The REPL-facing `user` namespaces stay thin and call `bamf.system.interface/start` directly:

```clj
(ns user
  (:require [bamf.system.interface :as system]))

(defn start []
  (system/start {:environment :local
                 :dev-ns      'bamf.dev.system}))
```

This keeps startup control explicit and removes the older `:go` multimethod indirection.

## Runtime Graph

```text
:config
   │
   ▼
:runtime-state/:rama
   │
   ▼
:runtime-state/:movies/env
   │
   ▼
:runtime-state/:rest-api/server
```

`bamf.movies.interface/start!` now accepts a config map containing at least `:rama` and returns movie-specific handles such as `:movie-depot` plus ownership metadata. Donut owns the Rama runtime itself.

## Usage

```clj
(require 'user)

(user/start)       ;; defaults to :local and returns :ready-to-rock-and-roll
(user/status)      ;; reports for last started environment
(user/runtime-state)
(user/config)
(user/restart)
(user/stop)
```

Radarr exposes the same command surface from its own `user` namespace, but starts `radarr.dev.system` instead of `bamf.dev.system`.
