#!/usr/bin/env python3
"""
Probe Radarr movie fields for:
  - PUT nullability (field set to null)
  - PUT omission behavior (field removed from payload)
  - POST defaults (field omitted on create)
"""

import argparse
import copy
import json
import os
import sys
import time
import urllib.parse
import urllib.request

DEFAULT_FIELDS = [
    "images",
    "genres",
    "sortTitle",
    "cleanTitle",
    "originalTitle",
    "cleanOriginalTitle",
    "originalLanguage",
    "status",
    "lastInfoSync",
    "runtime",
    "inCinemas",
    "physicalRelease",
    "digitalRelease",
    "year",
    "secondaryYear",
    "ratings",
    "recommendations",
    "certification",
    "youTubeTrailerId",
    "studio",
    "overview",
    "website",
    "popularity",
    "collection",
    "minimumAvailability",
]


def die(msg):
    print(msg, file=sys.stderr)
    sys.exit(1)


def request_json(base_url, api_key, method, path, body=None, params=None):
    base_parsed = urllib.parse.urlparse(base_url)
    if base_parsed.scheme not in ("http", "https"):
        raise ValueError(f"Invalid base_url scheme: {base_parsed.scheme!r}")

    path_parsed = urllib.parse.urlparse(path)
    if path_parsed.scheme or path_parsed.netloc:
        raise ValueError("Path must be a relative URL path, not an absolute URL")

    url = base_url.rstrip("/") + path
    url_parsed = urllib.parse.urlparse(url)
    if url_parsed.scheme not in ("http", "https"):
        raise ValueError(f"Invalid request URL scheme: {url_parsed.scheme!r}")
    if params:
        url += "?" + urllib.parse.urlencode(params, doseq=True)
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("X-Api-Key", api_key)
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read()
            if raw:
                return resp.getcode(), json.loads(raw.decode("utf-8"))
            return resp.getcode(), None
    except urllib.error.HTTPError as e:
        raw = e.read()
        payload = None
        if raw:
            try:
                payload = json.loads(raw.decode("utf-8"))
            except Exception:
                payload = {"raw": raw.decode("utf-8", errors="replace")}
        return e.code, payload


def get_quality_profile_id(base_url, api_key):
    status, body = request_json(base_url, api_key, "GET", "/api/v3/qualityProfile")
    if status != 200 or not isinstance(body, list) or not body:
        die("Unable to fetch quality profiles.")
    return body[0]["id"]


def get_root_folder_path(base_url, api_key):
    status, body = request_json(base_url, api_key, "GET", "/api/v3/rootFolder")
    if status != 200 or not isinstance(body, list) or not body:
        die("Unable to fetch root folders.")
    return body[0]["path"]


def get_movie_by_tmdb(base_url, api_key, tmdb_id):
    status, body = request_json(
        base_url, api_key, "GET", "/api/v3/movie", params={"tmdbId": tmdb_id}
    )
    if status == 200 and isinstance(body, list) and body:
        return body[0]
    return None


def ensure_create_payload(payload, base_url, api_key):
    payload = copy.deepcopy(payload)
    payload["id"] = 0
    if "qualityProfileId" not in payload or not payload["qualityProfileId"]:
        payload["qualityProfileId"] = get_quality_profile_id(base_url, api_key)
    if "rootFolderPath" not in payload or not payload["rootFolderPath"]:
        payload["rootFolderPath"] = get_root_folder_path(base_url, api_key)
    if "titleSlug" not in payload and "tmdbId" in payload:
        payload["titleSlug"] = str(payload["tmdbId"])
    if "monitored" not in payload:
        payload["monitored"] = True
    if "addOptions" not in payload:
        payload["addOptions"] = {"monitor": "movieOnly", "searchForMovie": False}
    return payload


def create_movie(base_url, api_key, payload):
    status, body = request_json(base_url, api_key, "POST", "/api/v3/movie", body=payload)
    return status, body


def get_movie(base_url, api_key, movie_id):
    status, body = request_json(base_url, api_key, "GET", f"/api/v3/movie/{movie_id}")
    return status, body


def put_movie(base_url, api_key, payload):
    movie_id = payload.get("id")
    if not movie_id:
        return 0, {"error": "missing id in payload"}
    status, body = request_json(
        base_url, api_key, "PUT", f"/api/v3/movie/{movie_id}", body=payload
    )
    return status, body


def delete_movie(base_url, api_key, movie_id):
    return request_json(
        base_url,
        api_key,
        "DELETE",
        f"/api/v3/movie/{movie_id}",
        params={"deleteFiles": "false", "addImportExclusion": "false"},
    )


def load_fields(args):
    if args.fields:
        return [f.strip() for f in args.fields.split(",") if f.strip()]
    if args.fields_file:
        with open(args.fields_file, "r", encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, list):
            die("--fields-file must be a JSON array of strings.")
        return data
    return DEFAULT_FIELDS


def run():
    parser = argparse.ArgumentParser()
    parser.add_argument("--payload", help="Path to JSON create payload.")
    parser.add_argument("--fields", help="Comma-separated field list.")
    parser.add_argument("--fields-file", help="JSON array of field names.")
    parser.add_argument("--use-existing-id", type=int, help="Skip create, use this id for PUT probes.")
    parser.add_argument("--skip-defaults", action="store_true")
    parser.add_argument("--skip-updates", action="store_true")
    parser.add_argument("--no-cleanup", action="store_true")
    parser.add_argument("--out", help="Write report JSON to this path.")
    args = parser.parse_args()

    base_url = os.environ.get("RADARR_URL")
    api_key = os.environ.get("RADARR_API_KEY")
    if not base_url or not api_key:
        die("RADARR_URL and RADARR_API_KEY must be set in the environment.")

    if not args.payload and not args.use_existing_id:
        die("Provide --payload for create or --use-existing-id for PUT probes.")

    fields = load_fields(args)

    report = {
        "baseUrl": base_url,
        "startedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "defaultsProbe": None,
        "updateProbe": None,
    }

    created_ids = []

    if not args.skip_defaults and args.payload:
        with open(args.payload, "r", encoding="utf-8") as f:
            base_payload = json.load(f)
        base_payload = ensure_create_payload(base_payload, base_url, api_key)
        tmdb_id = base_payload.get("tmdbId")
        if tmdb_id is None:
            die("Payload must include tmdbId.")

        # Delete any existing movie with this tmdbId before starting
        existing = get_movie_by_tmdb(base_url, api_key, tmdb_id)
        if existing:
            print(f"Deleting existing movie with tmdbId {tmdb_id} (id={existing['id']})", file=sys.stderr)
            delete_movie(base_url, api_key, existing["id"])
            time.sleep(1)  # Wait for deletion to complete

        defaults_results = {}
        for field in fields:
            payload = copy.deepcopy(base_payload)
            field_result = {"presentInPayload": field in payload}

            # Remove the field from the payload
            if field in payload:
                payload.pop(field)

            # Create the movie
            status, body = create_movie(base_url, api_key, payload)
            field_result["status"] = status
            field_result["response"] = body

            if status in (200, 201) and body and isinstance(body, dict):
                movie_id = body.get("id")
                created_ids.append(movie_id)
                field_result["observed"] = body.get(field)

                # Clean up immediately to avoid conflicts with next iteration
                if movie_id:
                    delete_movie(base_url, api_key, movie_id)
                    created_ids.remove(movie_id)

            defaults_results[field] = field_result
            time.sleep(0.5)  # Brief pause between creates

        report["defaultsProbe"] = {"results": defaults_results}

    update_target_id = args.use_existing_id
    if update_target_id is None and args.payload:
        with open(args.payload, "r", encoding="utf-8") as f:
            payload = json.load(f)
        payload = ensure_create_payload(payload, base_url, api_key)
        tmdb_id = payload.get("tmdbId")
        if tmdb_id is None:
            die("Payload must include tmdbId.")

        # Delete any existing movie with this tmdbId before creating update probe movie
        existing = get_movie_by_tmdb(base_url, api_key, tmdb_id)
        if existing:
            print(f"Deleting existing movie with tmdbId {tmdb_id} (id={existing['id']}) for update probe", file=sys.stderr)
            delete_movie(base_url, api_key, existing["id"])
            time.sleep(1)  # Wait for deletion to complete

        status, body = create_movie(base_url, api_key, payload)
        if status not in (200, 201) or not isinstance(body, dict):
            die(f"Failed to create update probe movie (status {status}).")
        update_target_id = body.get("id")
        created_ids.append(update_target_id)

    if not args.skip_updates and update_target_id is not None:
        status, baseline = get_movie(base_url, api_key, update_target_id)
        if status != 200 or not isinstance(baseline, dict):
            die("Failed to fetch baseline movie for PUT probes.")
        results = {}
        for field in fields:
            field_result = {"presentInBaseline": field in baseline}

            null_payload = copy.deepcopy(baseline)
            null_payload[field] = None
            null_status, null_body = put_movie(base_url, api_key, null_payload)
            field_result["null"] = {"status": null_status, "response": null_body}
            if null_status in (200, 202):
                _, after = get_movie(base_url, api_key, update_target_id)
                field_result["null"]["observed"] = after.get(field)
            put_movie(base_url, api_key, baseline)

            omit_payload = copy.deepcopy(baseline)
            if field in omit_payload:
                omit_payload.pop(field)
            omit_status, omit_body = put_movie(base_url, api_key, omit_payload)
            field_result["omit"] = {"status": omit_status, "response": omit_body}
            if omit_status in (200, 202):
                _, after = get_movie(base_url, api_key, update_target_id)
                field_result["omit"]["observed"] = after.get(field)
            put_movie(base_url, api_key, baseline)

            results[field] = field_result

        report["updateProbe"] = {"movieId": update_target_id, "results": results}

    if not args.no_cleanup:
        for movie_id in created_ids:
            if movie_id:
                delete_movie(base_url, api_key, movie_id)

    report["finishedAt"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

    output = json.dumps(report, indent=2, sort_keys=True)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(output)
    else:
        print(output)


if __name__ == "__main__":
    run()
