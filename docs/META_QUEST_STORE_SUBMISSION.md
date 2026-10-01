# Meta Quest Store submission readiness

This document tracks what VRClip needs for a Meta Horizon Store submission, and is
honest about a real policy risk before anything else.

## ⚠️ Policy risk, read this first

Meta's Horizon Store content policy restricts apps whose primary function is to
download content from third-party services without the rights-holder's
authorization. **VRClip is a yt-dlp-based universal downloader**, that is its
core, advertised function. This means a submission carries real, material risk of
rejection or later removal, regardless of how polished the app is technically.

This document prepares the submission package anyway, per the project's decision
to proceed with eyes open. It does **not** guarantee store acceptance. VRClip
remains fully installable and usable via sideloading (SideQuest, `adb install`)
on Quest 2, 3, 3S and Pro independent of any store outcome, that path has no such policy
gate.

If you want to reduce (not eliminate) this risk before submitting, consider
de-emphasizing the URL-download feature in the store listing copy and leading
with the 3D/360°/immersive player instead, since the player itself has no such
policy problem. That's a listing/marketing decision, not a code change, and is
left to the repo owner.

## Manifest checklist

VRClip is a hybrid app, laid out like Meta's HybridSample: the library is a 2D panel and the
player is an immersive activity. Present in `app/src/main/AndroidManifest.xml`:

- [x] `horizonos:uses-horizonos-sdk` (`minSdkVersion=28`, `targetSdkVersion=35`)
- [x] `MainActivity` (the library) has `LAUNCHER` plus `com.oculus.intent.category.2D`, so it
      opens as a panel in the home environment
- [x] `ImmersivePlayerActivity` has `com.oculus.intent.category.VR` and is the only immersive
      entry point; leaving it returns to the library panel
- [x] `uses-native-library libossdk.oculus.so` with `required="false"`
- [x] `com.oculus.supportedDevices` = `quest2|questpro|quest3|quest3s` (the Spatial SDK does not
      support the original Quest)
- [x] Optional (`required="false"`) hand tracking, passthrough, render model and virtual keyboard
      `uses-feature` entries
- [x] `com.oculus.permission.HAND_TRACKING`, `com.oculus.permission.RENDER_MODEL`,
      `com.oculus.permission.USE_SCENE`, `android.permission.MODIFY_AUDIO_SETTINGS`

Still to confirm before submission (needs a headset and the Meta developer dashboard, it cannot
be verified from source):

- [ ] The immersive scene launches and renders correctly on Quest 2, 3, 3S and Pro. The scene
      and its stereo layers follow the Spatial SDK 0.14 samples; only the control bar layout
      has been checked, in rendered screenshots. Nothing has run on a headset yet.
- [ ] The library panel and the return from the immersive player behave as expected in the home
      environment.
- [ ] Live 2D to 3D keeps a steady frame rate on Quest 2 (the slowest supported GPU).

## Icon / screenshot assets required by the Horizon Store

Not yet produced, placeholders to fill before submission:

- App icon: 512×512 PNG
- Store hero/banner image: 1920×1080 (16:9)
- At least 3 in-headset screenshots, 1280×720 or larger, showing real UI (not
  mockups), should include the immersive player in use
- Optional: a short (15–30s) capture/trailer video

## Privacy policy

Published at [illuminazionetech.github.io/VRClip/privacy.html](https://illuminazionetech.github.io/VRClip/privacy.html)
in English and Italian (source: `docs/privacy.html`). It lists everything the app connects to:
the sites of the links the user provides, GitHub for app and yt-dlp updates, and Qualcomm AI
Hub's public storage once, to download the depth model for 2D to 3D. No analytics, advertising
or tracking libraries are included, and there are no accounts.

## Data safety declaration (draft)

- Data collected: **None**.
- Data shared with third parties: **None**. The app talks directly to the site of a
  user-provided URL, to GitHub for updates and to Qualcomm AI Hub's storage for the depth
  model; VRClip has no servers of its own, so none of this traffic passes through one.
- Data deletion: uninstalling the app removes all local data; there is no
  server-side account to delete.

## Age rating / IARC questionnaire (draft guidance)

Because VRClip can download **any** content from **any** URL the user provides,
including content the app has no way to classify or control, answer the IARC
questionnaire conservatively rather than claiming an "Everyone" rating:

| Category | Draft answer |
|---|---|
| Violence | Possible via user-downloaded content, outside the app's control |
| Sexual content | Possible via user-downloaded content, outside the app's control |
| Profanity | Possible via user-downloaded content, outside the app's control |
| Controlled substances | Not depicted by the app itself; possible in downloaded content |
| Gambling | None |
| User-generated content shared with others | None, downloads are local-only, not shared through VRClip |
| Location sharing | None, VRClip does not access or share location |
| Personal information sharing | None |
| Digital purchases | None, VRClip has no in-app purchases |

Misrepresenting this (e.g. claiming a low rating despite the unrestricted
download capability) is itself a policy risk, answer honestly.

## Legal / support URLs required by the dashboard

- Privacy policy URL: https://illuminazionetech.github.io/VRClip/privacy.html
- Support URL: the GitHub Issues page (`https://github.com/illuminazionetech/VRClip/issues`)
  is acceptable for indie/open-source developers.
- Support email: needs to be provided by the developer account owner.

## Signing key

Since 1.2.0 every release is signed with a permanent release key. CI reads it from the
`KEYSTORE_B64` and `KEYSTORE_PASSWORD` repository secrets and refuses to publish any APK or
App Bundle whose certificate differs from the one pinned in `.github/release-signing-cert.sha256`.
Keep an offline backup of the keystore and its password: a store listing, like installed copies,
can only be updated by builds signed with the same key.

For local release builds, `app/build.gradle.kts` reads a gitignored `keystore.properties`:

```properties
storeFile=/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

Without it, release builds use the debug key, which cannot be submitted or update an installed
release.

## Summary

Code, manifest, signing and CI are in place, and the privacy policy is hosted. What remains
needs the account owner: a developer account, store assets, a test on real headsets, and the
review itself, which carries the policy risk described at the top of this document.
