# Feature Specification: Add metadata from the extra data in the save request

**Feature Branch**: `[005-save-request-metadata]`  
**Created**: 2026-01-02  
**Status**: Draft  
**Input**: User description: "Add metadata from the extra data in the save request that"

## Clarifications

### Session 2026-01-07

- Q: Where are metadata fields stored in the saved record? → A: Persist metadata in the separate Rama PState `metadata-by-movie-id` keyed by movie id; the HTTP request/response still exposes metadata merged into the movie record.
- Q: Which metadata keys are stored from extra data? → A: Store fields that correspond to MovieMetadata columns: images, genres, sortTitle, cleanTitle, originalTitle, cleanOriginalTitle, originalLanguage, status, lastInfoSync, runtime, inCinemas, physicalRelease, digitalRelease, year, secondaryYear, ratings, recommendations, certification, youTubeTrailerId, studio, overview, website, popularity, collection (tmdbId/title).
- Q: What is the metadata size limit? → A: No explicit size limit is defined for this iteration; oversized payload handling is out of scope.
- Q: Do extra data keys need to match metadata names exactly? → A: Yes, keys must match the MovieMetadata field names (Radarr payload keys) exactly.

### Session 2026-01-13

- Q: How should the system handle metadata fields explicitly set to `null` in the save request? → A: Treat `null` as an explicit removal of that metadata key; there are no required metadata fields, and tmdbId/imdbId/title are not part of metadata.
- Q: How should HTTP responses represent metadata keys that are not stored (never provided or removed via `null`)? → A: Omit those metadata keys from the response entirely.

### Session 2026-01-14

- Q: When a save/update results in no metadata, how should `metadata-by-movie-id` be stored? → A: Delete/omit the entry entirely (no row).
- Q: When persisting metadata, should storage be sparse or include all keys? → A: Store a sparse map with only provided non-null keys.
- Q: After normalization, how should status be stored and serialized? → A: Store a namespaced keyword internally and serialize to canonical string tokens for HTTP compatibility.
- Q: What canonical status strings should HTTP return? → A: `deleted`, `tba`, `announced`, `inCinemas`, `released`.
- Q: What HTTP status should validation failures return? → A: 422 Unprocessable Entity.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Save request captures metadata (Priority: P1)

Client submits a save request that includes MovieMetadata fields (for example, images, genres, status, ratings) and expects them to be captured and visible immediately after save.

**Why this priority**: It preserves provenance and unlocks downstream reporting and auditing.

**Independent Test**: Submit a save request with metadata fields, then read back the saved record and confirm metadata matches submitted values without additional dependencies.

**Acceptance Scenarios**:

1. **Given** a valid save request with metadata fields including images, genres, and status, **When** the save is processed, **Then** the saved record includes those fields.
2. **Given** a valid save request without metadata fields, **When** the save is processed, **Then** the record saves successfully and metadata remains empty/default without errors.

---

### User Story 2 - Metadata updates on PUT only (Priority: P2)

A client updates an existing record via PUT with updated MovieMetadata fields and expects metadata to reflect the new values while keeping unspecified metadata unchanged. Repeated POST create requests should not alter metadata.

**Why this priority**: It keeps metadata accurate over time as sources change without forcing full re-ingestion.

**Independent Test**: Save a record with metadata, update it via PUT with a subset of changed metadata, and verify changed fields update while untouched fields persist.

**Acceptance Scenarios**:

1. **Given** an existing record with metadata, **When** a PUT request provides new values for some metadata keys, **Then** those keys reflect the new values and other metadata remains as previously stored.
2. **Given** an existing record with metadata, **When** a PUT request omits all metadata, **Then** previously stored metadata remains and the update still succeeds.
3. **Given** an existing record with metadata, **When** a POST create request repeats the same movie payload, **Then** metadata remains unchanged and the request is treated as a duplicate without mutating stored metadata.

---

### User Story 4 - HTTP validation and handling for metadata (Priority: P3)

A client submits save or update requests containing metadata and expects HTTP validation plus response handling to reflect stored metadata and reject invalid payloads with actionable errors.

**Why this priority**: It keeps the HTTP API consistent and safe once metadata persistence is in place, without disturbing current behavior until this story is delivered.

**Independent Test**: Send malformed metadata (wrong types or unsupported status), observe a validation response that blocks persistence and cites the offending field.

**Acceptance Scenarios**:

1. **Given** a valid save request with metadata fields, **When** the request is processed, **Then** the create/update response includes the submitted metadata values and list/get responses include stored metadata keys (omitting keys that are not stored).
2. **Given** metadata contains disallowed value types or unsupported status values, **When** the save is attempted, **Then** the request is rejected with a message naming the invalid fields and no partial metadata is stored.
3. **Given** metadata contains unknown keys, **When** the save is processed, **Then** unknown keys are ignored while recognized keys are validated and persisted.

### Edge Cases

- Requests with unknown metadata keys should ignore those keys without failing the save while preserving known metadata.
- Save requests that omit metadata fields should behave like saves without metadata.
- Requests that include metadata keys with `null` values should remove those keys from stored metadata.
- Requests that result in an empty metadata map should remove the `metadata-by-movie-id` entry and return responses with no metadata keys.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept optional metadata fields in the save request payload corresponding to MovieMetadata columns and expose them in HTTP responses merged into the movie record.
- **FR-001a**: The system MUST omit metadata keys from HTTP responses when they are not stored (never provided or explicitly removed).
- **FR-002**: The system MUST persist submitted metadata fields in the separate Rama PState `metadata-by-movie-id` (movie-id → metadata row) and return them in the save confirmation or response and subsequent retrievals.
- **FR-003**: The system MUST validate metadata shape for recognized keys before saving, enforcing a valid key set (MovieMetadata fields: images, genres, sortTitle, cleanTitle, originalTitle, cleanOriginalTitle, originalLanguage, status, lastInfoSync, runtime, inCinemas, physicalRelease, digitalRelease, year, secondaryYear, ratings, recommendations, certification, youTubeTrailerId, studio, overview, website, popularity, collection) and value types (string, number, list, or map as used by the Radarr payload). Unknown keys are ignored per FR-007.
- **FR-004**: The system MUST reject a save request with a clear message when metadata violates validation rules, leaving the record and metadata unchanged.
- **FR-005**: The system MUST allow saves without metadata to proceed.
- **FR-006**: The system MUST update metadata only on PUT requests by replacing values for keys supplied in the latest request while leaving unspecified keys unchanged.
- **FR-007**: The system MUST ignore unknown metadata keys safely while continuing to process and store recognized metadata fields.
- **FR-008**: The system MUST provide a consistent mapping between incoming MovieMetadata fields and stored metadata names (1:1 with Radarr payload keys; collection values map from collection.tmdbId/title).
- **FR-009**: The system MUST map incoming status to the defined status enum (Deleted=-1, TBA=0, Announced=1, InCinemas=2, Released=3) in a case-insensitive way (for example, `tba`, `announced`, `inCinemas`, `released`) and reject saves with unsupported status values; status is stored internally as a namespaced keyword and serialized to canonical string tokens in HTTP responses (`deleted`, `tba`, `announced`, `inCinemas`, `released`).
- **FR-009a**: The system MUST return HTTP 422 for metadata validation failures.
- **FR-010**: The system MUST treat metadata keys set to `null` as explicit removals and delete those keys from stored metadata.
- **FR-011**: The system MUST NOT modify metadata on duplicate or repeated POST create requests; metadata changes are accepted only via PUT updates.
- **FR-012**: The system MUST require exact key matches for metadata fields; no aliases or case-insensitive variants are accepted.
- **FR-013**: The system MUST accept camelCase request keys for metadata fields and validate them in the HTTP create/update schemas before persistence.
- **FR-014**: When metadata is empty after processing (no keys provided or all keys removed), the system MUST delete/omit the `metadata-by-movie-id` entry; missing entries are treated as empty metadata for reads and responses.
- **FR-015**: The system MUST store metadata as a sparse map containing only provided non-null keys; omitted keys are not stored.

### Non-Functional Requirements

- **NFR-001**: Metadata save/update handling MUST be implemented as a Rama op (`def/ramaop`) within the existing event flow (movie-created/movie-updated ETL).
- **NFR-002**: The system MUST emit Telemere `print-event` logs for `hashing-by-movie-id` and `saving-metadata`.
- **NFR-003**: Any change that can modify system behavior MUST be validated by running automated tests after the implementation tasks complete (minimum: `clojure -X:test`).

### Metadata Field Types (request validation)

All metadata keys are optional, but when present must match the expected request type. Structured fields are accepted as JSON objects/arrays and serialized for storage as needed.

| Field                | Type            | Notes                                           |
| :------------------- | :-------------- | :---------------------------------------------- |
| `images`             | `array<object>` | `[{coverType, remoteUrl, url}]` as in fixtures. |
| `genres`             | `array<string>` |                                                 |
| `sortTitle`          | `string`        |                                                 |
| `cleanTitle`         | `string`        |                                                 |
| `originalTitle`      | `string`        |                                                 |
| `cleanOriginalTitle` | `string`        |                                                 |
| `originalLanguage`   | `object`        | `{id: integer, name: string}`.                  |
| `status`             | `string`        | Normalized to enum (FR-009).                    |
| `lastInfoSync`       | `string`        | ISO-8601 date-time.                             |
| `runtime`            | `integer`       |                                                 |
| `inCinemas`          | `string`        | ISO-8601 date-time.                             |
| `physicalRelease`    | `string`        | ISO-8601 date-time.                             |
| `digitalRelease`     | `string`        | ISO-8601 date-time.                             |
| `year`               | `integer`       |                                                 |
| `secondaryYear`      | `integer`       |                                                 |
| `ratings`            | `object`        | Map of provider -> `{type, value, votes}`.      |
| `recommendations`    | `string`        | Raw string (per API contract).                  |
| `certification`      | `string`        |                                                 |
| `youTubeTrailerId`   | `string`        |                                                 |
| `studio`             | `string`        |                                                 |
| `overview`           | `string`        |                                                 |
| `website`            | `string`        |                                                 |
| `popularity`         | `number`        |                                                 |
| `collection`         | `object`        | `{tmdbId: integer, title: string}`.             |

### Key Entities *(include if feature involves data)*

- **Save Request**: Client-submitted payload containing core record data plus optional MovieMetadata fields (for example, images, genres, ratings, status, and collection).
- **Saved Record**: Stored object that retains core data plus MovieMetadata fields (stored in `metadata-by-movie-id` and merged into HTTP responses); metadata is retrievable in responses and listings.

### Assumptions

- Clients supply metadata fields as top-level keys in the save payload; nested objects are allowed for collection, originalLanguage, images, and ratings.
- Incoming request keys are camelCase. Validation runs both before and after case folding/normalization, and keys must match the recognized key set in both passes.
- Existing save flows already identify the target record for updates; this feature only affects metadata handling.
- Recognized key and type validation aligns with current service limits used for other request fields (no metadata size limit enforced for now).
- The recognized metadata key set is fixed for this iteration: MovieMetadata columns listed in FR-003.
- Existing P-States already map non-metadata movie fields (for example, alternate titles, keywords, availability, quality profiles); this feature will leave those untouched.
- Status strings in save requests map directly to the enum values (Deleted=-1, TBA=0, Announced=1, InCinemas=2, Released=3) irrespective of casing (for example, `tba`, `announced`, `inCinemas`, `released`); invalid status values are treated as validation errors.
- Metadata changes are only accepted via PUT requests; repeated POST create requests are treated as duplicates and must not mutate stored metadata.
- Stored metadata rows are sparse and include only provided non-null keys.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of saved records retrieved immediately after a successful save present metadata values that match the last submitted valid metadata (no silent drops).
- **SC-002**: 95% of save attempts with invalid metadata return a specific validation message naming the problematic field without persisting any metadata.
- **SC-003**: Saves submitted without metadata continue to succeed at or above the current success rate (target ≥99%) with no additional steps required by the client.
