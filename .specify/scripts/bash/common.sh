#!/usr/bin/env bash
# Common functions and variables for all scripts

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

detect_vcs_type() {
    if command_exists jj && jj root >/dev/null 2>&1; then
        echo "jj"
        return 0
    fi

    if command_exists git && git rev-parse --show-toplevel >/dev/null 2>&1; then
        echo "git"
        return 0
    fi

    echo "none"
}

get_current_branch_jj() {
    local branch_list first_line branch_name status_output branch_line branches

    if branch_list=$(jj branch list --revisions @ 2>/dev/null); then
        first_line=$(echo "$branch_list" | head -n1 | sed 's/^[[:space:]]*//')
        if [[ -n "$first_line" ]]; then
            branch_name=$(echo "$first_line" | awk '{print $1}')
            if [[ -n "$branch_name" && "$branch_name" != "(no" && "$branch_name" != "@" ]]; then
                echo "$branch_name"
                return 0
            fi
        fi
    fi

    if status_output=$(jj status 2>/dev/null); then
        branch_line=$(echo "$status_output" | grep '^Branches:' | head -n1)
        if [[ -n "$branch_line" ]]; then
            branches=${branch_line#Branches:}
            branches=$(echo "$branches" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
            if [[ -n "$branches" && "$branches" != "(empty)" ]]; then
                branches=$(echo "$branches" | tr ',' ' ')
                for branch_name in $branches; do
                    case "$branch_name" in
                    @ | tracking | origin/* | \(*)
                        continue
                        ;;
                    *)
                        echo "$branch_name"
                        return 0
                        ;;
                    esac
                done
            fi
        fi
    fi

    return 1
}

# Get repository root, with fallback for non-git repositories
get_repo_root() {
    local vcs_type
    vcs_type=$(detect_vcs_type)

    if [[ "$vcs_type" == "git" ]]; then
        git rev-parse --show-toplevel
        return
    fi

    if [[ "$vcs_type" == "jj" ]]; then
        jj root
        return
    else
        # Fall back to script location for non-git repos
        local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        (cd "$script_dir/../../.." && pwd)
    fi
}

# Get current branch, with fallback for non-git repositories
get_current_branch() {
    # First check if SPECIFY_FEATURE environment variable is set
    if [[ -n "${SPECIFY_FEATURE:-}" ]]; then
        echo "$SPECIFY_FEATURE"
        return
    fi

    local vcs_type
    vcs_type=$(detect_vcs_type)

    # Then check git if available
    if [[ "$vcs_type" == "git" ]]; then
        git rev-parse --abbrev-ref HEAD
        return
    fi

    if [[ "$vcs_type" == "jj" ]]; then
        local jj_branch
        if jj_branch=$(get_current_branch_jj); then
            if [[ -n "$jj_branch" ]]; then
                echo "$jj_branch"
                return
            fi
        fi
    fi

    # For non-git repos, try to find the latest feature directory
    local repo_root=$(get_repo_root)
    local specs_dir="$repo_root/specs"

    if [[ -d "$specs_dir" ]]; then
        local latest_feature=""
        local highest=0

        for dir in "$specs_dir"/*; do
            if [[ -d "$dir" ]]; then
                local dirname=$(basename "$dir")
                if [[ "$dirname" =~ ^([0-9]{3})- ]]; then
                    local number=${BASH_REMATCH[1]}
                    number=$((10#$number))
                    if [[ "$number" -gt "$highest" ]]; then
                        highest=$number
                        latest_feature=$dirname
                    fi
                fi
            fi
        done

        if [[ -n "$latest_feature" ]]; then
            echo "$latest_feature"
            return
        fi
    fi

    echo "main" # Final fallback
}

# Check if we have a supported VCS available
has_git() {
    [[ "$(detect_vcs_type)" != "none" ]]
}

check_feature_branch() {
    local branch="$1"
    local vcs_type="${2:-}"

    if [[ -z "$vcs_type" ]]; then
        vcs_type=$(detect_vcs_type)
    fi

    # For non-git repos, we can't enforce branch naming but still provide output
    if [[ "$vcs_type" == "none" ]]; then
        echo "[specify] Warning: Version control repository not detected; skipped branch validation" >&2
        return 0
    fi

    if [[ ! "$branch" =~ ^[0-9]{3}- ]]; then
        local noun="branch"
        [[ "$vcs_type" == "jj" ]] && noun="bookmark"
        echo "ERROR: Not on a feature branch. Current $noun: $branch" >&2
        echo "Feature $noun names should be like: 001-feature-name" >&2
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
    if [[ ! "$branch_name" =~ ^([0-9]{3})- ]]; then
        # If branch doesn't have numeric prefix, fall back to exact match
        echo "$specs_dir/$branch_name"
        return
    fi

    local prefix="${BASH_REMATCH[1]}"

    # Search for directories in specs/ that start with this prefix
    local matches=()
    if [[ -d "$specs_dir" ]]; then
        for dir in "$specs_dir"/"$prefix"-*; do
            if [[ -d "$dir" ]]; then
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

get_feature_paths() {
    local repo_root=$(get_repo_root)
    local current_branch=$(get_current_branch)
    local vcs_type=$(detect_vcs_type)
    local has_git_repo="false"
    local has_jj_repo="false"
    local has_vcs_repo="false"

    case "$vcs_type" in
    git)
        has_git_repo="true"
        has_vcs_repo="true"
        ;;
    jj)
        has_jj_repo="true"
        has_vcs_repo="true"
        ;;
    esac

    # Use prefix-based lookup to support multiple branches per spec
    local feature_dir=$(find_feature_dir_by_prefix "$repo_root" "$current_branch")

    cat <<EOF
REPO_ROOT='$repo_root'
CURRENT_BRANCH='$current_branch'
HAS_GIT='$has_git_repo'
HAS_VCS='$has_vcs_repo'
HAS_JJ='$has_jj_repo'
VCS_TYPE='$vcs_type'
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

check_file() { [[ -f "$1" ]] && echo "  ✓ $2" || echo "  ✗ $2"; }
check_dir() { [[ -d "$1" && -n $(ls -A "$1" 2>/dev/null) ]] && echo "  ✓ $2" || echo "  ✗ $2"; }
