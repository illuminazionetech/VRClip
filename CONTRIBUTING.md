# Contributing to VRClip

Thanks for considering a contribution. VRClip is a native Android app in Kotlin with Jetpack
Compose; a standard Android toolchain (JDK 21, Android SDK platform 37) is all you need.

## Building locally

```bash
./gradlew assembleGenericDebug        # debug build
./gradlew testGenericDebugUnitTest    # unit tests
./gradlew ktfmtCheck                  # formatting check
./gradlew ktfmtFormat                 # fix formatting
./gradlew lintGenericDebug            # Android Lint
```

The project has two product flavors (`generic`, `githubPreview`) and splits release APKs per ABI
(`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, plus a universal APK), as CI does. Pass `-PnoSplits`
to build a single APK with every ABI.

A release build uses the debug signing key unless a local, gitignored `keystore.properties` exists
at the repository root, so you don't need the release key to build and test.

## Code style

Kotlin formatting is enforced by [ktfmt](https://github.com/facebook/ktfmt) (`ktfmt-gradle`,
Kotlin official style). Run `./gradlew ktfmtFormat` before committing; CI fails a pull request
whose code is not formatted. Android Lint must pass without errors.

## Commit messages and versions

Nobody edits the version number. CI computes it from the commit messages since the last release
tag, following [Conventional Commits](https://www.conventionalcommits.org/):

| Commit | Example | Release |
|---|---|---|
| `feat:` or `feat(scope):` | `feat(player): remember subtitle choice` | minor (1.2.0 to 1.3.0) |
| `type!:` or a `BREAKING CHANGE:` line | `feat!: drop Android 9` | major (1.2.0 to 2.0.0) |
| anything else (`fix:`, `perf:`, `build:`, `docs:`...) | `fix(quest): recenter after resume` | patch (1.2.0 to 1.2.1) |

The rules live in `.github/scripts/next-version.sh`. Local builds take the version of the latest
`v*` tag.

## Releases

A push to `main` that changes the app (`app/`, `color/`, Gradle files) publishes a release by
itself: CI tags the commit, then creates a GitHub Release with the APKs and the App Bundle. It
does so only after lint, unit tests and the build pass, and only when every file carries the
release certificate pinned in `.github/release-signing-cert.sha256`. Website or documentation
changes alone do not publish anything.

The release text is the section of [`RELEASE_NOTES.md`](RELEASE_NOTES.md) whose heading starts
with the new version (for example `## 1.3.0 - ...`), followed by GitHub's list of merged pull
requests. If you know a change will produce a release, add that section in the same pull request;
without it the release lists the pull requests only. The in-app update dialog and the website
show this text.

## Translations

English (`app/src/main/res/values/strings.xml`) is the source text and Italian
(`values-it/`) is kept complete. Other languages live in `values-<code>/strings.xml`: many of
them come from Seal and miss VRClip's newer strings, which then show in English.

To translate, edit or create the `strings.xml` for your language and open a pull request:

- translate only the text between the tags and keep every `name` as it is;
- keep placeholders such as `%1$s` or `%d`, and escape apostrophes as `\'`;
- leave out strings you don't translate: Android falls back to English for them.

The store listing texts are in `fastlane/metadata/android/<locale>/`.

## Project layout

- `app/`: the application module.
  - `ui/`: Compose screens (`ui/page/`), reusable components (`ui/component/`) and shared design
    pieces (`ui/common/`, with motion tokens in `ui/common/motion/`).
  - `player/`: the video player. `PlayerActivity` and `PlayerViewModel` run playback,
    `ProjectionDetector` recognises 360, 180 and 3D video, `gl/` is the OpenGL renderer for
    phones and tablets, `quest/` the Meta Spatial SDK scene, and `stereo/` the 2D to 3D pipeline
    (depth model download and inference, the Media3 effect, the background conversion worker).
  - `download/`: the download engine (`DownloaderV2`) and custom command tasks
    (`CommandTaskManager`).
  - `database/`: Room entities and DAOs for download history, cookies and command templates.
  - `util/`: preferences, updates (`AppUpdateManager`, `UpdateUtil`) and helpers.
- `color/`: a library module implementing Material You / HCT dynamic color.
- `docs/`: the website published on GitHub Pages, in English and Italian.
- `fastlane/metadata/android/`: store listing metadata per locale.

## Pull requests

- Keep pull requests focused; unrelated refactors make review harder.
- Run the commands above before opening one: CI runs the same checks.
- Describe what changed and why; the diff already shows how.
