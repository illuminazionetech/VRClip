package com.illuminazionetech.vrclip.ui.page

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the update dialog turns a GitHub release body into plain styled text. */
class ReleaseNotesTest {

    @Test
    fun wrappedLinesJoinTheItemAbove() {
        val notes =
            """
            ### New
            - The player resumes where you stopped and
              remembers the `speed`.
            - Second item
              - nested item
            """
                .trimIndent()

        assertEquals(
            "New\n• The player resumes where you stopped and remembers the speed.\n• Second item\n• nested item",
            releaseNotes(notes).text,
        )
    }

    @Test
    fun generatedPullRequestListIsShortened() {
        val notes =
            """
            ## What's Changed
            * feat: new player by @someone in https://github.com/illuminazionetech/VRClip/pull/31

            **Full Changelog**: https://github.com/illuminazionetech/VRClip/compare/v1.1.0...v1.2.0
            """
                .trimIndent()

        val text = releaseNotes(notes)
        assertEquals("What's Changed\n• feat: new player", text.text)
        assertTrue(
            text.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold && it.start == 0 }
        )
    }

    @Test
    fun boldMarkersBecomeStyles() {
        val text = releaseNotes("- **Quest**: immersive player")
        assertEquals("• Quest: immersive player", text.text)
        val bold = text.spanStyles.single()
        assertEquals("Quest", text.text.substring(bold.start, bold.end))
    }
}
