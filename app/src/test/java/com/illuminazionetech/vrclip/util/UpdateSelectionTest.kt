package com.illuminazionetech.vrclip.util

import com.illuminazionetech.vrclip.util.UpdateUtil.AssetsItem
import com.illuminazionetech.vrclip.util.UpdateUtil.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which release and which APK the in-app updater picks, against realistic release data. */
class UpdateSelectionTest {

    private fun apk(name: String) =
        AssetsItem(name = name, browserDownloadUrl = "https://example.com/$name", size = 1L)

    private val assets =
        listOf(
            apk("app-generic-arm64-v8a-release.apk"),
            apk("app-generic-armeabi-v7a-release.apk"),
            apk("app-generic-x86-release.apk"),
            apk("app-generic-x86_64-release.apk"),
            apk("app-generic-universal-release.apk"),
            apk("app-githubPreview-arm64-v8a-release.apk"),
            apk("app-githubPreview-universal-release.apk"),
            AssetsItem(name = "app-generic-release.aab"),
        )

    private val release = Release(tagName = "v1.2.0", name = "VRClip v1.2.0", assets = assets)

    @Test
    fun `picks the preferred ABI of the build flavor`() {
        val asset = UpdateUtil.selectAsset(release, "generic", listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("app-generic-arm64-v8a-release.apk", asset?.name)
    }

    @Test
    fun `32-bit x86 does not get the x86_64 build`() {
        val asset = UpdateUtil.selectAsset(release, "generic", listOf("x86"))
        assertEquals("app-generic-x86-release.apk", asset?.name)
    }

    @Test
    fun `preview flavor falls back to its own universal apk`() {
        val asset = UpdateUtil.selectAsset(release, "githubPreview", listOf("x86_64"))
        assertEquals("app-githubPreview-universal-release.apk", asset?.name)
    }

    @Test
    fun `never offers the app bundle`() {
        val bundleOnly = release.copy(assets = listOf(AssetsItem(name = "app-generic-release.aab")))
        assertNull(UpdateUtil.selectAsset(bundleOnly, "generic", listOf("arm64-v8a")))
    }

    @Test
    fun `stable channel ignores drafts and pre-releases`() {
        val releases =
            listOf(
                Release(tagName = "v1.3.0", draft = true),
                Release(tagName = "v1.2.1-beta.1", preRelease = true),
                Release(tagName = "v1.2.0"),
                Release(tagName = "v1.1.0"),
            )
        assertEquals("v1.2.0", UpdateUtil.selectRelease(releases, stableOnly = true)?.tagName)
        assertEquals(
            "v1.2.1-beta.1",
            UpdateUtil.selectRelease(releases, stableOnly = false)?.tagName,
        )
    }

    @Test
    fun `version comes from the tag even when the name is decorative`() {
        val releases =
            listOf(Release(tagName = "v1.10.0", name = "Big update"), Release(tagName = "v1.9.3"))
        assertEquals("v1.10.0", UpdateUtil.selectRelease(releases, stableOnly = true)?.tagName)
    }

    @Test
    fun `digest parsing accepts only well-formed sha256 values`() {
        val hex = "6fdf3c7883acec3c67c92f3b5e1ead70c4c705cde024ae3c00efb5088b02777a"
        assertEquals(hex, UpdateUtil.sha256FromDigest("sha256:$hex"))
        assertEquals(hex, UpdateUtil.sha256FromDigest("SHA256:${hex.uppercase()}"))
        assertNull(UpdateUtil.sha256FromDigest("sha1:$hex"))
        assertNull(UpdateUtil.sha256FromDigest("sha256:1234"))
        assertNull(UpdateUtil.sha256FromDigest(null))
    }
}
