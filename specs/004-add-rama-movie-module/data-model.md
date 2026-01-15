# Phase 1 Data Model

## MovieRecord
- **id** (`long`, required): Globally unique movie identifier created by ModuleUniqueIdPState.
- **path** (`string`, required): Absolute filesystem path; normalized (trimmed) and unique across records.
- **monitored** (`boolean`, required): Operational flag for scheduler; treated as integer `1/0` for backwards compatibility when exposed to Rama.
- **qualityProfileId** (`long`, required): Radarr-aligned profile identifier.
- **minimumAvailability** (`enum`, required): One of `announced`, `inCinemas`, `released`, `tba`.
- **lastSearchTime** (`instant`, optional): ISO-8601 UTC timestamp; `null` represents "never searched".
- **added** (`instant`, required): First persist timestamp, also used as default for `lastSearchTime`.
- **targetSystem** (`string`, optional): Lower-cased routing target (`radarr` default).
- **title/titleSlug/rootFolderPath/tmdbId/movieFileId/addOptions/tags/year**: Mirrored from Radarr schema per `components/movies/model.clj` canonicalization.

**Relationships & Constraints**
- Unique indexes on `tmdbId` and `path`; duplicates rejected at persistence boundary.
- `monitored` + `lastSearchTime` drive search scheduling (User Story 3).
- Records emitted on every create/update event to keep downstream caches in sync.

## MoviesPState
- **Primary storage**: Rama p-state materializing `MovieRecord` rows.
- **Indexes**:
  - `by-id`: lookup for read-by-id flows (`inspection.clj`).
  - `by-path`: dedupe + read by filesystem path.
  - `by-tmdb-id`: dedupe + metadata joins.
  - `by-monitored`: selection for scheduler queries.
  - `by-target-system`: routing for multi-system support.
- **State transitions**:
  - `movie-created-event` → insert canonical record.
  - `movie-updated-event` → merge changed fields, preserve `id` and history.

## ModuleUniqueIdProvider
- **Source**: ModuleUniqueIdPState declared inside the Rama MovieModule topology.
- **Responsibility**: Generate monotonic `long` ids during depot processing and embed them in the acknowledgement message returned to the service layer.
- **Integration**: The `foreign-append!` `:ack` map now contains `{:status ... :movie {:id <value>}}`, allowing HTTP persistence to merge the id into the normalized payload after Rama confirms the write.

## MovieEvents
- **Event Envelope**:
  - `:event` → `:movie.created` | `:movie.updated`.
  - `:version` → `1`.
  - `:depot` → Rama depot handle `*movie-saves-depot`.
  - `:payload` → Serialized `MovieRecord` (stored as `MovieRow` record).
- **Semantics**:
  - `movie.created` published once per new record; triggers inserts + downstream fan-out.
  - `movie.updated` published on monitored/profile/availability/metadata/lastSearchTime changes; emits full record for idempotent consumers.

## UpdateCommand
- **Input shape**: Partial map containing `id` and any mutable fields (`monitored`, `qualityProfileId`, `minimumAvailability`, `lastSearchTime`).
- **Validation**: Requires positive `id`; numeric fields remain positive integers; `lastSearchTime` accepts ISO string or `null`.
- **State transition**: Applies to MoviesPState entry identified by `id`; emits `movie.updated-event`.

## Queries
- **get-by-id**: returns canonical `MovieRecord`; leverages `by-id` index.
- **get-by-path**: accepts normalized path; used for ingestion dedupe and path-based reads.
- **list-monitored**: filters `monitored=true`; optionally time-slices by `lastSearchTime` for scheduler.
- **list-by-target-system**: supports multi-system routing (Radarr/Sonarr) as future extension.
