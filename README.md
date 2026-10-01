# VRClip

[![Build](https://github.com/illuminazionetech/VRClip/actions/workflows/build.yml/badge.svg)](https://github.com/illuminazionetech/VRClip/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/illuminazionetech/VRClip)](https://github.com/illuminazionetech/VRClip/releases/latest)
[![License: GPL v3](https://img.shields.io/github/license/illuminazionetech/VRClip)](LICENSE)

Video and audio downloader with a 360, 180 and stereo 3D player, for Android and Meta Quest.

VRClip downloads from the sites yt-dlp supports and plays the result in its own player, which
recognises flat, 360, 180 and stereoscopic video from the file's metadata, its name or the shape
of the picture. It can also turn flat video into 3D on the device. On Meta Quest the library is a
regular panel and playback opens an immersive scene built with the Meta Spatial SDK.

Website and downloads: [illuminazionetech.github.io/VRClip](https://illuminazionetech.github.io/VRClip/)

## Features

- Downloads: paste or share a link; yt-dlp picks the formats, aria2c can speed up the transfer,
  and cover art, chapters and subtitles can be embedded in the file. The yt-dlp engine updates
  itself so extraction keeps up with site changes.
- Player on phones and tablets:
  - resumes where you stopped and remembers the playback speed;
  - audio and subtitle tracks, including subtitle files saved next to the video;
  - double tap to seek, long press for 2x, swipe to scrub with a preview of the frame,
    brightness and volume swipes, pinch to fill, controls lock, repeat, picture-in-picture;
  - Material 3 Expressive controls with motion and haptic feedback;
  - 360 and 180 video with touch and motion sensor panning, split screen for Cardboard-style
    viewers, and red and cyan anaglyph output for 3D video.
- 2D to 3D: Depth Anything V2 Small estimates depth on the device GPU (CPU fallback) and VRClip
  draws a view for each eye. It runs live in the player, or converts a file at full quality in
  the background and replaces the flat original once the 3D file is complete. The model (about
  90 MB) is downloaded on first use, in the background with a notification; the download
  resumes after interruptions, falls back to a copy in this repository's `depth-model` release
  and is verified against a fixed SHA-256.
- Meta Quest 2, 3, 3S and Pro: a large screen in front of you for flat video, a sphere or half
  dome for 360 and 180, native stereo for 3D files and live 2D to 3D, passthrough, and a control
  bar you can grab and move, with speed and track pages.
- High resolution preset for 360 and 3D: asks yt-dlp for the largest video the device's
  hardware decoders can play, with the codec that reaches that size.
- Interface: Material 3 Expressive with dynamic color, a bottom bar on phones and a navigation
  rail on tablets and Quest, larger type and touch targets on Quest. English and Italian are
  complete; other languages come from Seal's translations.
- Updates: VRClip installs its own updates after checking the SHA-256 published by GitHub, the
  package name, the version code and the signing certificate.
- Privacy: no accounts, analytics, ads or tracking. See the
  [privacy policy](https://illuminazionetech.github.io/VRClip/privacy.html).

## Installation

The [website](https://illuminazionetech.github.io/VRClip/) offers the right file for the device
it is opened on, and has step by step guides for both platforms.

### Android phone or tablet
1. Download the APK from the [latest release](https://github.com/illuminazionetech/VRClip/releases/latest)
   (see the table below).
2. Allow installs from the browser or file manager when Android asks, then install.
3. On first launch VRClip asks for storage access (to save to `Download/VRClip`) and for
   notifications. Each step can be skipped and granted later from Settings.

### Meta Quest
1. Turn on developer mode: create a developer organization at developers.meta.com, then enable
   Developer mode for the headset in the Meta Horizon app on your phone.
2. Install `app-generic-arm64-v8a-release.apk` with [SideQuest](https://sidequestvr.com/) or
   `adb install -r VRClip.apk`.
3. Open VRClip from the app library under Unknown sources.

### Updating from 1.1.0 or older
1.2.0 is signed with a new permanent key, so Android cannot update 1.1.0 in place. Uninstall
VRClip once and install the new version; downloaded files in `Download/VRClip` are kept. From
1.2.0 on, updates install over the existing app.

## Which file to download

| File | Platform | When to use it |
|---|---|---|
| `app-generic-arm64-v8a-release.apk` | Android and Meta Quest | Recommended. Every Meta Quest and nearly all recent Android phones. |
| `app-generic-armeabi-v7a-release.apk` | Android | Only for old 32-bit Android devices. |
| `app-generic-x86_64-release.apk` / `app-generic-x86-release.apk` | Android | Emulators and devices with Intel or AMD processors. |
| `app-generic-universal-release.apk` | Android and Meta Quest | Every architecture in one larger file, if you are not sure which to pick. |
| `app-githubPreview-*-release.apk` | Android and Meta Quest | Preview channel with a separate app ID; installs next to the stable app. |
| `app-generic-release.aab` | Stores | Android App Bundle for store uploads. |

## Store distribution

Submission notes for the Meta Horizon Store are in
[`docs/META_QUEST_STORE_SUBMISSION.md`](docs/META_QUEST_STORE_SUBMISSION.md). A universal
downloader is a policy risk on both the Meta and Google Play stores, and the All files access and
install-packages permissions need per-store declarations that may not be approved. Sideloading
works regardless.

## Building

```bash
git clone https://github.com/illuminazionetech/VRClip.git
cd VRClip
./gradlew assembleGenericDebug
```

Requires JDK 21 and the Android SDK with platform 37. Release builds are signed with the debug key
unless a `keystore.properties` file is present. See [`CONTRIBUTING.md`](CONTRIBUTING.md) for
tests, formatting, commit messages and how releases are published.

## Security

See [`SECURITY.md`](SECURITY.md) to report a vulnerability.

## License and credits

VRClip is licensed under the GPLv3 (see [`LICENSE`](LICENSE)). It is a fork of
[Seal](https://github.com/JunkFood02/Seal) by JunkFood02 and uses
[yt-dlp](https://github.com/yt-dlp/yt-dlp), [FFmpeg](https://ffmpeg.org/) and
[aria2](https://aria2.github.io/) through
[youtubedl-android](https://github.com/JunkFood02/youtubedl-android), the
[Meta Spatial SDK](https://developers.meta.com/horizon/develop/spatial-sdk), and
[Depth Anything V2](https://github.com/DepthAnything/Depth-Anything-V2) through
[Qualcomm AI Hub](https://aihub.qualcomm.com/). Full attribution is in [`NOTICE`](NOTICE).
