# Feature Specification: Split into systems

**Feature Branch**: `003-split-into-systems`  
**Created**: 2025-10-03  
**Status**: Draft  
**Input**: User description:
> Split into systems
>
> Currently there's a single system for running bamf interactively (through the REPL), the user namespace. Let's develop support for having
> multiple systems (radarr, sonarr, etc) which are loaded by their respective projects. To achieve this we'll move functions that could be
> shared to create, configure, start, stop, restart and monitor these systems into a new component called system and those will be called by
> the system namespaces in each project (dev, radarr, sonarr). For at least the go function we'll use multimethods that take the system as a
> key as well as the environment so we can configure them. Remember to use the polylith architectural guidelines as well as donut.party system
> to configure the systems, run the go function in each system before being done.

## Execution Flow (main)
```
1. Maintainer selects a target system (bamf default via `user` namespace, or specialized variants like radarr, sonarr, etc.) for interactive work.
   → Shared system component resolves the named system and loads lifecycle capabilities.
2. Maintainer triggers the desired lifecycle action (start, stop, restart, monitor, or inspect status/runtime-state/config).
   → Component executes the action with the appropriate environment context.
3. When "start" is requested:
   → Dispatcher uses system identifier + environment to load the correct configuration.
   → System completes startup checks and reports readiness before returning control.
4. Project-specific namespace (dev, radarr, sonarr) exposes entry points that delegate to the shared component.
   → Maintainer receives confirmation for each action.
5. Monitoring feedback remains available while the system is running.
   → Shared component exposes status, runtime-state, and config views so maintainers can observe each system.
```

---

## ⚡ Quick Guidelines
- ✅ Deliver a consistent lifecycle experience across all interactive systems.
- ❌ Avoid duplicating lifecycle logic inside individual project namespaces.
- 👥 Document the maintainer journey so new systems can adopt the shared component smoothly.

### Section Requirements
- **Mandatory sections**: Document lifecycle operations, user scenarios, functional requirements, and key entities for named systems.
- **Optional sections**: Add integration or dependency notes when they impact the maintainer workflow.
- When a section doesn't apply, remove it so the spec remains concise for stakeholders.

### For AI Generation
When preparing implementation plans from this spec:
1. **Confirm monitoring flow**: Ensure status, runtime-state, and config outputs are available to maintainers across systems.
2. **Protect assumptions**: Confirm required environments and readiness criteria for each system.
3. **Think like a tester**: Validate every lifecycle action (start/stop/restart) for success and failure paths.
4. **Common risk areas**:
   - Divergent configuration rules between systems or environments.
   - Lack of visibility into monitoring outputs and error recovery steps.

---

## User Scenarios & Testing *(mandatory)*

### Primary User Story
An operations engineer wants to launch and manage different BAMF system variants (default `bamf` in the `user` namespace plus radarr, sonarr, etc.) from their respective project namespaces without manually wiring lifecycle logic each time.

### Acceptance Scenarios
1. **Given** a maintainer selects the default bamf system via the `user` namespace and requests `start`, **When** the shared system component runs the bamf lifecycle with the specified environment, **Then** the system starts successfully and reports readiness to the maintainer.
2. **Given** a new system namespace adopts the shared component, **When** the maintainer issues start and stop actions through that namespace, **Then** the component executes the actions and confirms the system state transitions without duplicating lifecycle code.

### Edge Cases
- What happens when a maintainer references a system name that is not registered?
- How does the shared component respond if the requested environment configuration is missing or incomplete?
- How does the maintainer recover when the start lifecycle fails mid-startup?

## Requirements *(mandatory)*

### Functional Requirements
- **FR-001**: Platform MUST allow maintainers to choose between multiple named systems (bamf default, radarr, sonarr, and future additions) for interactive sessions.
- **FR-002**: Shared system component MUST provide lifecycle operations to start, stop, restart, and monitor each named system, including access to status, runtime-state, and config views.
- **FR-003**: Each project namespace MUST delegate lifecycle actions to the shared component, ensuring consistent behavior and reducing duplication.
- **FR-004**: The start lifecycle MUST route through a dispatcher that uses system identifier and environment context to apply the correct configuration before reporting readiness.
- **FR-005**: Every system MUST execute the start lifecycle without exposing errors when supplied with valid configuration, and confirm readiness before the maintainer returns to other tasks.
- **FR-006**: System monitoring MUST expose status, runtime-state, and config views so maintainers can inspect active systems without additional tooling.
- **FR-007**: Lifecycle management MUST align with existing Polylith component boundaries and donut.party operational conventions to remain coherent with the platform architecture.
- **FR-008**: Documentation MUST outline how new system namespaces adopt the shared component, including required inputs such as system name and environment identifiers.

### Key Entities *(include if feature involves data)*
- **System Lifecycle Component**: Central capability that delivers lifecycle operations for any registered system; stores mappings between system names, environments, and lifecycle behaviors.
- **Named System Profile**: Conceptual representation of each system variant (default bamf served from the `user` namespace, radarr, sonarr, etc.) including lifecycle hooks and required configuration inputs.
- **Environment Context**: Descriptor for the environment (e.g., local, staging, production) paired with each system action to ensure correct configuration is applied.
- **Monitoring Feedback Channel**: Set of status, runtime-state, and config outputs that surface lifecycle results and ongoing status to maintainers.

---

## Review & Acceptance Checklist
*GATE: Automated checks run during main() execution*

### Content Quality
- [x] No implementation details (languages, frameworks, APIs)
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
