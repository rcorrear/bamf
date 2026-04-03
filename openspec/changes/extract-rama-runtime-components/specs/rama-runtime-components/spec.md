## ADDED Requirements

### Requirement: System SHALL provide a neutral Rama client runtime boundary
The system SHALL provide a `bamf.rama-client` component that wraps local Rama IPC ownership behind a neutral runtime handle and exposes the client-side foreign-operation helpers needed by downstream components without requiring those components to assume raw `:ipc` values.

#### Scenario: Local runtime is wrapped as a neutral handle
- **WHEN** BAMF starts a local Rama runtime for application-side use
- **THEN** the resulting runtime value SHALL be represented through the `bamf.rama-client` boundary rather than as an unstructured raw `:ipc` handle

#### Scenario: Downstream code performs foreign operations
- **WHEN** BAMF code needs to construct a foreign depot, PState, or query client
- **THEN** it SHALL do so through `bamf.rama-client` helpers derived from the neutral runtime handle

### Requirement: System SHALL track Rama runtime ownership separately from downstream consumers
The system SHALL model whether the application owns the Rama runtime it is using so shutdown behavior can close local runtimes it created while leaving externally provided runtimes alone.

#### Scenario: Application-owned local runtime shuts down
- **WHEN** BAMF stops a local runtime that it created
- **THEN** the neutral Rama runtime boundary SHALL identify that it owns the runtime and SHALL support closing it through the shared client boundary

#### Scenario: Non-owned runtime is consumed
- **WHEN** BAMF code receives a Rama runtime that the application did not create
- **THEN** the shared client boundary SHALL preserve that it does not own the runtime

### Requirement: System SHALL provide a server-side Rama helper boundary
The system SHALL provide a `bamf.rama-server` component and public namespace for helper behavior that belongs inside Rama worker or task-thread execution contexts rather than in application-side Donut composition or domain components.

#### Scenario: Server-side boundary is established
- **WHEN** this change is implemented
- **THEN** the workspace SHALL contain a dedicated `bamf.rama-server` component and public namespace as the canonical home for worker-side Rama helpers

#### Scenario: Worker-side helper is added in the future
- **WHEN** BAMF module worker code requires shared server-side Rama integration behavior
- **THEN** that helper SHALL be defined under the `bamf.rama-server` boundary rather than under a movie-domain namespace
