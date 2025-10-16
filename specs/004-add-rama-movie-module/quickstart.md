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
   http POST :3000/api/v3/movie \
     title="Dune" \
     path="/movies/Dune (2021)" \
     rootFolderPath="/movies" \
     monitored:=true \
     qualityProfileId:=1 \
     minimumAvailability="released" \
     tmdbId:=438631
   ```
   Expect `201` with a payload containing the generated `id` and timestamps. Rama should emit a `movie-created-event`.
4. **Update monitored status and last search time**
   ```bash
   http PATCH :3000/api/v3/movie/<<MOVIE_ID>> \
     id:=<<MOVIE_ID>> \
     monitored:=false \
     lastSearchTime="2025-10-10T17:00:00Z"
   ```
   Expect `200` and a `movie-updated-event` carrying the latest record.
5. **Retrieve monitored backlog**
   ```bash
   http GET :3000/api/v3/movie monitored==true
   ```
   Confirms list filtering respects the `monitored` index and optional `lastSearchTime` logic.
6. **Full regression**
   ```bash
   clojure -X:test
   ```
   Runs every suite across bases/components/projects before merge.

## Observability Checklist
- Telemere should log `:movies/create-*` and `:movies/update-*` events with movie ids, reasons for validation failures, and depot status codes.
- Rama depot stream must contain versioned `:movie.created` and `:movie.updated` events; inspect with Rama tooling if available.
- Duplicate submissions (same `movieMetadataId` or `path`) must yield HTTP 409 and log `:movies/create-duplicate`.
- Missing Rama env (specifically `:ipc` or `:movie-depot`) raises explicit errors during persistence, preventing silent id reuse.

## Rollback Plan
- Revert to the legacy `movie-saved-event` behaviour by restoring the previous depot client and limiting the module to create-only persistence if update workflows fail.
- Disable the PATCH endpoint at the router level (`bases/rest-api/src/clj/bamf/rest_api/routes.clj`) and keep the Rama module running in create-only mode until fixes deploy.
