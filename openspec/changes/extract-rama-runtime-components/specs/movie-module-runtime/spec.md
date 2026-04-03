## ADDED Requirements

### Requirement: Movies runtime SHALL expose canonical lifecycle entrypoints
The system SHALL expose movie runtime lifecycle through `bamf.movies.interface/start!` and `bamf.movies.interface/stop!`. `start!` SHALL accept a config map that includes a provided `:rama` runtime and MAY include launch or runtime options needed to start or connect the movie module.

#### Scenario: BAMF and Radarr use the same runtime lifecycle names
- **WHEN** BAMF and Radarr Donut systems start or stop the movies runtime
- **THEN** both systems SHALL call the same canonical `start!` and `stop!` interface functions

### Requirement: Movies runtime SHALL depend on a provided Rama runtime
The system SHALL start or connect the movies module from a provided Rama runtime dependency rather than by having the movies component create the application's Rama runtime internally.

#### Scenario: Movies runtime starts against local Rama dependency
- **WHEN** the BAMF Donut graph starts the movies runtime in local development
- **THEN** the movies runtime SHALL use the provided Rama dependency to launch or connect the movie module

#### Scenario: Movies runtime does not own global runtime creation
- **WHEN** the movies runtime starts
- **THEN** it SHALL NOT be responsible for creating the application's global Rama runtime

### Requirement: Movies runtime SHALL model module ownership separately from runtime ownership
The system SHALL represent movie-module lifecycle ownership independently from application runtime ownership so the movies runtime can deploy, update, or connect to a movie module without redefining who owns the underlying Rama runtime.

#### Scenario: Movies runtime owns the module but not necessarily the runtime
- **WHEN** BAMF launches or updates the movie module from a provided Rama runtime
- **THEN** the movies runtime boundary SHALL be able to indicate movie-module ownership independently from Rama runtime ownership

#### Scenario: Movies runtime attaches to an existing module-capable runtime
- **WHEN** BAMF connects movie-specific handles from a provided Rama runtime
- **THEN** movie-module lifecycle metadata SHALL remain distinct from the runtime handle itself

### Requirement: Movie persistence and inspection SHALL use the neutral Rama client boundary
The system SHALL keep movie depot appends driven by explicit movie-specific depot handles while moving movie PState inspection away from raw `:ipc` fallback chains and onto the shared Rama client abstraction.

#### Scenario: Persistence appends through explicit depot handle
- **WHEN** movie persistence code appends to the movie depot
- **THEN** it SHALL do so from the explicit movie depot handle constructed for the movies runtime rather than by looking up a raw `:ipc` handle

#### Scenario: Inspection reads through shared client boundary
- **WHEN** movie inspection code queries movie PStates
- **THEN** it SHALL resolve those PState handles from the shared Rama client boundary rather than by assuming a raw `:ipc` path in the environment
