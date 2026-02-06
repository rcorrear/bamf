# Phase 1 Quickstart

## Prerequisites
- Active branch: `004-add-rama-movie-module`.
- Clojure CLI installed; run commands from `/home/rcorrear/Projects/clojure/bamf`.
- Rama module compiled and accessible via development IPC (`com.rpl.rama.test/create-ipc` or deployed module).
- Optional: `httpie` or `curl` for manual API exercises.

## Workflow
1. **Run component-focused tests**
   ```bash
   clojure -X:test :nses '[bamf.movies.model-test bamf.movies.persistence-test bamf.movies.interface-test bamf.movies.http-test]'
   ```
   Validates Malli schema, persistence behaviours (duplicates, validation, depot integration), public interface wiring, and HTTP translations.
2. **Construct a Rama env for manual testing**
   ```clj
   (require '[com.rpl.rama :as rama]
            '[com.rpl.rama.test :as rt]
            '[bamf.movies.rama.common :as common])

   (def ipc (rt/create-ipc {:module common/module-name}))

   (def movies-env
     {:ipc         ipc
      :movie-depot (rama/foreign-depot ipc common/module-name common/movie-depot-name)})
   ```
   Pass `movies-env` to `persistence/save!` or through the HTTP context (`:movies/env`) when wiring routes so that depot writes and p-state lookups share the same IPC handle.
3. **Create a movie via HTTP**
   ```bash
   http POST :9090/api/v3/movie \
     title=Dune \
     path="/movies/Dune (2021)" \
     rootFolderPath=/movies \
     monitored:=true \
     qualityProfileId:=1 \
     minimumAvailability=released \
     tmdbId:=438631
   ```
   Expect `201` with a payload containing the generated `id` and timestamps. Rama should emit a `movie-created-event`.
4. **Retrieve monitored backlog**
   ```bash
   http GET :9090/api/v3/movie monitored=true
   ```
   Confirms list filtering respects the `monitored` index and optional `lastSearchTime` logic.
5. **Fetch a single movie**
   ```bash
   http GET :9090/api/v3/movie/<<MOVIE_ID>>
   ```
   Expect `200` with `data` payload when present or `404` if the id is unknown.
6. **Full regression**
   ```bash
   clojure -X:test
   ```
   Runs every suite across bases/components/projects before merge.

## Observability Checklist
- Telemere should log `:movies/create-*` and `:movies/update-*` events with movie ids, reasons for validation failures, and depot status codes.
- Rama depot stream must contain versioned `:movie.created` and `:movie.updated` events; inspect with Rama tooling if available. (Updates emit when invoked via component `update-movie!`; HTTP surface is create + read only.)
- Duplicate submissions (same `tmdbId` or `path`) must yield HTTP 409 and log `:movies/create-duplicate`.
- Missing Rama env (specifically `:ipc` or `:movie-depot`) raises explicit errors during persistence, preventing silent id reuse.

## Rollback Plan
- Revert to the legacy `movie-saved-event` behaviour by restoring the previous depot client and limiting the module to create-only persistence if update workflows fail.
- Keep HTTP surface at create/read only; updates are exercised via component/repl until Rama schema or contract issues are resolved.
