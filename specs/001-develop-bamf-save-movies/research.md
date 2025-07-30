# Phase 0 Research – Save Movies Persistence

## Decision Log

### 1. Timestamp Normalization
- **Decision**: Represent `Added` and `LastSearchTime` using ISO 8601 strings with UTC offset (`Z`).
- **Rationale**: Aligns with feature spec, prevents timezone drift across Rama depots and client integrations, and mirrors Radarr expectations when timestamps are absent or in differing zones.
- **Alternatives Considered**:
  - Store raw Radarr timestamps: rejected due to inconsistent locale/offset handling.
  - Convert to epoch milliseconds: rejected because API contract calls for ISO 8601 readability for external systems.

### 2. Optional Field Defaults
- **Decision**: When Tags/AddOptions are omitted, persist Tags as an empty set and AddOptions as an empty map while omitting non-collection fields altogether.
- **Rationale**: Matches spec guidance, simplifies downstream logic, and preserves schema expectations for Rama indexes.
- **Alternatives Considered**:
  - Allow `nil`: rejected because Rama indexed sets/maps do not tolerate null membership well and complicate contract validation.
  - Use sentinel values (e.g., `[]`, `{}` serialized strings): rejected for readability and schema drift risk.

### 3. Rama Identifier Strategy
- **Decision**: Use a monotonically increasing Long for `Id`, managed by Rama depot sequence helper to emulate SQL autoincrement semantics.
- **Rationale**: Maintains compatibility with legacy SQL concept while fitting Rama's deterministic event sourcing model.
- **Alternatives Considered**:
  - UUIDs: rejected because spec explicitly requests Long alignment with SQL primary key.
  - Client-provided ids: rejected to avoid collision and trust issues.

### 4. Tag Indexing
- **Decision**: Index tags via Rama set membership index keyed by tag string, mapping to movie ids for fast filtering.
- **Rationale**: Satisfies requirement to query by individual tags, keeps duplication checks efficient.
- **Alternatives Considered**:
  - Store tags as raw collection without index: rejected due to search performance.
  - Flatten tags into concatenated string: rejected for brittleness and loses set semantics.

### 5. Duplicate Detection Keys
- **Decision**: Enforce uniqueness on both `MovieMetadataId` and normalized `Path` via Rama pstate indexes and pre-insert checks.
- **Rationale**: Mirrors SQL indexes listed in reference schema; ensures duplicates are prevented regardless of metadata or storage path.
- **Alternatives Considered**:
  - Check only `MovieMetadataId`: rejected because same metadata could map to different physical copies during testing.
  - Delay duplicate detection to download phase: rejected per requirement to reject duplicates immediately.

## Remaining Questions
None. All clarifications from the feature spec and supplemental guidance are addressed.
