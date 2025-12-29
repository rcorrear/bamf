# Phase 0 Research

- Decision: Emit `movie-created-event` and `movie-updated-event` Rama depot messages with `{:event :movie.created|:movie.updated :version 1 :depot ... :payload (map->MovieRow canonical-movie)}` so downstream systems always receive the full Movies schema on every change.
  Rationale: FR-009 mandates renaming the existing `movie-saved-event`; the current depot client (`components/movies/src/clj/bamf/movies/rama_client/depot.clj`) already serializes complete `MovieRow` records, and Constitution P2 requires versioned, auditable events for create/update flows.
  Alternatives considered: Continue emitting `:movie.saved` for both create and update (rejected—violates FR-009 and obscures change semantics); publish only field deltas (rejected—acceptance tests expect full payload parity with the Movies schema).

- Decision: Source movie ids directly inside the Rama MovieModule by invoking ModuleUniqueIdPState during depot processing and returning the generated id via the `foreign-append!` acknowledgement.
  Rationale: The feature spec explicitly calls for ModuleUniqueIdPState to generate identifiers; keeping id assignment in Rama guarantees global uniqueness without leaking `:next-id` plumbing into the HTTP tier.
  Alternatives considered: Derive ids from depot append order (rejected—lacks global uniqueness guarantees across modules); delegate id generation to HTTP handlers (rejected—breaks Polylith component encapsulation and P3).

- Decision: Expand Telemere instrumentation with structured events `:movies/create-invalid`, `:movies/create-duplicate`, `:movies/create-error`, `:movies/create-stored`, plus analogous `:movies/update-*` entries that include outcome status, identifiers, and optional correlation ids.
  Rationale: Constitution P4 requires telemetry for every externally visible action; persistence code already uses `t/log!` with structured payloads, so renaming and extending the event set keeps observability aligned with the new create/update flows while supporting audits of LastSearchTime changes.
  Alternatives considered: Rely solely on Rama event history for observability (rejected—does not capture validation failures or depot connectivity issues); emit unstructured logs (rejected—fails P4's structured telemetry requirement).

- Decision: Inject Rama handles through the system config context supplied to `movies/get-http-api` so HTTP handlers receive an explicit `:movies/env` map instead of relying on a global runtime namespace.
  Rationale: The depot and p-state client namespaces already encapsulate all Rama interaction; passing the env map via component context keeps the interface pure and avoids hidden global state.
  Alternatives considered: Lazily constructing Rama handles per request (rejected—adds latency and complicates shutdown); reintroducing a bespoke runtime namespace (rejected—adds indirection without providing value beyond the existing client helpers).

- Decision: Materialize a dedicated `$$movies-id-by-path` p-state and rewrite the Rama client to query foreign p-states directly via the IPC handle.
  Rationale: Duplicate detection and upcoming read APIs require true path lookups; relying on pre-built lookup closures left the service unable to interrogate Rama. Using `com.rpl.rama/foreign-pstate` + `foreign-select-one` keeps the client consistent with Rama idioms and removes the brittle runtime cache.
  Alternatives considered: Maintaining bespoke lookup closures in a runtime namespace (rejected—duplicated Rama APIs and hid IPC requirements); performing path dedupe in transient service atoms (rejected—does not satisfy persistence/backfill stories).

- Decision: Materialize the Movies Rama p-state with indexes for id, path, metadata id, monitored flag, and target system so ingestion, curator updates, and scheduler reads can query canonical records directly via `inspection.clj`.
  Rationale: FR-005 and the User Story 3 acceptance criteria demand reads by id, path, and monitoring status; existing client helpers (`components/movies/src/clj/bamf/movies/inspection.clj`) assume those indexes exist, and prior research (`docs/save-movies.md`) highlights the need for dedupe-ready indexes.
  Alternatives considered: Querying the depot stream for reads (rejected—inefficient and complicates dedup); persisting to an auxiliary SQL store (rejected—conflicts with Rama-centric persistence strategy and Constitution P2).
