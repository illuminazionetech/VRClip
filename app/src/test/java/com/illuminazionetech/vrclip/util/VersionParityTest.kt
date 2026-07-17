package com.illuminazionetech.vrclip.util

import com.illuminazionetech.vrclip.util.UpdateUtil.toVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the versioning contract between [UpdateUtil]'s runtime tag parser (used to compare the
 * installed app against GitHub release tags) and `buildSrc/src/main/kotlin/Version.kt` (used to
 * generate `versionCode`/`versionName` at build time). buildSrc isn't on this module's classpath,
 * so the two can't share one implementation — this test instead pins the expected versionCode for
 * a set of sample tags using the same formula buildSrc uses
 * (`major*1e8 + minor*1e6 + patch*1e4 + build*10 + variant`, variant Alpha=100/Beta=200/RC=300/
 * Stable=400). If this formula changes in one file, change it in the other and update this test.
 */
class VersionParityTest {
    @Test
    fun `stable tag parses to the expected versionCode`() {
        val version = "v1.0.0".toVersion()
        assertEquals(100_000_400L, version.toNumber())
    }

    @Test
    fun `prerelease variants order below stable`() {
        val alpha = "v1.0.0-alpha.1".toVersion()
        val beta = "v1.0.0-beta.1".toVersion()
        val rc = "v1.0.0-rc.1".toVersion()
        val stable = "v1.0.0".toVersion()

        assertTrue(alpha < beta)
        assertTrue(beta < rc)
        assertTrue(rc < stable)
    }

    @Test
    fun `newer semantic version always outranks an older one regardless of variant`() {
        val older = "v1.0.0".toVersion()
        val newer = "v1.0.1-alpha.1".toVersion()
        assertTrue(older < newer)
    }

    @Test
    fun `malformed tag falls back to the empty stable version`() {
        val version = "not-a-version".toVersion()
        assertEquals(0L, version.major.toLong())
        assertEquals(0L, version.minor.toLong())
        assertEquals(0L, version.patch.toLong())
    }
}
