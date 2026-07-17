# Contributing to VRClip

Thanks for considering a contribution! VRClip is a native Android/Kotlin app (Jetpack
Compose), so contributing doesn't require anything beyond a standard Android
toolchain.

## Building locally

```bash
./gradlew assembleGenericDebug   # fast local debug build
./gradlew test                   # unit tests
./gradlew ktfmtCheck             # code formatting check
./gradlew ktfmtFormat             # auto-fix formatting
./gradlew lintGenericDebug        # Android Lint
```

The project has two product flavors (`generic`, `githubPreview`) and splits
release APKs per ABI (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, plus a
universal fallback) — this is also what CI produces, so
`./gradlew assembleGenericDebug` matches CI locally. Pass `-PnoSplits` to build a
single fat APK bundling all ABIs instead (useful for quick local testing on an
emulator without waiting on the split matrix).

A release build falls back to the debug signing key unless a local (gitignored)
`keystore.properties` exists at the repo root — you don't need a real signing key
to build and test locally.

## Code style

Kotlin formatting is enforced by [ktfmt](https://github.com/facebook/ktfmt) via the
`ktfmt-gradle` plugin, using Kotlin's official style. Run `./gradlew ktfmtFormat`
before committing; CI runs `ktfmtCheck` on every pull request.

## Project layout

- `app/` — the application module.
  - `ui/` — Jetpack Compose screens (`ui/page/`), reusable components
    (`ui/component/`), and shared design-system pieces (`ui/common/`, including
    the Liquid Glass system under `ui/common/glass/`).
  - `player/` — the in-app 3D/360°/XR video player: projection detection
    (`ProjectionDetector`), the shared ExoPlayer wrapper (`PlayerEngine`), the
    phone/tablet OpenGL renderer (`player/gl/`), and the Meta Quest immersive
    Spatial SDK scene (`player/quest/`).
  - `download/` — the active download engine (`DownloaderV2`) and custom-command
    task tracking (`CommandTaskManager`).
  - `database/` — Room entities/DAO for download history, cookies, command
    templates.
- `color/` — a standalone library module implementing Material You / HCT dynamic
  color.
- `fastlane/metadata/android/` — per-locale store listing metadata.

## Pull requests

- Keep PRs focused; unrelated refactors make review harder.
- Run the commands above before opening a PR — CI will run the same checks.
- Describe what changed and why, not just what — the diff already shows what.
