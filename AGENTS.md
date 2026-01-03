# bamf Development Guidelines

Auto-generated from all feature plans. Last updated: 2025-12-31

## Active Technologies
- Clojure 1.12.0 with Polylith workspace layout
- Donut System lifecycle management
- Reitit + Aleph HTTP stack with Muuntaja/Charred and Malli validation
- Ring middleware
- Rama persistence with Telemere instrumentation

## Project Structure
```
bases/
  rest-api/
components/
  config/
  movies/
  system/
development/
docs/
projects/
  radarr/
specs/
```

## Commands
- Run unit tests: `clojure -X:test`

## Code Style
Clojure: Prefer pure functions, leverage namespace-qualified keywords, and document public APIs with docstrings/specs.

## Recent Changes
- 005-save-request-metadata: Persist metadata fields on movie saves and updates
- 004-add-rama-movie-module: Added Clojure 1.12.0 + Rama (module + p-states), donut-party.system, Telemere

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
