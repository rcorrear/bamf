# Phase 0 Research

Decision: Persist metadata in `metadata-by-movie-id` PState and merge for HTTP  
Rationale: Store MovieMetadata separately in Rama (keyed by movie id) to keep the movie row focused while still returning a merged HTTP view that matches the Radarr payload shape.  
Alternatives considered: Store in the movie row PState; nest under a `metadata` sub-map - rejected to keep storage isolated while preserving API compatibility.

Decision: Metadata fields correspond to MovieMetadata columns (excluding core movie identifiers)  
Rationale: Persist only the MovieMetadata columns: images, genres, sortTitle, cleanTitle, originalTitle, cleanOriginalTitle, originalLanguage, status, lastInfoSync, runtime, inCinemas, physicalRelease, digitalRelease, year, secondaryYear, ratings, recommendations, certification, youTubeTrailerId, studio, overview, website, popularity, collection. Core movie fields (tmdbId, imdbId, title) remain part of the movie record, not metadata.  
Alternatives considered: Persist custom keys (sourceSystem/externalId/etc.) or include core identifiers - rejected because they do not map to MovieMetadata or belong to the movie row.

Decision: Request shape uses Radarr payload keys (no extraData block)  
Rationale: The save request already includes MovieMetadata fields at the top level; accept those keys directly and store them in `metadata-by-movie-id`.  
Alternatives considered: Add a dedicated `extraData` block - rejected to avoid deviating from the existing payload shape.

Decision: Accept camelCase request keys and validate both camelCase and kebab-case forms  
Rationale: Radarr payloads use camelCase, and Ring middleware decamelizes to kebab-case keywords; validation runs twice (camelCase pre-normalization, kebab-case post-keywordization) with exact matches in each pass.  
Alternatives considered: Require kebab-case payloads - rejected to preserve Radarr compatibility.

Decision: Validation rules and mapping  
Rationale: Enforce the recognized metadata key set and accept value types as provided by the Radarr payload (strings, numbers, lists, or maps for images/ratings/collection/originalLanguage). Ignore unknown metadata keys. Map collection and originalLanguage values per the data model. Validate `status` and `minimumAvailability` as exact-match tokens (no enum mapping). Treat `null` metadata values as explicit removals.  
Alternatives considered: Reject unknown keys at the schema layer; allow `null` values to pass through - rejected to preserve compatibility and avoid storing unusable fields.

Decision: Update semantics and size limits  
Rationale: Only PUT updates may change metadata; provided keys replace, unspecified keys remain. No size limit is enforced this iteration to avoid premature constraints.  
Alternatives considered: Allow POST to update metadata; enforce a size limit now - rejected to keep create idempotent and defer sizing until usage is clearer.

Decision: Response representation for missing metadata  
Rationale: Omit metadata keys that are not stored (never provided or explicitly removed) to keep responses aligned with optional fields and avoid implying a value exists.  
Alternatives considered: Return missing keys as nulls or empty values - rejected to avoid response noise and ambiguity.

Decision: Rama depot events carry full payload  
Rationale: `foreign-append!` must receive the full movie payload (core movie fields plus metadata fields) so the Rama module can persist metadata alongside the movie row.  
Alternatives considered: Trim payload to only movie PState fields - rejected because metadata persistence depends on the full event payload.

Outstanding clarifications: None (all requirements resolved for this phase).
