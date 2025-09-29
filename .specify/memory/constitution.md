# BAMF Constitution

## Core Principles

### I. Brick independency
Every brick must be independent; When a brick has functional dependencies on another brick they
must be shared through the dependency injection framework. Every brick may expose its own APIs
and these should be available to a central API routes repository which will expose them.

### II. API Interface
All HTTP API interfaces MUST:
- Accept JSON as input (via the HTTP request)
- Produce JSON as output
- Fully conform to the OpenAPI spec laid out in:
  - [Radarr V3 OpenAPI Spec](https://raw.githubusercontent.com/Radarr/Radarr/develop/src/Radarr.Api.V3/openapi.json)
  - [Sonarr V3 OpenAPI Spec](https://raw.githubusercontent.com/Sonarr/Sonarr/refs/heads/v5-develop/src/Sonarr.Api.V3/openapi.json)

### III. Minimal request processing
Request handlers should be as small as possible. The handlers will be sufficiently small as to be able to
send the data to be persisted by a Rama module. Data processing should be done inside Rama modules.

### IV. Test-First (NON-NEGOTIABLE) -->
TDD mandatory: Tests written → User approved → Tests fail → Then implement; Red-Green-Refactor cycle strictly enforced.

### V. Integration Testing
Focus areas requiring integration tests: New brick contract tests, Shared schemas.

## Additional Constraints
- APIs must have a way to select which system they will target (Sonarr, Radarr, other). This has to be propagated to bricks.
- All code will be implemented in Clojure following the polylith software architecture. This implies we follow their guidelines:
  - Building blocks have different purposes:
    - Bases expose a public API to the outside world. As a result they have an api (api.clj) and an implementation (core.clj).
    - Components are the main building blocks and encapsulate blocks of code that can be assembled together. Consequently they have an
      interface (interface.clj) and an implementation (core.clj).
- All API inputs must be validated, we'll use malli for that.
- All code is implemented implemented taking event sourcing in mind, this must translate well to persistance related operations.
- Logging is an important part of observability, since everything is event sourced and mostly async we need good logging to figure out
  what's happening in the system. We'll use com.taoensso/telemere for that.
- Bricks will be wired using the donut-party.system dependency injection library.

## Quality Gates
No code should be commited with a coverage rate below 80%. Be as exhaustive as possible.

## Governance
Constitution supersedes all other practices; Amendments require documentation, approval, migration plan

Example: Version: 0.0.1 | Ratified: 2025-09-21 | Last Amended:
