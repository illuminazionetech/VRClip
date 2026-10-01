#!/usr/bin/env bash
# Prints the section of RELEASE_NOTES.md written for one version, without its heading, so the
# GitHub Release (and the in-app update dialog that shows it) carries the human-written notes.
# Prints nothing when the file has no section for that version; GitHub's generated list of pull
# requests is still added below by the release step.
#
# Usage: release-notes.sh 1.2.0 [RELEASE_NOTES.md]
set -euo pipefail

version="${1:?version required}"
file="${2:-RELEASE_NOTES.md}"

[[ -f "$file" ]] || exit 0

# A section starts at "## <version>" followed by a space, a dash or the end of the line, and
# ends at the next "## " heading.
awk -v version="$version" '
    /^## / {
        if (found) exit
        rest = substr($0, 4)
        if (index(rest, version) == 1) {
            next_char = substr(rest, length(version) + 1, 1)
            if (next_char == "" || next_char == " " || next_char == "-") { found = 1; next }
        }
    }
    found { print }
' "$file" | sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}'
