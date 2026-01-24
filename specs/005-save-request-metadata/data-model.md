# Data Model

## Entities

### Save Request
- **Core movie fields**: existing movie payload fields (id, tmdbId, imdbId, title variants, monitored, qualityProfileId, availability, minimumAvailability, etc.)
- **MovieMetadata fields** (top-level keys matching Radarr payload):
  - `certification` (string)
  - `cleanOriginalTitle` (string)
  - `cleanTitle` (string)
  - `collection` (object with `tmdbId` and `title`; maps to CollectionTmdbId/CollectionTitle)
  - `digitalRelease` (date-time string)
  - `genres` (list of strings)
  - `images` (list of objects; JSON-serializable)
  - `inCinemas` (date-time string)
  - `lastInfoSync` (date-time string)
  - `originalLanguage` (object stored as provided)
  - `originalTitle` (string)
  - `overview` (string)
  - `physicalRelease` (date-time string)
  - `popularity` (number)
  - `ratings` (object; JSON-serializable)
  - `recommendations` (string; raw string per API contract)
  - `runtime` (integer)
  - `secondaryYear` (integer)
  - `sortTitle` (string)
  - `studio` (string)
  - `website` (string)
  - `year` (integer)
  - `youTubeTrailerId` (string)
- Unknown metadata keys: ignored without failing the save
- Input keys are camelCase (Radarr payload). Ring middleware decamelizes to kebab-case keywords; validate camelCase keys pre-normalization and kebab-case keys post-keywordization, with exact matches in each pass.

### Saved Record (HTTP)
- **Core movie fields**: unchanged by this feature
- **MovieMetadata fields** (merged into responses using Radarr payload keys; collection/originalLanguage returned as provided)
  - Updated only on PUT; provided keys replace existing values; unspecified keys persist
  - Keys set to `null` are removed from stored metadata
  - Keys that are not stored are omitted from responses
- POST duplicates must not mutate existing metadata

### Persistence (Rama)
- **movies** PState: core movie fields only (existing storage)
- **metadata-by-movie-id** PState: MovieMetadata fields keyed by movie id (movie-id → metadata row), stored separately from the movie row and joined for HTTP responses

### Status Validation
- Allowed tokens (exact match): `deleted`, `tba`, `announced`, `inCinemas`, `released`
- Default value when omitted on POST: `"released"`

### POST Default Behavior
When fields are omitted from POST requests:
- **Core field defaults**: `status` and `minimumAvailability` → `"released"` (stored in the movie row)
- **TMDB auto-population** *(future work)*: certification, cleanTitle, digitalRelease, genres, images, inCinemas, originalLanguage, originalTitle, overview, physicalRelease, popularity, ratings, runtime, sortTitle, studio, website, year, youTubeTrailerId (currently default to `null` when omitted)
- **Null defaults**: cleanOriginalTitle, collection, lastInfoSync, recommendations, secondaryYear

## Relationships
- Save Request → Saved Record (1:1 upsert by movie identity); metadata merged into the stored record on successful saves.

## Validation Rules
- Recognized metadata key set enforced with exact key matching (MovieMetadata fields above)
- Value types follow Radarr payload shapes (strings, numbers, lists, or maps)
- Metadata keys explicitly set to `null` are removed from stored metadata
- Unknown metadata keys are ignored
- No metadata size limit enforced for this iteration
- Validation failures block persistence; responses name offending fields
