#!/usr/bin/env bash
# Builds the 2D to 3D depth models from the official Depth Anything V2 Small weights with
# tools/depth-model/export.py and checks every file against the size and SHA-256 pinned in
# tools/depth-model/models.json (which the app pins too).
#
#   build-depth-models.sh verify    build and check, nothing is published (pull requests)
#   build-depth-models.sh publish   also upload the files to the `depth-model` release, unless
#                                   the published copies already match (one API call)
#
# The release is a prerelease and never "latest": the website and the in-app updater ignore it.
set -euo pipefail

mode="${1:?verify or publish}"
manifest=tools/depth-model/models.json
tag=$(jq -r .release "$manifest")

if [[ "$mode" == publish ]]; then
    missing=0
    while IFS=$'\t' read -r file sha; do
        published=$(gh api "repos/$GITHUB_REPOSITORY/releases/tags/$tag" \
            --jq ".assets[] | select(.name == \"$file\") | .digest" 2>/dev/null || true)
        [[ "$published" == "sha256:$sha" ]] || missing=1
    done < <(jq -r '.models[] | [.file, .sha256] | @tsv' "$manifest")
    if [[ "$missing" == 0 ]]; then
        echo "Every depth model in $manifest is already published in the $tag release."
        exit 0
    fi
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

python3 -m venv "$work/venv"
# shellcheck disable=SC1091
source "$work/venv/bin/activate"
pip install --quiet --upgrade pip
pip install --quiet -r tools/depth-model/requirements.txt

git clone --quiet "$(jq -r .source.repository "$manifest")" "$work/repo"
git -C "$work/repo" checkout --quiet "$(jq -r .source.commit "$manifest")"

curl -fsSL --retry 3 --retry-delay 5 -o "$work/weights.pth" "$(jq -r .source.weights "$manifest")"
echo "$(jq -r .source.weights_sha256 "$manifest")  $work/weights.pth" | sha256sum --check --quiet

python tools/depth-model/export.py \
    --repo "$work/repo" --weights "$work/weights.pth" --out "$work/out" --verify-pins

if [[ "$mode" != publish ]]; then
    echo "The depth models build reproducibly and match $manifest."
    exit 0
fi

if ! gh release view "$tag" > /dev/null 2>&1; then
    gh release create "$tag" \
        --title "Depth models for 2D to 3D" \
        --prerelease \
        --latest=false \
        --notes "Depth models used by VRClip for 2D to 3D. Not an app release."
fi
notes="Depth Anything V2 Small (Apache-2.0) for VRClip's 2D to 3D conversion, built from the official weights by tools/depth-model/export.py: a GPU-friendly LiteRT graph with float16 weights. This is not an app release."
while IFS=$'\t' read -r file sha; do
    notes+=$'\n\n'"\`$file\`: SHA-256 \`$sha\`"
done < <(jq -r '.models[] | [.file, .sha256] | @tsv' "$manifest")
notes+=$'\n\n'"\`depth_anything_v2_small_518.tflite\` is the earlier Qualcomm AI Hub export, still downloaded by VRClip 1.3."
gh release edit "$tag" --title "Depth models for 2D to 3D" --notes "$notes"
jq -r '.models[].file' "$manifest" | while read -r file; do
    gh release upload "$tag" "$work/out/$file" --clobber
done
echo "Published the depth models to the $tag release."
