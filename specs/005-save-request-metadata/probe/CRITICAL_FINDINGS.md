# CRITICAL SPEC/IMPLEMENTATION INCONSISTENCIES

**Analysis Date:** 2026-02-28
**Components Analyzed:**
- Spec: `specs/005-save-request-metadata/spec.md`
- Implementation: `components/movies/src/clj/bamf/movies/persistence.clj`, `model.clj`
- Radarr Behavior: `probe/post_probe_results.json`

---

## 🚨 CRITICAL ISSUE #1: minimumAvailability Requirement Mismatch

### Current Implementation (`persistence.clj:110`)
```clojure
minimum-error (when (nil? (:minimum-availability movie))
                missing-minimum-availability-error)
```

**Behavior:** REQUIRES `minimumAvailability` on POST - returns 422 if omitted

### Radarr API Behavior (Probe Finding)
**Behavior:** `minimumAvailability` is OPTIONAL on POST - defaults to `"released"` when omitted

### Spec Statement (`spec.md:199-200`)
```
[:minimum-availability {:optional true :error/message minimum-availability-message}
```

**Behavior:** Schema marks it as `optional: true` but validation enforces it as required

### The Problem
**INCONSISTENCY:** The implementation requires `minimumAvailability` but:
1. The schema says it's optional
2. Radarr's API makes it optional with a default
3. The spec doesn't document it as required

### Impact
- **SEVERITY:** HIGH
- Clients omitting `minimumAvailability` will get 422 errors from our API
- Same payload succeeds in Radarr (creates movie with default "released")
- Breaking API compatibility with Radarr

### Resolution Options

**Option A: Match Radarr (Recommended)**
- Remove the requirement check on line 110
- Apply default value `"released"` when omitted
- Document default behavior in spec

**Option B: Require Explicitly**
- Update spec to document that `minimumAvailability` is REQUIRED on POST
- Keep current implementation
- Accept API incompatibility with Radarr

---

## 🚨 CRITICAL ISSUE #2: status Has No Default But Radarr Provides One

### Current Implementation (`model.clj:333`)
```clojure
(assoc :minimum-availability (normalize-minimum-availability (:minimum-availability movie)))
```

**Behavior:**
- No similar normalization for `:status` in the `normalize` function
- `status` can be omitted or nil
- No default applied

### Radarr API Behavior (Probe Finding)
**Behavior:** `status` is OPTIONAL on POST - defaults to `"released"` when omitted

### The Problem
**INCONSISTENCY:**
1. Our implementation accepts `status: null` or omitted status
2. Radarr defaults omitted status to `"released"`
3. Our stored movie will have `status: null`, Radarr's will have `"released"`
4. Spec doesn't document this difference

### Impact
- **SEVERITY:** MEDIUM-HIGH
- Movies created without `status` field will have different metadata in our system vs Radarr
- Downstream consumers might expect status to always have a value
- No validation prevents this mismatch

### Resolution Options

**Option A: Match Radarr (Recommended)**
- Apply default value `"released"` when status is omitted on POST
- Document in spec

**Option B: Allow Null**
- Document that our system allows null status
- Accept divergence from Radarr

---

## 🚨 CRITICAL ISSUE #3: Spec Doesn't Document POST Defaults

### Spec Gap
The spec extensively documents:
- PUT update behavior (preserve omitted fields)
- Null removal behavior (explicit `null` removes metadata)
- Validation rules

But it does NOT document:
- What happens when fields are omitted from POST
- Server default values for any fields
- TMDB auto-population differences

### The Problem
**INCONSISTENCY:**
- Developers implementing from spec won't know about defaults
- Test cases won't cover default behavior
- API documentation will be incomplete

### Impact
- **SEVERITY:** HIGH
- Incomplete specification
- Implementation ambiguity
- Missing test coverage

### Resolution
Add new spec sections:
1. **POST Default Behavior** - document which fields get defaults
2. **Radarr Compatibility Notes** - document known differences
3. **Field Reference Table** - add "Default on POST" column

---

## Implementation vs Radarr Behavioral Differences

### Our Implementation

| Field | Omitted on POST | Current Behavior |
|-------|----------------|------------------|
| `minimumAvailability` | ❌ REJECTS | Returns 422 error |
| `status` | ✅ Allowed | Stored as `null`, omitted from response |
| Other metadata | ✅ Allowed | Stored as `null`, omitted from response |

### Radarr API

| Field | Omitted on POST | Radarr Behavior |
|-------|----------------|-----------------|
| `minimumAvailability` | ✅ Allowed | Defaults to `"released"` |
| `status` | ✅ Allowed | Defaults to `"released"` |
| 18 metadata fields | ✅ Allowed | Auto-fetches from TMDB |
| 5 metadata fields | ✅ Allowed | Defaults to `null` |

### Divergence Analysis

**✅ Acceptable Differences:**
- Not auto-fetching from TMDB (we're not a TMDB client)
- Storing sparse metadata (design choice)

**❌ Problematic Differences:**
- Rejecting requests that Radarr accepts (`minimumAvailability` requirement)
- Different default values (`status` null vs "released")

---

## Recommended Actions

### 1. Immediate Fix (Breaking Change)
**File:** `components/movies/src/clj/bamf/movies/persistence.clj:110`

**Remove:**
```clojure
minimum-error (when (nil? (:minimum-availability movie))
                missing-minimum-availability-error)
```

**Add to model.clj normalize function (line 333):**
```clojure
(assoc :minimum-availability
  (or (normalize-minimum-availability (:minimum-availability movie))
      "released"))
```

**Add similar for status:**
```clojure
(assoc :status
  (or (normalize-metadata-status (get-in movie [:status]))
      "released"))
```

### 2. Update Spec

**Add to spec.md after line 156:**

```markdown
#### POST Default Values

When fields are omitted from POST requests:

- `status` defaults to `"released"`
- `minimumAvailability` defaults to `"released"`
- Metadata fields (cleanOriginalTitle, collection, lastInfoSync, recommendations, secondaryYear) default to `null` and are omitted from responses
- Other metadata fields remain `null` (our system does not auto-fetch from TMDB)

#### Divergence from Radarr API

Unlike Radarr's API:
- We do NOT auto-populate metadata fields from TMDB when omitted
- We store exactly what clients provide
- Fields omitted on POST remain null/absent unless defaults are applied
```

**Add new FR:**
```markdown
- **FR-019**: When `status` is omitted from POST, the system MUST default to `"released"`.
- **FR-020**: When `minimumAvailability` is omitted from POST, the system MUST default to `"released"`.
```

### 3. Update Tests

Ensure tests cover:
- POST without `minimumAvailability` succeeds and defaults to "released"
- POST without `status` succeeds and defaults to "released"
- Response includes default values (not omitted)
- PUT preserves existing values when fields are omitted

### 4. Update API Documentation

Document in API schema:
- Both fields are optional with defaults on POST
- Both fields preserve existing values on PUT when omitted
- Explicit `null` is not allowed (validation will reject)

---

## Testing Strategy

### New Test Cases Required

1. **POST without minimumAvailability**
   - Expect: 201 Created
   - Response includes: `"minimumAvailability": "released"`

2. **POST without status**
   - Expect: 201 Created
   - Response includes: `"status": "released"`

3. **POST with explicit minimumAvailability**
   - Expect: Uses provided value

4. **PUT omitting minimumAvailability**
   - Expect: Preserves existing value

5. **PUT with minimumAvailability = null**
   - Expect: 422 Unprocessable Entity (validation failure)

---

## Summary

| Issue | Severity | Component | Status |
|-------|----------|-----------|--------|
| minimumAvailability required on POST | 🔴 HIGH | persistence.clj:110 | **NEEDS FIX** |
| status has no default | 🟡 MEDIUM | model.clj:333 | **NEEDS FIX** |
| Spec missing POST defaults | 🔴 HIGH | spec.md | **NEEDS UPDATE** |
| No TMDB auto-population | 🟢 LOW | By Design | **DOCUMENT** |

**Bottom Line:** The implementation currently REJECTS valid Radarr-compatible requests. This is a breaking API incompatibility that should be fixed.
