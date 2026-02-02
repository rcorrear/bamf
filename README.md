# BAMF — Batch Automated Media Fetcher

BAMF is an automated media fetcher for BitTorrent workflows. It watches Jackett or Prowlarr feeds for new releases and persists Radarr-compatible movie metadata so automation tooling knows what to download next.

![BAMF Logo](./docs/logo.png)

> **Heads up** — This is a hobby project and a learning vehicle, not production software. It is heavily LLM-assisted (most of the code was generated with the help of AI coding tools) and very much a work in progress — many features are incomplete or missing entirely. The primary goal is to explore technologies that interest me: **Clojure**, **Polylith**, and **Rama**. Use at your own curiosity.

## What's Here Today
- A composable REST surface built from Polylith components and aggregated at runtime with Reitit.
- Donut System driven lifecycles so development, test, and production environments share the same start/stop/status tooling.
- Telemetry hooks (Telemere) around persistence and HTTP paths to keep future observability consistent.

## Architecture
- **Polylith** keeps bases and components isolated.
- **Rama** provides the durable media catalog.
- **Reitit + Aleph** expose the HTTP API.
- **Donut System** orchestrates lifecycles.

```
bases/
  rest-api/          # Aleph/Reitit HTTP base (route aggregation, server startup)
components/
  api/               # Public interface facade for other components/bases
  config/            # Aero-backed configuration loader + validation
  movies/            # Rama persistence, HTTP handlers, specs
  system/            # Donut lifecycle helpers shared by user-facing systems
development/         # REPL-only helpers and named Donut systems
docs/                # Architecture notes and feature briefs
specs/               # Feature plans and research notes
```

## Getting Started
1. Install the [Clojure CLI](https://clojure.org/guides/getting_started) (version 1.12+ required).
2. Clone the repository and jump into a REPL with `clj -M:dev`.
3. Boot the local system by evaluating `(user/start)`; this loads the Donut `:local` system, starts the Rama IPC test harness, and exposes the REST API on port `9090` (see `components/config/src/resources/config-local.edn`).
4. Stop or restart the environment with `(user/stop)` / `(user/restart)`. Status helpers such as `(user/status)` and `(user/runtime-state)` mirror the Donut tooling in `components/system`.

### Running Tests
- Full suite: `clojure -M:test`
- Specific namespace: `clojure -M:test --focus bamf.rest-api.routes-test`
- Specific test: `clojure -M:test --focus bamf.rest-api.routes-test/some-test`
- Unit tests only: `clojure -M:test --focus :unit`
- Integration tests only: `clojure -M:test --focus :integration`

## Configuration
- Defaults live under `components/config/src/resources`. Configuration files are selected by environment (`config-local.edn`, `config-test.edn`, etc.).
- Extend the HTTP surface by updating `bamf.dev.system/http-components` with a new `:component/http-api` entry so Donut knows which Polylith components to pull routes from.

## Polylith References
- [High-level Polylith guide](https://polylith.gitbook.io/polylith)
- [Poly tool docs](https://cljdoc.org/d/polylith/clj-poly/CURRENT)

The Polylith team hangs out in the [#polylith Slack channel](https://clojurians.slack.com/archives/C013B7MQHJQ).

## License

See `LICENSE` for details.
