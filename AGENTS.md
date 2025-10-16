# bamf Development Guidelines

Auto-generated from all feature plans. Last updated: 2025-10-03

## Active Technologies
- Clojure 1.12.0 + Rama (001-develop-bamf-a)
- Clojure 1.12.0 + Rama (module + p-states), donut-party.system, Telemere (004-add-rama-movie-module)
- Rama Movies p-state backed by ModuleUniqueIdPState id generation (004-add-rama-movie-module)

## Project Structure
```
bases/
  rest-api/
components/
  api/
  config/
  movies/
development/
docs/
specs/
```

## Commands
- Run unit tests: `clojure -X:test`

## Code Style
Clojure: Prefer pure functions, leverage namespace-qualified keywords, and document public APIs with docstrings/specs.

## Recent Changes
- 004-add-rama-movie-module: Added Clojure 1.12.0 + Rama (module + p-states), donut-party.system, Telemere
- 003-split-into-systems: Centralized lifecycle component with multimethod start/stop/monitoring (donut-party + Telemere)
- 002-modular-apis: Implemented component route aggregation with Reitit + Telemere diagnostics (Donut DI)
- 001-develop-bamf-a: Added Save Movies persistence design (Clojure + Rama)

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
