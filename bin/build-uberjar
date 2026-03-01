#!/usr/bin/env bash
set -euo pipefail

# Build Rama module uberjars via root build.clj (parameterized by component).
# Usage:
#   ./scripts/build-uberjar.sh                    # build all configured components
#   ./scripts/build-uberjar.sh movies             # build only the movies component
#   ./scripts/build-uberjar.sh movies --env dev   # pass env through to build (optional)

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

COMPONENT=""
EXTRA_ARGS=()

if [[ $# -gt 0 && $1 != --* ]]; then
	COMPONENT="$1"
	shift
fi

EXTRA_ARGS=("$@")

if [[ -n ${COMPONENT} ]]; then
	echo "=> Building component '${COMPONENT}'"
	clojure -T:build uber :component \""${COMPONENT}"\" "${EXTRA_ARGS[@]}"
else
	echo "=> Building all configured components"
	clojure -T:build uber-all "${EXTRA_ARGS[@]}"
fi

echo "=> Done."
