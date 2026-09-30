#!/usr/bin/env bash
# Verifies that every APK and App Bundle passed as an argument is signed with the VRClip release
# key, whose certificate SHA-256 digest is pinned in .github/release-signing-cert.sha256 (a public
# value, not a secret). Android refuses to install an update signed with a different key, so a
# release built without the signing secrets must never be published: the in-app updater would
# offer it and the install would fail for every user.
#
# Exit status: 0 when every file matches, 1 otherwise.
set -euo pipefail

expected=$(tr -d '[:space:]:' <"$(dirname "$0")/../release-signing-cert.sha256" | tr '[:upper:]' '[:lower:]')

apksigner=$(ls -d "${ANDROID_HOME:-/usr/local/lib/android/sdk}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1)
if [[ -z "$apksigner" ]]; then
    echo "::error::apksigner not found in the Android SDK build-tools"
    exit 1
fi

status=0
for file in "$@"; do
    case "$file" in
        *.apk)
            actual=$("$apksigner" verify --print-certs "$file" |
                sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1)
            ;;
        *.aab)
            actual=$(keytool -printcert -jarfile "$file" 2>/dev/null |
                sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1 | tr -d ':' | tr '[:upper:]' '[:lower:]')
            ;;
        *)
            continue
            ;;
    esac
    if [[ "$actual" == "$expected" ]]; then
        echo "OK   $(basename "$file")"
    else
        echo "::error file=$file::$(basename "$file") is not signed with the release key (got '${actual:-none}')"
        status=1
    fi
done
exit $status
