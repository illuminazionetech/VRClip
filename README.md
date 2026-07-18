# VRClip

[![Build](https://github.com/illuminazionetech/VRClip/actions/workflows/build.yml/badge.svg)](https://github.com/illuminazionetech/VRClip/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/illuminazionetech/VRClip)](https://github.com/illuminazionetech/VRClip/releases/latest)
[![License: GPL v3](https://img.shields.io/github/license/illuminazionetech/VRClip)](LICENSE)

**Universal video and audio downloader with an immersive 3D/360/XR player, for Android and Meta Quest.**

VRClip downloads video and audio from hundreds of platforms (powered by yt-dlp) and plays them in-app with a player that automatically recognizes flat, 360, 180, and stereoscopic 3D content. On Meta Quest it delivers a fully immersive experience through the Meta Spatial SDK; on Android phones and tablets it uses a dedicated OpenGL renderer with touch and gyroscope panning, plus a Cardboard-style split-screen mode. The interface follows Google's Material 3 Expressive guidelines, with dynamic color, expressive shapes and typography, and spring-based motion, and adapts automatically to controller-driven interaction on Quest.

## Features

- Universal downloads: every site supported by yt-dlp, with multi-threading and bundled aria2c for maximum speed.
- Built-in immersive player: automatic detection of flat, 360 mono, 360 3D (top-bottom or side-by-side), 180, and 3D content, with a manual projection override per video.
- Platform-tuned rendering:
  - Android phones and tablets: OpenGL equirectangular sphere rendering (360/180) with touch and gyroscope panning, and a Cardboard-style split-screen mode for 3D video.
  - Meta Quest (2, 3, Pro): a real immersive scene through the Meta Spatial SDK, with a spatially placed video panel and correct per-eye stereo.
- Guided first run: the app walks you through storage access, notifications, and the download engine setup, so downloads work out of the box.
- Self-updating engine: yt-dlp updates itself automatically so extraction keeps working as sites change.
- Metadata handling: embeds metadata, subtitles, and thumbnails into downloaded files.
- Material 3 Expressive UI: dynamic color (Material You), expressive shapes and typography, spring motion, automatically scaled for 10-foot controller use on Quest.
- Privacy first: no tracking, no ads, no accounts. Every download happens locally.

## Installation

### Android (phone or tablet)
1. Download the APK from the [latest release](https://github.com/illuminazionetech/VRClip/releases/latest). See the table below for which file to pick.
2. Allow installs from unknown sources in your device settings, then install the APK.

### Meta Quest (2 / 3 / Pro)
1. Download the same APK (`generic` recommended). It is a single universal app; there is no separate Quest file.
2. Install it on the headset with [SideQuest](https://sidequestvr.com/) or `adb install`.
3. The app detects that it is running on Quest and enables the dedicated interface and player.

## Which file to download

Every CI build produces one APK per architecture, much lighter than a universal package, or you can let the [download site](https://illuminazionetech.github.io/VRClip/) pick the right file for you:

| File | Platform | When to use it |
|---|---|---|
| `app-generic-arm64-v8a-release.apk` | Android and Meta Quest | Recommended. Covers all Meta Quest headsets and nearly all recent Android phones. |
| `app-generic-armeabi-v7a-release.apk` | Android | Only for very old 32-bit Android devices. |
| `app-generic-x86_64-release.apk` / `app-generic-x86-release.apk` | Android | Only for emulators or devices with Intel/AMD CPUs. |
| `app-generic-universal-release.apk` | Android and Meta Quest | Contains every architecture. Use it only if you are unsure which one to pick (larger file). |
| `app-githubPreview-*-release.apk` | Android and Meta Quest | Beta/preview channel (separate app ID, installs alongside the stable build). |

## Usage

1. Copy the link of the video or audio you want to download.
2. Open VRClip and paste the link, or share it directly from the source app.
3. Pick the format you want.
4. When the download finishes, tap the video in the list to open it in the built-in player. The projection (flat/360/3D) is detected automatically and can be overridden from the menu if needed.

## First-run permissions

VRClip saves downloads to the public `Download/VRClip` folder, which on Android 11 and newer requires the All files access permission. The first-run setup requests it (and the notification permission on Android 13+) with clear explanations; every step can be skipped and granted later from Settings. Without storage access, downloads cannot be written and the home screen shows a shortcut to the grant screen.

## Store distribution notes

VRClip is technically ready for Quest 2/3/Pro; submission material (manifest checklist, privacy policy, data safety text) is documented in [`docs/META_QUEST_STORE_SUBMISSION.md`](docs/META_QUEST_STORE_SUBMISSION.md). Note that as a universal downloader the app sits in a gray area of both the Meta and Google Play content policies, and its use of the All files access and install-packages permissions requires per-store declarations that may not be approved for this app category. Sideloading is fully supported regardless of any store outcome. A Play-compatible build would additionally need a scoped-storage download mode and a flavor without the in-app self-updater.

## Security and privacy

All downloads happen locally on the device. No personal data is collected, there are no tracking or advertising SDKs, and there are no accounts. The only network traffic goes to the source sites you explicitly choose, plus optional update checks for yt-dlp and the app itself. See [`SECURITY.md`](SECURITY.md) for how to report security issues. The privacy policy is published at [illuminazionetech.github.io/VRClip/privacy](https://illuminazionetech.github.io/VRClip/privacy.html).

## Building

```
git clone https://github.com/illuminazionetech/VRClip.git
cd VRClip
./gradlew assembleGenericRelease
```

Requires JDK 21 and the Android SDK (compileSdk 36). See [`CONTRIBUTING.md`](CONTRIBUTING.md) for code style and pull request guidelines.

## License and credits

VRClip is licensed under the **GPLv3** (see [`LICENSE`](LICENSE)) and is a fork of [Seal](https://github.com/JunkFood02/Seal) by JunkFood02, extended with Meta Quest support, the immersive 3D/360/XR player, and a full Material 3 Expressive redesign. It uses [yt-dlp](https://github.com/yt-dlp/yt-dlp), [ffmpeg](https://ffmpeg.org/), and [aria2c](https://aria2.github.io/) through [youtubedl-android](https://github.com/JunkFood02/youtubedl-android). Full attribution is in [`NOTICE`](NOTICE).
