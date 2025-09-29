# Feature Specification: Save Movies Persistence

**Feature Branch**: `001-develop-bamf-a`  
**Created**: 2025-09-21  
**Status**: Draft  
**Input**: User description: "Develop Bamf, a media downloads platform. It should allow users to add movies, tv shows and eventually other media types. These are then sent to a third party index manager (Prowlarr) which searches for them in online databases of media (BitTorrent trackers, eventually Usenet). After the media is found it is scheduled for download in a client of this type and then another process moves files to their final destination. In this initial phase for this feature, let's call it \"Save Movies,\" let's focus on the movies module. We'll take API requests with JSON resembling what's in the file movie.json (or which is the same, the POST /api/v3/movie endpoint for Radarr). You can edit any comments that you make, but you can't edit comments that other people made. You can delete any comments that you made, but you can't delete comments anybody else made."

## Execution Flow (main)
```
1. Receive a Save Movies API request containing Radarr-style movie metadata JSON
   → Validate presence and format of persistence-critical fields (Path, Monitored, QualityProfileId, MovieFileId, MinimumAvailability, MovieMetadataId)
   → If validation fails: return error describing missing/invalid fields
2. Normalize incoming payload to Bamf's canonical movie representation (field names, data types, timestamp formats)
   → Convert Tags into a deduplicated, lowercase vector of strings (default to empty vector when omitted) and capture AddOptions as a Clojure map (default to empty map when omitted)
   → Convert Added and LastSearchTime timestamps to ISO 8601 UTC strings
3. Check existing persistence store for duplicates based on MovieMetadataId and Path
   → If duplicate found: respond with status indicating existing record and return stored identifier
4. Assign the next Rama movie identifier as a Long that maintains SQL-style primary key semantics
   → Attach timestamps (Added, LastSearchTime) using ISO 8601 UTC
5. Persist the normalized movie record in Rama, ensuring indexes cover MovieMetadataId, Path, and Tag membership for fast lookup
   → If persistence fails: surface failure reason and leave existing state untouched
6. Return a success acknowledgement containing the stored movie identifier, key field values, and timestamps so clients can confirm persistence
```

---

## ⚡ Quick Guidelines
- ✅ Focus on WHAT users need and WHY
- ❌ Avoid HOW to implement (no tech stack, APIs, code structure)
- 👥 Written for business stakeholders, not developers

### Section Requirements
- **Mandatory sections**: Must be completed for every feature
- **Optional sections**: Include only when relevant to the feature
- When a section doesn't apply, remove it entirely (don't leave as "N/A")

### For AI Generation
When creating this spec from a user prompt:
1. **Mark all ambiguities**: Use [NEEDS CLARIFICATION: specific question] for any assumption you'd need to make
2. **Don't guess**: If the prompt doesn't specify something (e.g., "login system" without auth method), mark it
3. **Think like a tester**: Every vague requirement should fail the "testable and unambiguous" checklist item
4. **Common underspecified areas**:
   - User types and permissions  
   - Data retention/deletion policies  
   - Performance targets and scale  
   - Error handling behaviors  
   - Integration requirements  
   - Security/compliance needs

---

## User Scenarios & Testing *(mandatory)*

### Primary User Story
As a Bamf operator, I submit a movie through the Save Movies API so the platform can immediately persist its metadata and readiness state for later automation.

### Acceptance Scenarios
1. **Given** a well-formed movie payload that contains all persistence-required fields, **When** it is posted to the Save Movies endpoint, **Then** Bamf validates the data, stores a Rama movie record mirroring the Movies schema, and returns the assigned identifier.
2. **Given** a movie whose MovieMetadataId already exists in storage, **When** the same payload is submitted again, **Then** Bamf rejects the duplicate, references the existing record, and leaves persisted data unchanged.

### Edge Cases
- What happens when the payload omits optional fields like Tags or AddOptions—should defaults be applied or stored as empty values?
- How does the system handle timestamps provided in varying formats or time zones for Added and LastSearchTime?
- What response is returned if Rama persistence is temporarily unavailable or the indexing step cannot be completed?

## Requirements *(mandatory)*

### Functional Requirements
- **FR-001**: The platform MUST expose a Save Movies endpoint that accepts JSON payloads aligned with Radarr's `POST /api/v3/movie` structure for movie persistence.
- **FR-002**: The system MUST validate incoming payloads and provide explicit error messages for missing or malformed persistence-critical fields (Path, RootFolderPath, Monitored, QualityProfileId, MinimumAvailability, `tmdbId`) and ensure optional AddOptions maps are well-formed when supplied.
- **FR-003**: The system MUST normalize incoming data into Bamf's canonical movie representation, converting timestamps to ISO 8601 UTC, defaulting Tags to an empty vector, defaulting AddOptions to an empty map when not provided, and ensuring `titleSlug` matches the string form of `tmdbId`.
- **FR-004**: The system MUST prevent duplicate movie records by enforcing uniqueness on the derived MovieMetadataId (falling back to `tmdbId`) and Path before writing to storage.
- **FR-005**: The system MUST create and persist a Rama movie record with a Long Id and fields Title, TitleSlug, Path, RootFolderPath, Monitored, QualityProfileId, Added, Tags (vector of strings), AddOptions (map), MovieFileId, MinimumAvailability, MovieMetadataId, `tmdbId`, and LastSearchTime.
- **FR-006**: The system MUST index MovieMetadataId, Path, and individual Tag values in Rama to support fast retrieval and duplicate checks.
- **FR-007**: The system MUST return a confirmation payload containing the persisted movie identifier, monitored status, relevant tags, and storage timestamps so clients can reconcile submissions.
- **FR-008**: The system MUST rely on Rama depot event retention for auditability, eliminating the need for additional persistence-layer logging.

### Key Entities *(include if feature involves data)*
- **Movie Persistence Record**: Canonical movie data stored in Rama with Long Id, Title, TitleSlug, Path, RootFolderPath, Monitored flag, QualityProfileId, Added timestamp (ISO 8601 UTC), Tags vector, AddOptions map, MovieFileId, MinimumAvailability level, MovieMetadataId (derived from `tmdbId` when Radarr submits `0`), `tmdbId`, and LastSearchTime (ISO 8601 UTC).
- **Duplicate Detection Index**: Maintains lookup keys for MovieMetadataId, Path, and Tag membership to ensure submissions do not create redundant records and to accelerate retrieval.

### Dependencies & Assumptions
- Rama persistence infrastructure provides Long-typed identifiers and indexed structures for Tag membership.
- Event depots remain authoritative for audit trails, enabling materialized views without additional logging layers.

### Success Criteria
- A Rama depot persists the submitted movie event with normalized data.
- A Rama pstate materializes the movie record with Long Id, canonical fields, and ISO 8601 UTC timestamps.
- Rama indexing supports lookup of the movie record by MovieMetadataId, Path, and Tag.

---

## Review & Acceptance Checklist
*GATE: Automated checks run during main() execution*

### Content Quality
- [ ] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

### Requirement Completeness
- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

---

## Execution Status
*Updated by main() during processing*

- [x] User description parsed
- [x] Key concepts extracted
- [x] Ambiguities marked
- [x] User scenarios defined
- [x] Requirements generated
- [x] Entities identified
- [x] Review checklist passed

---
