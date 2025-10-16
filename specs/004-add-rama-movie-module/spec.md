# Feature Specification: Rama Movie Module

**Feature Branch**: `004-add-rama-movie-module`  
**Created**: 2025-10-15  
**Status**: Draft  
**Input**: User description:
> Implement the Rama movie module. To store the movie data we need to implement the Rama module and initially one p-state which will hold the data. The data has to match the following sqlite table schema: CREATE TABLE Movies ( Id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Path TEXT NOT NULL, Monitored INTEGER NOT NULL, QualityProfileId INTEGER NOT NULL, MinimumAvailability INTEGER NOT NULL, MovieMetadataId INTEGER NOT NULL, LastSearchTime DATETIME ) The id wil be created with `ModuleUniqueIdPState`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Capture Movies in Rama Catalog (Priority: P1)

A content ingestion operator needs newly discovered movies to be persisted in the Rama movie module so downstream services can rely on a single source of truth.

**Why this priority**: Without reliable persistence the rest of the pipeline cannot plan searches, quality upgrades, or monitoring tasks.

**Independent Test**: Trigger a discovery event with complete movie metadata and confirm the module returns a stored record containing all required fields and a generated unique identifier.

**Acceptance Scenarios**:

1. **Given** a new movie discovery with path, monitored flag, quality profile id, minimum availability, and metadata id, **When** the ingestion pipeline writes the record to the Rama movie module, **Then** the module stores the movie once, assigns a new unique movie id, makes the record immediately available for reads, and emits a `movie-created-event` to downstream subscribers.
2. **Given** a new movie discovery that omits `LastSearchTime` because no searches have run yet, **When** the record is persisted, **Then** the module stores the record with the timestamp unset while preserving all required fields for later updates.

---

### User Story 2 - Maintain Movie Monitoring Settings (Priority: P2)

A library curator needs to adjust `Monitored` status, quality profile, or availability expectations for existing movies without re-importing data.

**Why this priority**: Curators frequently toggle monitoring or adjust quality targets; lack of update support would force manual database edits and introduce drift.

**Independent Test**: Update an existing movie’s monitored flag and quality profile through the module and verify the persisted record reflects the new values without altering other fields.

**Acceptance Scenarios**:

1. **Given** an existing movie record in the Rama module, **When** a curator submits an update to change `Monitored` from active to paused, **Then** the module saves the new monitored status, preserves the movie’s unique id and other attributes, and publishes a `movie-updated-event` that carries the fully persisted movie record.
2. **Given** an existing movie requires a refreshed `LastSearchTime`, **When** the search scheduler records the timestamp after a search completes, **Then** the module overwrites the prior timestamp while keeping other fields intact.

---

### User Story 3 - Provide Movie Data to Dependents (Priority: P3)

A search scheduler or reporting tool needs to retrieve consistent movie records to plan search jobs and produce operational dashboards.

**Why this priority**: Dependent services must rely on the Rama movie module to avoid duplicating storage or serving stale data.

**Independent Test**: Request movie data by id and by file path from the module and confirm the responses include all schema-aligned fields needed by the scheduler.

**Acceptance Scenarios**:

1. **Given** the scheduler requests all monitored movies needing searches, **When** it queries the Rama movie module, **Then** the module returns the correct set of movie records including their last search timestamps for prioritization.
2. **Given** an integration asks for movie details by unique id, **When** the module serves the record, **Then** the caller receives path, monitored flag, quality profile id, minimum availability, metadata id, and the last search time if present.

### Edge Cases

- Duplicate path submissions should resolve to a single stored movie record, preventing parallel entries for the same file location.
- Records missing any required schema field (path, monitored, quality profile id, minimum availability, metadata id) must be rejected with a clear validation outcome so upstream systems can correct the payload.
- Updates where `Monitored` or numeric identifiers are provided as non-numeric strings must be declined to protect data integrity.
- When search activity has never occurred, the module must persist an explicit “no timestamp” state rather than faking a datetime so schedulers can detect movies that still need an initial search.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Rama movie module MUST maintain a persistent movie catalog whose attributes mirror the Movies schema (Id, Path, Monitored, QualityProfileId, MinimumAvailability, MovieMetadataId, LastSearchTime).
- **FR-002**: Each stored movie MUST receive a unique identifier sourced from the shared Module Unique ID provider so records remain globally distinct across BAMF modules.
- **FR-003**: The module MUST accept create requests that include every required field (Path, Monitored, QualityProfileId, MinimumAvailability, MovieMetadataId) and reject submissions missing any required data.
- **FR-004**: The module MUST expose update operations that let authorized services adjust monitored status, quality profile, availability expectation, metadata id, and last search time without recreating the record.
- **FR-005**: The module MUST surface read operations that retrieve movies by unique id, by path, and by monitored status so dependent services can plan searches and reporting.
- **FR-006**: The module MUST store `LastSearchTime` as optional data, retaining an explicit “not yet searched” state when no timestamp is available.
- **FR-007**: The module MUST enforce validation that `Monitored`, `QualityProfileId`, `MinimumAvailability`, and `MovieMetadataId` are numeric values aligned with agreed domain ranges to prevent inconsistent data.
- **FR-008**: The module MUST provide auditable outcomes (success or validation error) for create and update requests so upstream systems can respond without inspecting low-level storage logs.
- **FR-009**: The module MUST rename the existing `movie-saved-event` publication to `movie-created-event` to clearly distinguish creation notifications from the forthcoming `movie-updated-event`.

### Key Entities *(include if feature involves data)*

- **Movie Record**: Canonical representation of a single movie in the BAMF catalog, containing path, monitoring status, quality profile, minimum availability, metadata linkage, optional last search time, and a globally unique movie id.
- **Module Unique ID Provider**: Shared identifier service that guarantees each movie receives a non-colliding id, allowing other Rama components to reference movies consistently.
- **Search Scheduling Profile**: Conceptual view of monitored movies that combines monitoring status and last search timestamps to help schedulers prioritize future search jobs.

## Assumptions

- Upstream ingestion sources translate boolean monitored flags into integer values (1 for monitored, 0 for unmonitored) before sending data to the Rama movie module.
- Dependencies will consume the Rama movie module directly while expecting field naming parity with existing contracts.
- Authorization and auditing of who may create or update movies are handled by surrounding application layers; this feature focuses on state persistence and data integrity.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of newly discovered movies with valid data are persisted in the Rama movie module with all required fields populated and a generated unique id.
- **SC-002**: Downstream schedulers can retrieve newly stored movies within 5 seconds of ingestion, ensuring search planning never waits for manual synchronization.
- **SC-003**: 95% of update attempts (monitoring toggles, quality adjustments, timestamp refreshes) succeed on the first submission without manual data fixes.
- **SC-004**: No data reconciliation incidents are reported between the Rama movie module and dependent services during the first full content refresh cycle after launch.
