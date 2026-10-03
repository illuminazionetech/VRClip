# Depth models

VRClip's 2D to 3D conversion estimates depth with Depth Anything V2 Small (Apache-2.0). The app
downloads two LiteRT models from the `depth-model` release of this repository:

| Model | Input | Used for |
| --- | --- | --- |
| `depth_anything_v2_small_live_364.tflite` | 364 × 364 | live 3D, when the device is not fast enough for the full model |
| `depth_anything_v2_small_full_518.tflite` | 518 × 518 | conversion to a 3D file, and live 3D on fast devices |

Both are built here from the official weights. `export.py` rebuilds the network with operations
the LiteRT GPU delegate runs on common mobile GPUs (no tensor above four dimensions, no transposed
convolutions), checks the result against the reference model and stores the weights in float16.

`models.json` pins the source commit, the weights and the SHA-256 of every output. The app pins
the same values in `DepthModel.kt`, and `DepthModelPinsTest` fails when the two drift apart.

## CI

The `depth-models` job in `.github/workflows/build.yml` runs `.github/scripts/build-depth-models.sh`:

- on pull requests that touch this folder, it builds the models and checks them against the pins;
- on pushes to `main` and on manual runs, it uploads them to the release when the published copies
  differ.

## Building locally

```sh
python3.11 -m venv venv && . venv/bin/activate
pip install -r tools/depth-model/requirements.txt
git clone https://github.com/DepthAnything/Depth-Anything-V2.git
git -C Depth-Anything-V2 checkout "$(jq -r .source.commit tools/depth-model/models.json)"
curl -fLo vits.pth "$(jq -r .source.weights tools/depth-model/models.json)"
python tools/depth-model/export.py --repo Depth-Anything-V2 --weights vits.pth --out out \
    --verify-pins --selftest-dir app/src/main/assets/depth
```

`--selftest-dir` rewrites the reference depth grids that the app compares with a test inference
before it trusts the GPU, to catch drivers that compute the model wrongly in float16. Run it
whenever the export changes, and update the sizes and SHA-256 in `models.json` and `DepthModel.kt`
together.
