#!/usr/bin/env bash
# Common functions and variables for all scripts

# has_git checks whether Git is installed and the current directory is inside a Git repository.
has_git() {
	command -v git >/dev/null 2>&1 && git rev-parse --show-toplevel >/dev/null 2>&1
}

# has_jj returns success when `jj` is installed and the current directory is inside a jj repository.
has_jj() {
	command -v jj >/dev/null 2>&1 && jj root >/dev/null 2>&1
}

# get_vcs_type identifies the version control system in use and echoes "jj" if a JJ repository is active, "git" if a Git repository is active, or "none" otherwise.
get_vcs_type() {
	if has_jj; then
		echo "jj"
		return
	fi

	if has_git; then
		echo "git"
		return
	fi

	echo "none"
}

# get_repo_root prints the repository root path based on the detected VCS or a fallback derived from the script's location.
# If a JJ repository is detected it outputs `jj root`; if a Git repository is detected it outputs the Git top-level directory;
# otherwise it prints the directory three levels above the script's location as a non-VCS fallback.
get_repo_root() {
	local vcs_type
	vcs_type=$(get_vcs_type)

	if [[ $vcs_type == "jj" ]]; then
		jj root
	elif [[ $vcs_type == "git" ]]; then
		git rev-parse --show-toplevel
	else
		# Fall back to script location for non-vcs repos
		local script_dir="$(CDPATH="" cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
		(cd "$script_dir/../../.." && pwd)
	fi
}

# get_jj_current_bookmark prints the current JJ bookmark name representing the active feature, preferring a bookmark that begins with a three-digit numeric prefix (e.g., `001-...`); if no such bookmark exists it prints the first bookmark found, and prints nothing if no bookmarks are available.
get_jj_current_bookmark() {
	local bookmarks raw_bookmarks
	raw_bookmarks=$(jj bookmark list --revisions @ 2>/dev/null || true)
	bookmarks=$(printf '%s\n' "$raw_bookmarks" | sed 's/^[[:space:]]*//; s/:.*//; s/[[:space:]].*//; s/@.*//' | sed '/^$/d')

	if [[ -z $bookmarks ]]; then
		local status_line raw_status
		raw_status=$(jj status --color never 2>/dev/null || true)
		status_line=$(printf '%s\n' "$raw_status" | sed -n 's/^Bookmarks:[[:space:]]*//p')
		if [[ -n $status_line ]]; then
			bookmarks=$(printf '%s\n' "$status_line" | tr ',' '\n' | sed 's/^ *//; s/ *$//; s/@.*//')
		fi
	fi

	local match
	match=$(printf '%s\n' "$bookmarks" | awk '/^[0-9]{3}-/ {print; exit}')
	if [[ -n $match ]]; then
		echo "$match"
		return
	fi

	printf '%s\n' "$bookmarks" | awk 'NF {print; exit}'
}

# get_current_branch determines the current feature branch or identifier by returning, in order: the SPECIFY_FEATURE env value (if set), the current JJ bookmark (if in a JJ repo), the current Git branch (if in a Git repo), the latest numeric-prefixed directory under `specs/`, and otherwise "main".
get_current_branch() {
	# First check if SPECIFY_FEATURE environment variable is set
	if [[ -n ${SPECIFY_FEATURE:-} ]]; then
		echo "$SPECIFY_FEATURE"
		return
	fi

	local vcs_type
	vcs_type=$(get_vcs_type)

	if [[ $vcs_type == "jj" ]]; then
		local current_bookmark
		current_bookmark=$(get_jj_current_bookmark)
		if [[ -n $current_bookmark ]]; then
			echo "$current_bookmark"
			return
		fi
	fi

	# Then check git if available
	if [[ $vcs_type == "git" ]] && git rev-parse --abbrev-ref HEAD >/dev/null 2>&1; then
		git rev-parse --abbrev-ref HEAD
		return
	fi

	# For non-vcs repos, try to find the latest feature directory
	local repo_root=$(get_repo_root)
	local specs_dir="$repo_root/specs"

	if [[ -d $specs_dir ]]; then
		local latest_feature=""
		local highest=0

		for dir in "$specs_dir"/*; do
			if [[ -d $dir ]]; then
				local dirname=$(basename "$dir")
				if [[ $dirname =~ ^([0-9]{3})- ]]; then
					local number=${BASH_REMATCH[1]}
					number=$((10#$number))
					if [[ $number -gt $highest ]]; then
						highest=$number
						latest_feature=$dirname
					fi
				fi
			fi
		done

		if [[ -n $latest_feature ]]; then
			echo "$latest_feature"
			return
		fi
	fi

	echo "main" # Final fallback
}

# check_feature_branch checks whether a branch name follows the required three-digit feature prefix (e.g., 001-feature-name); it prints an error or warning as appropriate and exits non-zero for invalid feature names.
check_feature_branch() {
	local branch="$1"
	local vcs_type="$2"

	# Backwards compatibility: accept boolean values
	if [[ $vcs_type == "true" ]]; then
		vcs_type="git"
	elif [[ $vcs_type == "false" || -z $vcs_type ]]; then
		vcs_type="none"
	fi

	# For non-vcs repos, we can't enforce branch naming but still provide output
	if [[ $vcs_type == "none" ]]; then
		echo "[specify] Warning: No VCS detected; skipped feature validation" >&2
		return 0
	fi

	if [[ ! $branch =~ ^[0-9]{3}- ]]; then
		if [[ $vcs_type == "jj" ]]; then
			echo "ERROR: Not on a feature bookmark. Current feature: $branch" >&2
			echo "Feature bookmarks should be named like: 001-feature-name" >&2
			echo "Tip: run 'jj bookmark create -r @ 001-feature-name' or set SPECIFY_FEATURE" >&2
		else
			echo "ERROR: Not on a feature branch. Current branch: $branch" >&2
			echo "Feature branches should be named like: 001-feature-name" >&2
		fi
		return 1
	fi

	return 0
}

get_feature_dir() { echo "$1/specs/$2"; }

# Find feature directory by numeric prefix instead of exact branch match
# This allows multiple branches to work on the same spec (e.g., 004-fix-bug, 004-add-feature)
find_feature_dir_by_prefix() {
	local repo_root="$1"
	local branch_name="$2"
	local specs_dir="$repo_root/specs"

	# Extract numeric prefix from branch (e.g., "004" from "004-whatever")
	if [[ ! $branch_name =~ ^([0-9]{3})- ]]; then
		# If branch doesn't have numeric prefix, fall back to exact match
		echo "$specs_dir/$branch_name"
		return
	fi

	local prefix="${BASH_REMATCH[1]}"

	# Search for directories in specs/ that start with this prefix
	local matches=()
	if [[ -d $specs_dir ]]; then
		for dir in "$specs_dir"/"$prefix"-*; do
			if [[ -d $dir ]]; then
				matches+=("$(basename "$dir")")
			fi
		done
	fi

	# Handle results
	if [[ ${#matches[@]} -eq 0 ]]; then
		# No match found - return the branch name path (will fail later with clear error)
		echo "$specs_dir/$branch_name"
	elif [[ ${#matches[@]} -eq 1 ]]; then
		# Exactly one match - perfect!
		echo "$specs_dir/${matches[0]}"
	else
		# Multiple matches - this shouldn't happen with proper naming convention
		echo "ERROR: Multiple spec directories found with prefix '$prefix': ${matches[*]}" >&2
		echo "Please ensure only one spec directory exists per numeric prefix." >&2
		echo "$specs_dir/$branch_name" # Return something to avoid breaking the script
	fi
}

# get_feature_paths prints a block of environment-style variable assignments describing the current feature context.
# It emits the following variables: REPO_ROOT, CURRENT_BRANCH, VCS_TYPE, HAS_GIT, FEATURE_DIR, FEATURE_SPEC, IMPL_PLAN, TASKS, RESEARCH, DATA_MODEL, QUICKSTART, and CONTRACTS_DIR.
get_feature_paths() {
	local repo_root=$(get_repo_root)
	local current_branch=$(get_current_branch)
	local vcs_type
	vcs_type=$(get_vcs_type)
	local has_git_repo="false"

	if [[ $vcs_type == "git" ]]; then
		has_git_repo="true"
	fi

	# Use prefix-based lookup to support multiple branches per spec
	local feature_dir=$(find_feature_dir_by_prefix "$repo_root" "$current_branch")

	cat <<EOF
REPO_ROOT='$repo_root'
CURRENT_BRANCH='$current_branch'
VCS_TYPE='$vcs_type'
HAS_GIT='$has_git_repo'
FEATURE_DIR='$feature_dir'
FEATURE_SPEC='$feature_dir/spec.md'
IMPL_PLAN='$feature_dir/plan.md'
TASKS='$feature_dir/tasks.md'
RESEARCH='$feature_dir/research.md'
DATA_MODEL='$feature_dir/data-model.md'
QUICKSTART='$feature_dir/quickstart.md'
CONTRACTS_DIR='$feature_dir/contracts'
EOF
}

check_file() { [[ -f $1 ]] && echo "  ✓ $2" || echo "  ✗ $2"; }
check_dir() { [[ -d $1 && -n $(ls -A "$1" 2>/dev/null) ]] && echo "  ✓ $2" || echo "  ✗ $2"; }