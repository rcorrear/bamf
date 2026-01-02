# Data Model

## Entities

### Save Request
- **Core movie fields**: existing movie payload fields (id, tmdbId, imdbId, title variants, status, monitored, qualityProfileId, availability, etc.)
- **MovieMetadata fields** (top-level keys matching Radarr payload):
  - `images` (list of objects; JSON-serializable)
  - `genres` (list of strings)
  - `sortTitle` (string)
  - `cleanTitle` (string)
  - `originalTitle` (string)
  - `cleanOriginalTitle` (string)
  - `originalLanguage` (object; `originalLanguage.id` maps to the MovieMetadata OriginalLanguage value)
  - `status` (string mapped case-insensitively to enum)
  - `lastInfoSync` (date-time string)
  - `runtime` (integer)
  - `inCinemas` (date-time string)
  - `physicalRelease` (date-time string)
  - `digitalRelease` (date-time string)
  - `year` (integer)
  - `secondaryYear` (integer)
  - `ratings` (object; JSON-serializable)
  - `recommendations` (string; raw string per API contract)
  - `certification` (string)
  - `youTubeTrailerId` (string)
  - `studio` (string)
  - `overview` (string)
  - `website` (string)
  - `popularity` (number)
  - `collection` (object with `tmdbId` and `title`; maps to CollectionTmdbId/CollectionTitle)
- Unknown metadata keys: ignored without failing the save
- Input keys are camelCase (Radarr payload). Validation runs both before and after normalization; keys must match the recognized set in both passes.

### Saved Record (HTTP)
- **Core movie fields**: unchanged by this feature
- **MovieMetadata fields** (merged into responses using Radarr payload keys; collection/originalLanguage returned as provided)
  - Updated only on PUT; provided keys replace existing values; unspecified keys persist
  - Keys set to `null` are removed from stored metadata
  - Keys that are not stored are omitted from responses
  - POST duplicates must not mutate existing metadata
- **status**: stored as enum value per mapping; invalid values rejected

### Persistence (Rama)
- **movies** PState: core movie fields only (existing storage)
- **metadata-by-movie-id** PState: MovieMetadata fields keyed by movie id (movie-id → metadata row), stored separately from the movie row and joined for HTTP responses

### Status Enum
- Deleted = -1
- TBA = 0
- Announced = 1
- InCinemas = 2
- Released = 3
- Input normalization: case-insensitive strings mapped to these values; invalid strings cause validation failure

## Relationships
- Save Request → Saved Record (1:1 upsert by movie identity); metadata merged into the stored record on successful saves.

## Validation Rules
- Recognized metadata key set enforced with exact key matching (MovieMetadata fields above)
- Value types follow Radarr payload shapes (strings, numbers, lists, or maps)
- Metadata keys explicitly set to `null` are removed from stored metadata
- Unknown metadata keys are ignored
- No metadata size limit enforced for this iteration
- Validation failures block persistence; responses name offending fields
