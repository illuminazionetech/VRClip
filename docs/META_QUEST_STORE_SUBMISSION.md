# Meta Quest Store submission readiness

This document tracks what VRClip needs for a Meta Horizon Store submission, and is
honest about a real policy risk before anything else.

## ⚠️ Policy risk — read this first

Meta's Horizon Store content policy restricts apps whose primary function is to
download content from third-party services without the rights-holder's
authorization. **VRClip is a yt-dlp-based universal downloader** — that is its
core, advertised function. This means a submission carries real, material risk of
rejection or later removal, regardless of how polished the app is technically.

This document prepares the submission package anyway, per the project's decision
to proceed with eyes open. It does **not** guarantee store acceptance. VRClip
remains fully installable and usable via sideloading (SideQuest, `adb install`)
on Quest 2/3/Pro independent of any store outcome — that path has no such policy
gate.

If you want to reduce (not eliminate) this risk before submitting, consider
de-emphasizing the URL-download feature in the store listing copy and leading
with the 3D/360°/immersive player instead, since the player itself has no such
policy problem. That's a listing/marketing decision, not a code change, and is
left to the repo owner.

## Manifest checklist

Already present in `app/src/main/AndroidManifest.xml`:

- [x] `horizonos:uses-horizonos-sdk` (`minSdkVersion=28`, `targetSdkVersion=35`)
- [x] `com.oculus.intent.category.VR` launcher category on `MainActivity`
- [x] `com.oculus.supported_devices` = `quest|quest2|questpro|quest3`
- [x] Optional (`required="false"`) hand-tracking / passthrough / render-model
      `uses-feature` entries
- [x] `com.oculus.permission.HAND_TRACKING`, `com.oculus.permission.RENDER_MODEL`
- [x] `com.oculus.permission.USE_SCENE`, `android.permission.MODIFY_AUDIO_SETTINGS`
      (added for the immersive Spatial SDK player)
- [x] `ImmersivePlayerActivity` registered with a fullscreen theme

Still to confirm before submission (needs real hardware / Meta developer
dashboard, can't be verified from source alone):

- [ ] The immersive scene launches and renders correctly on real Quest 2/3/Pro
      hardware (this was built and reasoned about carefully, but has not been
      runtime-verified on a physical headset in this development environment)
- [ ] App doesn't regress the panel-app (2D) experience on Quest when the
      immersive player isn't in use

## Icon / screenshot assets required by the Horizon Store

Not yet produced — placeholders to fill before submission:

- App icon: 512×512 PNG
- Store hero/banner image: 1920×1080 (16:9)
- At least 3 in-headset screenshots, 1280×720 or larger, showing real UI (not
  mockups) — should include the immersive player in use
- Optional: a short (15–30s) capture/trailer video

## Privacy policy (draft)

> VRClip does not collect, store, or transmit any personal data to VRClip's own
> servers — VRClip has no backend servers. Network access is limited to: (1)
> fetching media the user explicitly requests from a URL they provide, sent
> directly to the source site; (2) an optional check for `yt-dlp` engine updates.
> No analytics, advertising, or tracking SDKs are included. No account or sign-in
> exists. All downloaded files and app preferences are stored locally on-device
> and can be deleted by uninstalling the app or clearing its data.

This needs to be hosted at a stable URL (a GitHub Pages page or even a raw
GitHub-rendered Markdown link is acceptable to Meta) before submission, since the
developer dashboard requires a privacy policy URL.

## Data safety declaration (draft)

- Data collected: **None**.
- Data shared with third parties: **None**, other than the destination site the
  user's browser/app traffic goes to directly when downloading a user-provided
  URL — VRClip's own servers never see this traffic, because VRClip has none.
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
| User-generated content shared with others | None — downloads are local-only, not shared through VRClip |
| Location sharing | None — VRClip does not access or share location |
| Personal information sharing | None |
| Digital purchases | None — VRClip has no in-app purchases |

Misrepresenting this (e.g. claiming a low rating despite the unrestricted
download capability) is itself a policy risk — answer honestly.

## Legal / support URLs required by the dashboard

- Privacy policy URL: see above, needs hosting.
- Support URL: the GitHub Issues page (`https://github.com/illuminazionetech/VRClip/issues`)
  is acceptable for indie/open-source developers.
- Support email: needs to be provided by the developer account owner.

## Signing key

`app/build.gradle.kts` already supports a real release signing config, gated on a
local (gitignored) `keystore.properties` file:

```properties
storeFile=/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

Without it, release builds fall back to the debug signing key, which **cannot**
be used for a store submission or to update an existing installed release build.
Generate and securely back up a real keystore before the first submission — Meta,
like Google Play, requires signing-key consistency across app updates, and losing
the key means you can never update the app under the same listing again.

## Summary

Code/manifest/CI readiness: mostly done. Store submission itself (developer
account, real assets, hosted privacy policy, a real signing key, and the actual
review) is outside what this repository can do — it requires the account owner's
action, and carries the policy risk flagged at the top of this document.
