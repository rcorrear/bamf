# Save Movies Persistence

The `POST /api/v3/movie` endpoint accepts Radarr-style JSON payloads and persists
canonical movie metadata into Rama. This document summarizes the contract and key
behaviours captured in automated tests.

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
  "movieMetadataId": 0,
  "movieFileId": 0,
  "minimumAvailability": "released",
  "tags": ["scifi", "4k"],
  "addOptions": {"searchForMovie": true}
}
```

## Successful Response

```json
{
  "id": 42,
  "title": "Dune",
  "titleSlug": "12345",
  "path": "/movies/Dune (2021)",
  "rootFolderPath": "/movies",
  "monitored": true,
  "qualityProfileId": 1,
  "minimumAvailability": "released",
  "tmdbId": 12345,
  "movieMetadataId": 12345,
  "tags": ["scifi", "4k"],
  "addOptions": {"searchForMovie": true},
  "added": "2025-09-21T12:00:00Z",
  "lastSearchTime": "2025-09-21T12:00:00Z"
}
```

- Tags are normalized to lowercase and de-duplicated but remain JSON arrays.
- `added`/`lastSearchTime` timestamps are converted to ISO 8601 UTC strings.
- The response mirrors Radarr's `movie` resource; Bamf-specific routing metadata is
  not included.
- Incoming `movieMetadataId` values of `0` are replaced with the provided `tmdbId`
  so Rama indexes can enforce uniqueness without exposing Bamf-specific fields.
- When `titleSlug` is supplied it must match the stringified `tmdbId`; mismatches
  trigger a validation error.

## Duplicate Handling

- Submissions with an existing `tmdbId` return `400`:
  ```json
  {
    "message": "Movie already exists",
    "reason": "duplicate-metadata",
    "errors": {"tmdbId": ["Movie already exists (id 7)"]}
  }
  ```
- Duplicate `path` values also return `400` with `errors.path` populated.

## Validation Errors

Missing or malformed required fields result in a `400` response with detailed
messages, for example:

```json
{
  "message": "Validation failed",
  "errors": ["path is required"]
}
```

## Quickstart Notes

Use Rama's provided test harness (`com.rpl.rama.test/create-ipc`) to exercise the
Save Movies module once the Rama module wiring is in place. This avoids the need
for bespoke in-memory simulators.
