# Spec Inconsistencies with Radarr API Probe Findings

**Analysis Date:** 2026-02-28
**Probe Results:** `post_probe_results.json`
**Spec Version:** `specs/005-save-request-metadata/spec.md`

## Critical Inconsistencies

### 1. Missing POST Default Behavior Documentation

**Probe Finding:**
- When `status` is **omitted** from POST: Radarr defaults to `"released"`
- When `minimumAvailability` is **omitted** from POST: Radarr defaults to `"released"`

**Spec Gap:**
The spec documents validation rules (FR-009) for `status` and `minimumAvailability`:
> "The system MUST accept only exact-match values for `status` and `minimumAvailability` and reject unsupported values"

But it does NOT document:
- What happens when these fields are omitted from POST requests
- That Radarr assigns default values of `"released"` for both fields
- Whether our system should replicate these defaults or require explicit values

**Impact:** HIGH
- If our system doesn't match Radarr's default behavior, movies created without these fields will have inconsistent metadata
- Clients might expect the same defaults as Radarr provides

**Recommendation:**
Add to spec:
- FR-009b: When `status` is omitted from POST, the system SHOULD default to `"released"` (matching Radarr behavior)
- FR-009c: When `minimumAvailability` is omitted from POST, the system SHOULD default to `"released"` (matching Radarr behavior)
- OR explicitly document that these fields are REQUIRED on POST if we don't want to replicate defaults

---

### 2. TMDB Auto-Population Not Documented

**Probe Finding:**
18 metadata fields auto-populate from TMDB when omitted from POST:
- certification, cleanTitle, digitalRelease, genres, images, inCinemas
- originalLanguage, originalTitle, overview, physicalRelease, popularity
- ratings, runtime, sortTitle, studio, website, year, youTubeTrailerId

**Spec Statement:**
FR-005: "The system MUST allow saves without metadata to proceed."

**Inconsistency:**
The spec treats all metadata fields as truly optional, but Radarr's API automatically fetches many of them from TMDB when omitted. This means:
- A POST without metadata to Radarr ≠ an empty metadata result
- Radarr uses `tmdbId` to fetch missing metadata from their TMDB integration
- Our system doesn't have TMDB integration (we're storing client-provided metadata)

**Impact:** MEDIUM-HIGH
- Our system's behavior will diverge from Radarr's
- Clients submitting minimal payloads to Radarr get rich metadata back
- Same payloads to our system will have sparse/empty metadata
- This is actually CORRECT for our use case (we store what's provided), but should be documented

**Recommendation:**
Add clarification to spec:
- Document that unlike Radarr's API, our system does NOT auto-fetch metadata from TMDB
- FR-005 amendment: "The system MUST allow saves without metadata to proceed; omitted metadata fields remain null/absent (no TMDB auto-population)"
- Add to Assumptions section: "This system stores client-provided metadata as-is and does not integrate with TMDB to fetch missing fields"

---

### 3. Null Default Fields Not Explicitly Listed

**Probe Finding:**
5 fields default to `null` when omitted from POST:
- cleanOriginalTitle
- collection
- lastInfoSync
- recommendations
- secondaryYear

**Spec Statement:**
The metadata field type table (lines 116-141) lists these fields but doesn't indicate which are nullable or have null defaults.

**Inconsistency:**
- The spec says metadata fields are optional (FR-005)
- But it doesn't document which fields default to null vs which auto-populate (in Radarr's case)
- The type table shows `collection` as `object` but doesn't indicate it can be null

**Impact:** LOW-MEDIUM
- Developers might assume all optional fields behave the same way
- Tests might not cover null handling for these specific fields
- Response serialization needs to handle null correctly per FR-001a (omit from responses)

**Recommendation:**
Update metadata field type table to include a "Nullable" or "Default" column:
```markdown
| Field                | Type            | Nullable | Notes                                           |
| :------------------- | :-------------- | :------- | :---------------------------------------------- |
| `cleanOriginalTitle` | `string`        | Yes      | Defaults to null when omitted                   |
| `collection`         | `object`        | Yes      | Defaults to null when omitted                   |
```

---

### 4. Response Omission Behavior Needs Clarification

**Spec Statement (FR-001a, line 89):**
> "The system MUST omit metadata keys from HTTP responses when they are not stored (never provided or explicitly removed)."

**Spec Statement (User Story 2, line 58):**
> "Given an existing record with metadata, When a PUT request omits all metadata, Then previously stored metadata remains"

**Probe Finding:**
All POST requests succeeded with HTTP 201. Fields omitted from POST either:
- Defaulted to null (5 fields)
- Auto-populated from TMDB (18 fields in Radarr)
- Defaulted to "released" (2 fields: status, minimumAvailability)

**Potential Inconsistency:**
The spec says "omit metadata keys from responses when they are not stored", but in Radarr:
- Even fields that weren't in the POST request appear in the response (because Radarr fetched them from TMDB or applied defaults)

Our system should clarify:
- Do we omit fields that were never provided? (YES, per FR-001a)
- Do we omit fields that are stored as null? (UNCLEAR - needs clarification)
- What about fields with server defaults (status/minimumAvailability)?

**Impact:** MEDIUM
- Response serialization logic needs precise rules
- Tests need to validate omission behavior

**Recommendation:**
Clarify in spec:
- FR-001a amendment: "Fields stored as null are considered 'not stored' and MUST be omitted from responses"
- OR: "Fields stored as null MUST be returned as null in responses"
- Document behavior for server-default fields (status/minimumAvailability) when omitted from POST

---

## Minor Issues

### 5. Type for `recommendations` Could Be More Specific

**Spec (line 134):**
> `recommendations | string | Raw string (per API contract).`

**Probe Finding:**
`recommendations` defaults to `null` when omitted.

**Issue:**
The type is listed as `string`, but it's nullable. Should be documented as `string | null` or the table should have a nullable column.

**Impact:** LOW
**Recommendation:** Add nullable indicator to type table.

---

## Spec Strengths (Alignment with Probe)

✅ The spec correctly identifies all 25+ metadata fields
✅ Validation rules for exact-match status values align with Radarr's behavior
✅ The sparse storage approach (FR-015) is correct
✅ The null-as-removal semantics (FR-010) align with expected behavior

---

## Summary of Required Spec Updates

1. **HIGH PRIORITY**: Document POST default behavior for `status` and `minimumAvailability`
2. **HIGH PRIORITY**: Document that system does NOT auto-populate from TMDB (unlike Radarr)
3. **MEDIUM PRIORITY**: Clarify response omission rules for null-valued fields
4. **LOW PRIORITY**: Add nullable/default column to metadata field type table

---

## Recommended Spec Additions

### New Functional Requirement

**FR-016**: When `status` or `minimumAvailability` are omitted from POST requests, the system SHOULD apply default values of `"released"` to match Radarr API behavior. When omitted from PUT requests, existing values MUST be preserved.

**FR-017**: The system MUST NOT auto-populate metadata from external sources (e.g., TMDB). Only client-provided metadata values are stored and returned.

**FR-018**: Fields stored with `null` values are considered "not stored" for the purposes of FR-001a and MUST be omitted from HTTP responses.

### Updated Assumptions Section

Add after line 156:
> - Unlike Radarr's API, this system does not integrate with TMDB or other external metadata sources. Metadata is stored exactly as provided by the client. Fields omitted from POST requests will not be auto-populated.
> - When `status` or `minimumAvailability` are omitted from POST, the system applies a default value of `"released"` to maintain compatibility with Radarr's behavior.
> - Fields that are stored as `null` are treated as "not stored" and omitted from HTTP responses per FR-001a.
