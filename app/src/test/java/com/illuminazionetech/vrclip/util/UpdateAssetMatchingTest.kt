package com.illuminazionetech.vrclip.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The in-app updater picks a release asset by matching the device ABI against the asset file
 * name. These are the exact names the CI release pipeline produces, so this test pins the
 * matcher against them, in particular the "x86" vs "x86_64" substring trap.
 */
class UpdateAssetMatchingTest {

    private val assets =
        listOf(
            "app-generic-arm64-v8a-release.apk",
            "app-generic-armeabi-v7a-release.apk",
            "app-generic-x86-release.apk",
            "app-generic-x86_64-release.apk",
            "app-generic-universal-release.apk",
        )

    private fun matchesOf(abi: String): List<String> =
        assets.filter { UpdateUtil.assetMatchesAbi(it, abi) }

    @Test
    fun `arm64 only matches the arm64 asset`() {
        assertTrue(matchesOf("arm64-v8a") == listOf("app-generic-arm64-v8a-release.apk"))
    }

    @Test
    fun `arm32 only matches the arm32 asset`() {
        assertTrue(matchesOf("armeabi-v7a") == listOf("app-generic-armeabi-v7a-release.apk"))
    }

    @Test
    fun `x86 does not match the x86_64 asset`() {
        assertTrue(matchesOf("x86") == listOf("app-generic-x86-release.apk"))
        assertFalse(UpdateUtil.assetMatchesAbi("app-generic-x86_64-release.apk", "x86"))
    }

    @Test
    fun `x86_64 only matches the x86_64 asset`() {
        assertTrue(matchesOf("x86_64") == listOf("app-generic-x86_64-release.apk"))
    }

    @Test
    fun `universal fallback matches the universal asset`() {
        assertTrue(matchesOf("universal") == listOf("app-generic-universal-release.apk"))
    }
}
