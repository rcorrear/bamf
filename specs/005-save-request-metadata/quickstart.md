# Quickstart: Save Request Metadata

1) Read the spec and research  
- `specs/005-save-request-metadata/spec.md` for requirements and success criteria.  
- `specs/005-save-request-metadata/research.md` for decisions (allowlist, storage shape, validation approach).

2) Persist metadata without changing HTTP behavior (US1/US2)  
- Capture MovieMetadata fields from the save payload and pass them through the Rama event flow.  
- Persist metadata into `metadata-by-movie-id` (keyed by movie id); no-op when the metadata map is empty.  
- Only PUT updates may change metadata; replace only provided keys and keep unspecified keys unchanged.  
- Treat metadata keys set to `null` as explicit removals.  
- POST create requests should not mutate metadata on duplicates.

3) Add HTTP validation and response handling (US4)  
- Accept MovieMetadata fields at the top level of the save request (Radarr payload keys).  
- Enforce exact key matches for the MovieMetadata recognized key set (images, genres, sortTitle, cleanTitle, originalTitle, cleanOriginalTitle, originalLanguage, status, lastInfoSync, runtime, inCinemas, physicalRelease, digitalRelease, year, secondaryYear, ratings, recommendations, certification, youTubeTrailerId, studio, overview, website, popularity, collection).  
- Validate camelCase metadata fields in the HTTP create/update schemas before and after normalization.  
- Ignore unknown metadata keys; map collection and originalLanguage values per the data model.  
- Normalize status strings to Deleted=-1, TBA=0, Announced=1, InCinemas=2, Released=3.  
- Merge stored metadata into create/update responses and list/get inspection responses; omit keys that are not stored.

4) Keep non-metadata flows untouched  
- Preserve existing mappings for alternate titles, keywords, availability, quality profiles, etc.

5) Tests  
- Add clojure.test/kaocha coverage for: valid metadata save/echo, metadata-less saves, PUT-only metadata updates, `null` removal behavior, status casing normalization, rejection of invalid shape/status, ignoring unknown keys, and full payload delivery to Rama via `foreign-append!` (unit + module integration).  
- Use fixtures: `components/movies/test/resources/movie-save-request.json`, `components/movies/test/resources/movie-update-request.json`, `components/movies/test/resources/movie-save-request-invalid-metadata.json`.  
- Run `clojure -X:test`.

6) Observability  
- Telemere print-event logs: `hashing-by-movie-id`, `saving-metadata`.  
- Metrics: none for this feature.  
- Traces: none for this feature.
