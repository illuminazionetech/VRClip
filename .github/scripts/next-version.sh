#!/usr/bin/env bash
# Computes the next VRClip version from the commits since the last release tag, so nobody ever
# edits a version number by hand. Prints GitHub Actions step outputs (key=value lines):
#
#   previous  last release tag (empty when the repository has none)
#   version   next version, MAJOR.MINOR.PATCH
#   tag       the tag that version will be published under (vMAJOR.MINOR.PATCH)
#   bump      major | minor | patch
#   release   true when app code changed since the last tag, so a push to main should publish
#
# Bump rules (Conventional Commits, matched on any commit subject or body line since the tag):
#   "BREAKING CHANGE:" / "BREAKING-CHANGE:" or a "type!:" subject  -> major
#   "feat:" / "feat(scope):"                                       -> minor
#   anything else                                                  -> patch
#
# Only changes under the paths below produce a release; website-only or docs-only pushes don't.
set -euo pipefail

app_paths=(app color buildSrc gradle build.gradle.kts settings.gradle.kts gradle.properties)

previous=$(git describe --tags --abbrev=0 --match 'v[0-9]*.[0-9]*.[0-9]*' 2>/dev/null || true)

if [[ -z "$previous" ]]; then
    base="0.0.0"
    range="HEAD"
    release=true
else
    base="${previous#v}"
    base="${base%%-*}"
    range="${previous}..HEAD"
    if git diff --quiet "$previous" HEAD -- "${app_paths[@]}"; then
        release=false
    else
        release=true
    fi
fi

IFS=. read -r major minor patch <<<"$base"

messages=$(git log --format='%s%n%b' "$range")

if grep -qE '^(BREAKING[ -]CHANGE:|[a-z]+(\([^)]*\))?!:)' <<<"$messages"; then
    bump=major
elif grep -qE '^feat(\([^)]*\))?:' <<<"$messages"; then
    bump=minor
else
    bump=patch
fi

case "$bump" in
    major) major=$((major + 1)); minor=0; patch=0 ;;
    minor) minor=$((minor + 1)); patch=0 ;;
    patch) patch=$((patch + 1)) ;;
esac

version="${major}.${minor}.${patch}"

echo "previous=${previous}"
echo "version=${version}"
echo "tag=v${version}"
echo "bump=${bump}"
echo "release=${release}"
