# Radarr POST /api/v3/movie - Field Default Behavior

**Probe Date:** 2026-02-28
**Radarr Instance:** http://radarr.pig-duckbill.ts.net:7878

## Test Methodology

For each field in the test list, created a movie via POST with that field omitted from the payload (using tmdbId 530915 - "1917"). Observed what value Radarr assigned to the omitted field.

## Results

### 1. Server-Assigned Defaults (Independent of TMDB)

These fields receive hardcoded default values from Radarr when omitted:

| Field | Default Value |
|-------|--------------|
| `status` | `"released"` |
| `minimumAvailability` | `"released"` |

### 2. Fields That Default to NULL

These fields are set to `null` when omitted from POST:

- `cleanOriginalTitle`
- `collection`
- `lastInfoSync`
- `recommendations`
- `secondaryYear`

### 3. Fields Auto-Populated from TMDB

When omitted from POST, these fields are automatically fetched from TMDB using the provided `tmdbId`:

- `certification`
- `cleanTitle`
- `digitalRelease`
- `genres`
- `images`
- `inCinemas`
- `originalLanguage`
- `originalTitle`
- `overview`
- `physicalRelease`
- `popularity`
- `ratings`
- `runtime`
- `sortTitle`
- `studio`
- `website`
- `year`
- `youTubeTrailerId`

## Summary

- **Total fields tested:** 25
- **Server defaults:** 2 (`status`, `minimumAvailability`)
- **Null defaults:** 5
- **TMDB auto-populated:** 18
- **Success rate:** 100% (all POST requests returned HTTP 201)

## Implications for Spec/Implementation

1. `status` and `minimumAvailability` should be marked as **optional with defaults** in the spec
2. Fields in the "null defaults" category are truly optional and nullable
3. Fields in the "TMDB auto-populated" category can be omitted on POST, but Radarr will fetch them from TMDB
4. All tested fields are **removable** (can be omitted without causing errors)

## Raw Data

Full probe results available in: `scripts/post_probe_results.json`
