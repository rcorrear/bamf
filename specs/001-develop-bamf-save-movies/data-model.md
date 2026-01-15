# Phase 1 – Data Model

## Movie Persistence Record
- **Id** (`long`, required) – Rama-assigned sequential identifier.
- **Path** (`string`, required) – Absolute or root-relative destination path; must be unique; normalized to lowercase filesystem rules.
- **Monitored** (`boolean`, required) – Indicates whether Bamf should track future updates; must be true/false.
- **QualityProfileId** (`long`, required) – Reference to quality profile configured in Radarr-compatible systems; must be positive.
- **TitleSlug** (`string`, optional) – When present must equal the string form of `tmdbId` to remain compatible with Radarr route patterns.
- **Added** (`string`, optional) – ISO 8601 UTC timestamp when the movie was registered; defaults to ingestion timestamp if absent.
- **Tags** (`vector<string>`, optional) – Tag identifiers; defaults to empty vector; each tag canonicalized to lowercase.
- **AddOptions** (`map`, optional) – Nested map mirroring Radarr `addOptions`; defaults to empty map.
- **MovieFileId** (`long`, optional) – Identifier referencing current file version (0 when not yet downloaded per Radarr contract); must be ≥0 when provided.
- **MinimumAvailability** (`enum`, required) – Availability threshold (e.g., `announced`, `in-cinemas`, `released`); validate against configured set.
- **LastSearchTime** (`string`, optional) – ISO 8601 UTC timestamp of most recent search; optional for persistence-only phase.
- **TargetSystem** (`string`, optional) – Propagated system selector (Radarr/Sonarr/etc.) per constitution additional constraint; defaults to `"radarr"` for compatibility when omitted.

## Rama Depot Events
- **Event Type**: `movie.saved`
- **Payload**: `{id title title-slug path root-folder-path monitored quality-profile-id added tags add-options movie-file-id minimum-availability tmdb-id year last-search-time target-system}`
- **Metadata**: command id, request correlation id, received-at timestamp (ISO 8601 UTC).
- **Validation**: Reject if required fields missing or duplicate detection fails; include error event `movie.save-rejected` with reason codes.

## Indexes & Materializations
- **Primary Index**: `:movie/by-id` (Long → Movie Persistence Record).
- **Uniqueness Index**: `:movie/by-tmdb-id` (Long → Movie Persistence Record) enforcing single entry per tmdb id.
- **Path Index**: `:movie/by-path` (normalized string → Movie Persistence Record) enforcing unique storage path.
- **Tag Index**: `:movie/by-tag` (tag string → set<Long>) enabling reverse lookup by tag.
- **Target System Index**: `:movie/by-target-system` (string → set<Long>) to satisfy constitution constraint for downstream routing.

## Duplicate Detection Flow
1. Lookup `tmdbId`; if present, emit duplicate response without persisting.
2. Lookup normalized `Path`; if present, emit duplicate response without persisting.
3. Reserve next Long `Id`, persist `movie.saved` event with normalized payload.
4. Pstate materializes record and updates indexes atomically.

## Validation Rules
- Strings trimmed; empty strings invalid for required fields.
- Tags sanitized to lowercase and deduplicated.
- AddOptions keys retained as provided; values validated for JSON-compatible primitives.
- Timestamps validated against ISO 8601 UTC; convert if payload includes local offset.
- MinimumAvailability must match configured keyword set; invalid values cause a 400 response.
- TitleSlug, when supplied, must match `(str tmdbId)` to satisfy Radarr's canonical URL expectations.

## Error Conditions
- **duplicate-metadata**: Movie with same `tmdbId` already exists.
- **duplicate-path**: Movie with same normalized `Path` already exists.
- **invalid-payload**: Required fields missing or types incorrect (surfaced as HTTP 400).
- **persistence-failure**: Rama depot or pstate write rejected (bubble up infrastructure error with request correlation id).
