## 1.0.1 — Release pipeline fix
- 🛠️ Fixed a CI packaging failure that prevented 1.0.0's release build from completing, and made
  release publishing fully automatic: pushing a version bump to `main` now tags, builds, and
  publishes the GitHub Release (with the in-app auto-updater picking it up) with no manual steps.

## 1.0.0 — Material 3 Expressive rewrite
- 🎨 Full UI rewrite on Material Design 3 Expressive: the "Liquid Glass" blur/translucent-border
  system is gone, replaced with real M3 tonal surfaces, an expressive shape scale, spring-based
  motion, and dynamic color (Android 12+ wallpaper-based, with an HCT/Monet-derived VR Blue scheme
  as fallback). Meta Quest's larger touch-target/type-scale spatial density carries over unchanged.
- 🐋 New app icon and brand mark: a VR-Blue seal wearing a headset — a nod to Seal, the project
  this app is built on — replacing the previous abstract glyph everywhere (launcher, adaptive
  monochrome icon, notification icon, About screen).
- 🧭 Renamed the Android package from `com.xrclip` to `com.illuminazionetech.vrclip` and finished
  the internal `XRClip` → `VRClip` rebrand (classes, resources, Gradle project name, CI artifacts)
  to match the app's public name.
- ⚙️ Settings cleanup: removed dead sponsor/donation code paths and strings that were never reachable
  from any screen, deduplicated repeated entries between Troubleshooting and General/Directory,
  moved the debug-only "print details" toggle out of the main settings flow, and added a real
  "Follow system" dark theme option alongside On/Off.
- 🔄 Fixed the in-app updater and all About/Troubleshooting links to point at this repository
  (`illuminazionetech/VRClip`) instead of a stale upstream reference.
- 🎬 New in-app 3D/360°/180°/stereoscopic video player for Android (ExoPlayer + custom OpenGL
  equirectangular/stereo renderer), replacing "open in an external app" as the default action.
- 🥽 Fully immersive Meta Spatial SDK player on Meta Quest, with an equirectangular/half-dome
  panel sized and stereo-configured from the detected projection.
- 🧹 Removed the dead legacy (V1) download UI/orchestration code path; consolidated the live
  custom-command-task tracker into `CommandTaskManager`.
- 📄 Added `NOTICE`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, and a Meta Quest Store
  submission readiness doc (`docs/META_QUEST_STORE_SUBMISSION.md`).
- ✅ CI now runs a dedicated lint/format-check job on every pull request.

## Release 0.0.4
- 🚀 Updated version to 0.0.4.
- 📱 Improved compatibility: Lowered Min SDK to Android 7.0 (API 24).
- 🛠️ Fixed build issues and optimized performance.
- 🎨 UI refinements for the new XRClip brand.

## Overview
Initial beta release of XRClip featuring core VR video clipping functionality.

## What's Included
- ✨ Core VR video recording and clipping features
- 🎨 Modern Material Design 3 UI with Jetpack Compose
- 🏗️ Built with Kotlin Coroutines and Flow for efficient async operations
- 📱 Support for multiple Android architectures (arm64-v8a, armeabi-v7a, x86, x86_64)
- 🔒 Release builds with code optimization (ProGuard enabled)

## Build Details
- **Target SDK:** Android 15 (API 35)
- **Min SDK:** Android 7.0 (API 24)
- **Java Version:** JDK 21
- **Kotlin:** Latest stable version
- **Build System:** Gradle with Kotlin DSL

## Available Downloads
- **Generic Release APK** - Full-featured release build
- **GitHub Preview Release APK** - Preview version for GitHub
- **Debug APK** - Development build for testing

## Technical Stack
- Jetpack Compose for declarative UI
- Room Database for local persistence
- Kotlin Serialization for data handling
- AndroidX libraries for compatibility

## Notes
- This is an early beta release - expect continued improvements
- Memory optimizations applied (4GB JVM heap for compilation)
- All APK variants included for different use cases

## System Requirements
- Android 7.0 or higher
- Minimum 2GB RAM recommended

Enjoy the Bunny release! 🐰🎬