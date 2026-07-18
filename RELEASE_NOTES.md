# Release notes

## 1.1.0 - Working downloads out of the box, expressive UI, modern toolchain

### Fixes
- Fixed YouTube (and other site) downloads failing on fresh installs. The bundled yt-dlp engine
  is now current (youtubedl-android 0.18.1), updates itself eagerly on first launch, and
  downloads wait for the engine instead of racing its initialization.
- Fixed the in-app auto-updater never running: its preference default was missing, so automatic
  update checks were silently disabled for every install.
- Fixed 32-bit x86 devices being offered the x86_64 APK by the in-app updater; asset selection
  now matches the ABI exactly and falls back through supported ABIs to the universal APK.
- Update checks are no longer skipped on metered networks; only the actual APK download waits
  for explicit confirmation.
- Notification posting is now guarded against the permission being revoked at runtime on
  Android 13 and newer.

### New
- First-run setup: a guided flow requests storage access (All files access on Android 11+, with
  fallbacks for Quest headsets), the notification permission on Android 13+, and shows the
  download engine getting ready. Every step can be skipped and granted later.
- The home screen shows a clear call to action when storage access is missing, and a live status
  row while the download engine is initializing or updating.
- Engine update failures and metered-network postponements are surfaced instead of swallowed.

### UI
- The theme is now built on Material 3 Expressive (material3 1.4) with the expressive motion
  scheme, on top of the existing dynamic color, Monet fallback, and OLED black options.
- Navigation destinations have proper selected/unselected icon pairs with a spring-animated
  icon transition, in the drawer, the rail, and the VR side navigation.
- Expressive loading indicators for engine setup, and assorted dead-code cleanup (unused
  welcome dialog, placeholder menu button, misnamed component file).

### Toolchain
- AGP 8.10.1, Gradle 8.11.1, Kotlin 2.1.21, KSP 2, Room 2.7.2, Compose BOM 2025.12.01,
  Navigation 2.9.8, OkHttp 5.1.0, compileSdk/targetSdk 36.

### Release pipeline
- Release builds are signed with a real keystore when signing secrets are configured, with the
  debug-key fallback kept so releases never block.
- CI runs on feature branch pushes and manual dispatch, and an Android App Bundle is attached
  to releases alongside the per-ABI APKs.

## 1.0.2 - Download site fixes
- Fixed the download button on the website appearing stuck or unresponsive on some mobile and
  Meta Quest browsers, and made it a real link instead of a scripted click for maximum browser
  compatibility, with an always-visible direct-download fallback link.
- Fixed the "Yt-dlp version" field coming back blank in copied error reports on installs where
  the in-app yt-dlp updater had not completed yet, even though a working yt-dlp was present.

## 1.0.1 - Release pipeline fix
- Fixed a CI packaging failure that prevented 1.0.0's release build from completing, and made
  release publishing fully automatic: pushing a version bump to `main` now tags, builds, and
  publishes the GitHub Release (with the in-app auto-updater picking it up) with no manual steps.

## 1.0.0 - Material 3 Expressive rewrite
- Full UI rewrite on Material Design 3 Expressive: the "Liquid Glass" blur/translucent-border
  system is gone, replaced with real M3 tonal surfaces, an expressive shape scale, spring-based
  motion, and dynamic color (Android 12+ wallpaper-based, with an HCT/Monet-derived VR Blue
  scheme as fallback). Meta Quest's larger touch-target/type-scale spatial density carries over
  unchanged.
- New app icon and brand mark: a VR-Blue seal wearing a headset, a nod to Seal, the project this
  app is built on, replacing the previous abstract glyph everywhere (launcher, adaptive
  monochrome icon, notification icon, About screen).
- Renamed the Android package from `com.xrclip` to `com.illuminazionetech.vrclip` and finished
  the internal XRClip to VRClip rebrand (classes, resources, Gradle project name, CI artifacts)
  to match the app's public name.
- Settings cleanup: removed dead sponsor/donation code paths and strings that were never
  reachable from any screen, deduplicated repeated entries between Troubleshooting and
  General/Directory, moved the debug-only "print details" toggle out of the main settings flow,
  and added a real "Follow system" dark theme option alongside On/Off.
- Fixed the in-app updater and all About/Troubleshooting links to point at this repository
  (`illuminazionetech/VRClip`) instead of a stale upstream reference.
- New in-app 3D/360/180/stereoscopic video player for Android (ExoPlayer plus a custom OpenGL
  equirectangular/stereo renderer), replacing "open in an external app" as the default action.
- Fully immersive Meta Spatial SDK player on Meta Quest, with an equirectangular/half-dome panel
  sized and stereo-configured from the detected projection.
- Removed the dead legacy (V1) download UI/orchestration code path; consolidated the live
  custom-command-task tracker into `CommandTaskManager`.
- Added `NOTICE`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, and a Meta Quest Store
  submission readiness doc (`docs/META_QUEST_STORE_SUBMISSION.md`).
- CI now runs a dedicated lint/format-check job on every pull request.
