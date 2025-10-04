# Phase 1 Data Model

## SystemLifecycleComponent
- **Identifier**: Polylith component keyword `:components/system`.
- **Lifecycle Functions**: Multimethods `start`, `stop`, `restart`, `status`, `runtime-state`, `config` dispatched on `[system environment]`.
- **Dependencies**: Donut-party.system graph inputs (configs, Rama connections, Telemere logger).
- **Responsibilities**: Provide lifecycle orchestration while remaining stateless aside from inputs.

## NamedSystemProfile
- **system-key** (`keyword`): Identifier such as `:system/bamf`, `:system/radarr`, `:system/sonarr`.
- **environment** (`keyword`): Environment value (e.g., `:dev`, `:test`, `:prod`).
- **lifecycle-handlers** (`map`): Functions or data required by lifecycle multimethods (start, stop, restart).
- **monitoring-hooks** (`map`): References to data sources powering `status`, `runtime-state`, and `config` outputs.

## EnvironmentContext
- **name** (`keyword`): Environment identifier used in dispatch key.
- **config** (`map`): Configuration merged from project settings, secrets, and runtime overrides.
- **instrumentation** (`map`): Telemere logger parameters and diagnostics thresholds.

## LifecycleOutcome
- **system-key** (`keyword`): System acted upon.
- **environment** (`keyword`): Environment used during lifecycle action.
- **result** (`enum`): `:ok`, `:error`.
- **details** (`map`): Structured payload for success confirmation or error diagnostic; must be empty for successful `start` operations to honor "no exposed errors" requirement.
- **telemetry** (`vector`): List of emitted Telemere events for auditing.

## MonitoringView
- **status** (`map`): High-level readiness indicators returned to maintainers from `status`.
- **runtime-state** (`map`): Operational metrics such as running processes, active jobs, or connection pools.
- **config** (`map`): Effective configuration snapshot for the system/environment combination.
