# Release notes

## 1.3.0 - Reliable 3D model download, expressive player and a new website

### 2D to 3D
- The depth model download no longer starts over or stops with a generic error. It runs in the
  background with a progress notification and a Cancel button, keeps going when you leave the
  app, resumes after a dropped connection, retries, and checks free space before it starts.
  When it fails it says why: no connection, not enough space, or a file that did not pass the
  check.
- If Qualcomm AI Hub cannot be reached, the same model is downloaded from VRClip's GitHub
  releases and checked against the same SHA-256.
- The player settings and the model dialog show the download as it runs, with size and
  percentage.

### Player
- New controls built on Material 3 Expressive: a play button that changes shape while playing,
  seek buttons that spin with the jump, a seek bar that shows the buffered range and a still of
  the frame under your finger while you scrub, and the secondary actions in a floating toolbar.
  Tap the duration to see the remaining time.
- Haptic feedback follows the same rules everywhere: ticks for buttons and while scrubbing,
  clicks for toggles, a stronger bump when a long press or a drag takes hold and when
  brightness or volume reaches its end.
- The speed sheet has step buttons and presets and applies the speed as you change it; track
  and projection sheets mark the current choice; deleting a video asks for confirmation.
- Controls over the video are easier to read on bright scenes, and the resume message no longer
  covers the seek buttons.
- On phones the player settings show only phone options; Meta Quest options appear only on
  Meta Quest.

### Meta Quest
- The control bar uses the same controls as the phone player and adds pages for playback speed
  and audio and subtitle tracks. The 3D model page shows the download and can cancel it.
- The video layer is drawn behind the control bar, so a 360 video can no longer cover it, and
  flat screens use supersampling to reduce shimmer.

### Downloads
- The "VR mode" preset is now "High resolution for 360° and 3D". It asks for the highest
  resolution the device's hardware decoders can play, with the codec that reaches it, instead
  of a fixed 8K AV1 video that many phones and Quest 2 cannot decode.
- The site filters in the library keep a stable order: by number of videos, then by name.

### Interface
- Onboarding illustrations morph from one shape into the next, empty pages move slowly, and
  settings switches give toggle haptics.

### Website
- New design with light and dark themes, an interactive 2D to 3D demo and screenshots of this
  version.

## 1.2.0 - New player, 2D to 3D, a working Quest scene and verified updates

### Updating from 1.1.0 or older
- 1.2.0 is signed with VRClip's new permanent release key. Android does not let an app update
  to a version signed with a different key, so 1.1.0 cannot update itself to 1.2.0: uninstall
  VRClip, then install 1.2.0. Files you downloaded stay in Download/VRClip; the library list,
  settings and history are removed with the old app. Later versions update in place.

### Player
- The player opens full screen in its own window and can continue in picture-in-picture while
  you use VRClip or other apps. It also opens local videos shared from other apps.
- Playback resumes where you stopped (library thumbnails show a progress bar) and the speed
  you chose is remembered.
- Audio and subtitle tracks can be chosen, including subtitle files yt-dlp saved next to the
  video.
- Gestures: double tap to seek 10 seconds, long press for 2x, horizontal swipe to scrub,
  vertical swipes for brightness and volume, pinch to fill the screen. Also controls lock,
  repeat, rotation, and a media session so headset buttons control playback.
- 360 and 180 video starts straight ahead and upright, 180 maps onto a half dome, the phone's
  motion sensor turns the view, split screen keeps the right aspect per eye, and a red and cyan
  anaglyph output joins single eye and split screen.
- Projection detection reads the stereo and spherical metadata in the file, understands more
  file name tags (such as `_360` or `vr180_sbs`) and no longer takes "Xbox 360" for a 360
  degree video.

### 2D to 3D
- In the player, the 3D button turns a flat video into side-by-side 3D while it plays.
- In the library, Convert to 3D renders the whole file at full quality in the background, with
  a notification, and replaces the flat file only after the new one is complete and checked.
- Depth comes from Depth Anything V2 Small, which runs on the device GPU with a CPU fallback.
  The model (about 90 MB) is downloaded once, on first use, and checked against a fixed
  SHA-256 before it is used. Videos never leave the device.
- Settings for depth strength and pop-out, and a page to manage the model.

### Meta Quest
- The immersive player now shows the video: before, it never created the scene objects and
  used a 100x100 video surface. Flat video plays on a large screen in front of you, 360 and
  180 video surrounds you, 3D files and live 2D to 3D are shown in native stereo, and a control
  bar you can grab and move has seek, 3D, projection, passthrough and recenter.
- The library runs as a regular Horizon OS panel, and leaving the player brings it back.
- The manifest follows Meta's hybrid app layout, and Quest 3S is listed as supported.

### Interface
- A navigation bar on phones and a rail on tablets, landscape phones and Quest, with a badge
  for active downloads and the New download action.
- Material 3 Expressive across the app: queue cards with wavy progress, speed and ETA and
  readable labels on any thumbnail; a library with a poster grid, 360/180/3D badges, multi
  selection and a new details sheet; grouped settings; a full-screen onboarding.
- Transitions follow Material motion and respond to predictive back.
- Text on Quest no longer overlaps: line height now scales with the larger type.
- The Italian translation is complete, and counts use real plural forms.

### Updates
- Every update is checked before it is installed: the SHA-256 published by GitHub, the package
  name, the version code and the signing certificate must all match. Installation uses a
  PackageInstaller session, which on Android 12 and newer can install without a confirmation
  prompt.
- A daily background check notifies you of new versions; the existing setting turns it off.
- If an update is signed with a different key, a dialog explains the one-time reinstall
  instead of showing a generic installer error.

### Fixes
- A link shared to VRClip while it was closed was ignored.
- The storage access button could do nothing on some devices.
- Removing a task left its action sheet open.
- After a crash report the app now closes instead of staying in an undefined state.
- Durations of exactly one hour are formatted correctly.

### Under the hood
- Android Gradle Plugin 9.4, Gradle 9.8, Kotlin 2.4, compileSdk 37, Compose BOM 2026.09 with
  Material 3 1.5 alpha, Media3 1.11, Room 2.8, Coil 3, Meta Spatial SDK 0.14 and LiteRT 2.2.
- Version numbers come from the commit history (Conventional Commits). A push to `main` that
  changes the app publishes a release only after lint, unit tests and the build pass and every
  APK and the App Bundle carry the release signature; the release text comes from this file.
- The website is new, in English and Italian, with screenshots of the app, the notes of the
  latest release and step by step install guides for Android and Meta Quest.

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
