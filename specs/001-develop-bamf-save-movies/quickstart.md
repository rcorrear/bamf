# Quickstart – Save Movies Persistence

## Prerequisites
- Java 21+ and Clojure CLI tools installed (per repository README).
- Rama environment configured (depot + pstate processes) according to Bamf developer guide.
- Feature branch `001-develop-bamf-a` checked out.

## 1. Run Tests First (TDD)
```bash
clojure -X:test :only bamf.api.save-movies-test
clojure -X:test :only bamf.rama.movies.depot-test
clojure -X:test :only bamf.rama.movies.pstate-test
```
Expect failures until implementation completes.

## 2. Start Local Services
```bash
clojure -M:dev run
```
Ensure the Save Movies endpoint is listening (default `http://localhost:9090`).

## 3. Submit Sample Movie
```bash
curl -X POST http://localhost:9090/api/v3/movie \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Dune",
    "qualityProfileId": 1,
    "path": "/movies/Dune (2021)",
    "titleSlug": "12345",
    "rootFolderPath": "/movies",
    "monitored": true,
    "tmdbId": 12345,
    "movieFileId": 0,
    "minimumAvailability": "released",
    "tags": ["scifi", "4k"],
    "addOptions": {"searchForMovie": true}
  }'
```
You should receive `201 Created` with persisted identifiers.

## 4. Verify Rama State
- Inspect depot event stream via Rama CLI:
  ```bash
  rama inspect movie.saved --tail 5
  ```
- Query pstate for record:
  ```bash
  rama query movie/by-tmdb-id --key 12345
  ```

## 5. Duplicate Submission Check
Repeat the POST with the same `tmdbId` or `path`; expect a `400` duplicate response and no new event.

## 6. Clean Up
- Stop local services (`Ctrl+C`).
- Clear Rama depot if needed for fresh runs:
  ```bash
  rama depot truncate movie
  ```
