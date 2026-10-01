#!/usr/bin/env bash
# Publishes the depth model used for 2D to 3D as an asset of the `depth-model` release, so the
# app has a second download source next to Qualcomm AI Hub (see DepthModelManager.MIRROR_URL).
# The file is checked against the SHA-256 pinned in DepthModelManager.kt before it is uploaded,
# and nothing is downloaded when the published copy already matches.
#
# The release is a prerelease and never "latest": the website and the in-app updater ignore it.
set -euo pipefail

kotlin=app/src/main/java/com/illuminazionetech/vrclip/player/stereo/DepthModelManager.kt
source_url="https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/depth_anything_v2/releases/v0.63.0/depth_anything_v2-tflite-float.zip"
tag=depth-model

sha=$(sed -n 's/.*MODEL_SHA256 = "\([0-9a-f]\{64\}\)".*/\1/p' "$kotlin")
name=$(sed -n 's/.*MODEL_FILE_NAME = "\([^"]*\)".*/\1/p' "$kotlin")
if [[ -z "$sha" || -z "$name" ]]; then
    echo "::error file=$kotlin::cannot read the pinned model name and SHA-256"
    exit 1
fi

published=$(gh api "repos/$GITHUB_REPOSITORY/releases/tags/$tag" \
    --jq ".assets[] | select(.name == \"$name\") | .digest" 2>/dev/null || true)
if [[ "$published" == "sha256:$sha" ]]; then
    echo "$name is already published in the $tag release."
    exit 0
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
curl -fsSL --retry 3 --retry-delay 5 -o "$work/model.zip" "$source_url"
unzip -q -j "$work/model.zip" '*.tflite' -d "$work/unpacked"
file=$(find "$work/unpacked" -name '*.tflite' | head -n 1)
actual=$(sha256sum "$file" | cut -d' ' -f1)
if [[ "$actual" != "$sha" ]]; then
    echo "::error::the model from Qualcomm AI Hub has SHA-256 $actual, expected $sha"
    exit 1
fi
mv "$file" "$work/$name"

if ! gh release view "$tag" > /dev/null 2>&1; then
    gh release create "$tag" \
        --title "Depth model for 2D to 3D" \
        --prerelease \
        --latest=false \
        --notes "Depth Anything V2 Small (Apache-2.0), TFLite export by Qualcomm AI Hub, SHA-256 \`$sha\`. VRClip downloads it from here when the original source cannot be reached. This is not an app release."
fi
gh release upload "$tag" "$work/$name" --clobber
echo "Published $name to the $tag release."
