# Phase 1 Quickstart

## Prerequisites
- Clojure CLI tools installed (matches workspace `deps.edn`).
- Rama services available if route handlers exercise persistence (movies component already integrates).
- Active feature branch: `002-modular-apis`.

## Workflow
1. **Run failing contract tests**
   ```bash
   clojure -X:test :nses '[bamf.rest-api.routes-test]'
   ```
   Tests fail until the new aggregation logic and component exports are implemented.
2. **Implement component route export**
   - Add `get-http-api` to `components.movies.interface` returning Reitit route data.
   - Ensure handlers delegate persistence to Rama modules and return JSON.
3. **Wire aggregation in REST base**
   - During dependency graph assembly, inject component APIs through `bamf.dev.core/http-components` so the rest-api base receives `movies/get-http-api` at startup.
   - Keep the `http-components` map in code (`development/src/clj/bamf/dev/core.clj`) authoritative for which bricks expose HTTP routes.
4. **Verify diagnostics**
   - Remove the movies entry from the `http-components` map and re-run `clojure -X:test :nses '[bamf.rest-api.routes-test]'` to confirm a `:rest-api/missing-http-api` warning.
   - Introduce a duplicate descriptor temporarily in the map and confirm both a `:rest-api/duplicate-route` warning and a synthesized `:rest-api/route-diagnostics` log with environment/runtime metadata.
5. **Run full suite**
   ```bash
   clojure -X:test
   ```
   Ensure all new and existing tests pass after implementation.

## Observability Checklist
- Diagnostic logs tagged with `:environment` and `:runtime-state-keys` appear for missing routes, duplicates, and invalid route declarations.
- `bamf.rest-api.routes/diagnose-routes` events surface as `:rest-api/route-diagnostics` with action (`:warn`/`:error`).
- Malli validation errors reported with HTTP 400 status and JSON bodies.
- Route metadata exposed through the REST API's OpenAPI generator.

## Rollback Plan
- Remove the movies entry from `http-components` (or provide an empty vector) to fall back to the previous static router definition if unexpected behavior occurs.
- Keep Movies component's previous router configuration accessible until post-release validation succeeds.
