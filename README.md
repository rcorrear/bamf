# BAMF

BAMF is an automated media fetcher for BitTorrent workflows. It watches Jackett or Prowlarr feeds for new releases and persists Radarr-compatible movie metadata so automation tooling knows what to download next.

![BAMF Logo](./docs/logo.png)

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
- Full suite: `clojure -X:test`
- Route-specific checks: `clojure -X:test :nses '[bamf.rest-api.routes-test bamf.rest-api.routes-integration-test]'`

## HTTP API
- **Movies** - `POST /api/v3/movie` accepts Radarr-style payloads, normalizes tags/timestamps, rejects duplicates (`tmdbId` or `path`), and returns the stored record. A placeholder `GET /api/v3/movie` endpoint exists while the listing query is implemented (`components/movies/src/clj/bamf/movies/http.clj`).
- Component routes are registered via `get-http-api` on each component's interface namespace and composed by `bamf.rest-api.routes/aggregate`.

## Configuration
- Defaults live under `components/config/src/resources`. Configuration files are selected by environment (`config-local.edn`, `config-test.edn`, etc.).
- Extend the HTTP surface by updating `bamf.dev.system/http-components` with a new `:component/http-api` entry so Donut knows which Polylith components to pull routes from.

## Polylith References
- [High-level Polylith guide](https://polylith.gitbook.io/polylith)
- [Poly tool docs](https://cljdoc.org/d/polylith/clj-poly/CURRENT)

The Polylith team hangs out in the [#polylith Slack channel](https://clojurians.slack.com/archives/C013B7MQHJQ).

## License

See `LICENSE` for details.
