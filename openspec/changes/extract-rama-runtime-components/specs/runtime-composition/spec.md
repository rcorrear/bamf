## ADDED Requirements

### Requirement: System SHALL compose Rama as an explicit Donut dependency
The system SHALL compose BAMF runtime graphs with an explicit Rama node that other runtime-managed components depend on through the Donut graph rather than by creating Rama runtime state internally.

#### Scenario: Movies depends on Rama through the graph
- **WHEN** BAMF starts a Donut system that includes the movies runtime
- **THEN** the movies runtime SHALL receive its Rama dependency from an explicit Donut-managed Rama node

#### Scenario: Shared runtime can support multiple consumers
- **WHEN** more than one BAMF runtime-managed component requires Rama access
- **THEN** those components SHALL be able to depend on the same explicit Rama graph node

### Requirement: System SHALL start runtimes through explicit startup functions
The system SHALL expose runtime startup through plain functions that load the relevant development namespace and start the selected Donut system directly, without routing startup through a `:go` defmethod.

#### Scenario: Starting BAMF runtime
- **WHEN** a developer calls the BAMF REPL startup helper
- **THEN** startup SHALL load the BAMF dev system namespace and start the selected Donut system without dispatching through a `:go` start method

#### Scenario: Starting Radarr runtime
- **WHEN** a developer calls the Radarr REPL startup helper
- **THEN** startup SHALL load the Radarr dev system namespace and start the selected Donut system without dispatching through a `:go` start method

### Requirement: System SHALL remove multimethod startup dispatch from `bamf.system.interface`
The system SHALL replace the current startup multimethod and `:go` dispatch path in `bamf.system.interface` with explicit startup functions while preserving the existing REPL command surface.

#### Scenario: Shared startup facade remains available
- **WHEN** a developer uses the shared system interface to start BAMF or Radarr
- **THEN** the interface SHALL provide explicit startup functions instead of a `defmulti`/`defmethod` startup chain

### Requirement: System SHALL keep per-system composition differences as overlays
The system SHALL keep shared runtime graph construction separate from per-system overlays so BAMF and Radarr can vary by system-specific registrations without duplicating most startup wiring.

#### Scenario: BAMF and Radarr differ by route registration
- **WHEN** BAMF and Radarr define different HTTP component registrations
- **THEN** their Donut composition SHALL express that difference through small overlays on a shared runtime graph pattern rather than through mostly duplicated startup definitions
