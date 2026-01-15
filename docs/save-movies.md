# Save Movies Persistence

The `POST /api/v3/movie` endpoint accepts Radarr-style JSON payloads and persists
canonical movie metadata into Rama. Reads are exposed via `GET /api/v3/movie`
(optionally filtered by `monitored=true`) and `GET /api/v3/movie/{id}`. Updates
to monitored/quality/availability/metadata/lastSearchTime are available via the
component API (`bamf.movies.interface/update-movie!`) and emit `movie-updated-event`,
but are not surfaced as HTTP PATCH/PUT.

## Request

```json
{
  "title": "Dune",
  "qualityProfileId": 1,
  "path": "/movies/Dune (2021)",
  "titleSlug": "12345",
  "rootFolderPath": "/movies",
  "monitored": true,
  "tmdbId": 12345,
  "movieFileId": 0,
  "minimumAvailability": "released",
  "tags": ["scifi", "4k"],
  "addOptions": {"searchForMovie": true}
}
```

## Successful Response

```json
{
  "data": {
    "id": 42,
    "title": "Dune",
    "titleSlug": "12345",
    "path": "/movies/Dune (2021)",
    "rootFolderPath": "/movies",
    "monitored": true,
    "qualityProfileId": 1,
    "minimumAvailability": "released",
    "tmdbId": 12345,
    "tags": ["scifi", "4k"],
    "addOptions": {"searchForMovie": true},
    "added": "2025-09-21T12:00:00Z",
    "lastSearchTime": "2025-09-21T12:00:00Z"
  }
}
```

- Tags are normalized to lowercase and de-duplicated but remain JSON arrays.
- `added`/`lastSearchTime` timestamps are converted to ISO 8601 UTC strings.
- When `titleSlug` is supplied it must match the stringified `tmdbId`; mismatches
  trigger a validation error.

## Duplicate Handling

Submissions with an existing `tmdbId` return `409`:
```json
{
  "error": "duplicate-metadata",
  "field": "tmdbId",
  "existing-id": 7
}
```

## Validation Errors

Missing or malformed required fields result in a `422` response with detailed
messages, for example:

```json
{
  "errors": ["path is required"]
}
```

## Events
- `movie-created-event` is published on every successful POST.
- `movie-updated-event` is published when `bamf.movies.interface/update-movie!` is invoked (component/repl); HTTP currently exposes create/read only.

## Quickstart Notes

Use Rama's provided test harness (`com.rpl.rama.test/create-ipc`) to exercise the
Save Movies module once the Rama module wiring is in place. This avoids the need
for bespoke in-memory simulators. For HTTP reads, use `GET /api/v3/movie` (monitored filter optional) and `GET /api/v3/movie/{id}`.
