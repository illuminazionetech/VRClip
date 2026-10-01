package com.illuminazionetech.vrclip.player.stereo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** How a finished 3D conversion replaces the flat file. */
class MoveIntoPlaceTest {

    @get:Rule val folder = TemporaryFolder()

    private fun file(name: String, text: String) = folder.newFile(name).apply { writeText(text) }

    @Test
    fun mp4IsReplacedInPlace() {
        val original = file("clip.mp4", "flat")
        val temp = file(".clip.vrclip-3d.part.mp4", "3d")

        assertTrue(StereoConversionWorker.moveIntoPlace(temp, original, original))
        assertEquals("3d", original.readText())
        assertFalse(temp.exists())
        assertEquals(listOf("clip.mp4"), folder.root.list()!!.toList())
    }

    @Test
    fun otherContainersBecomeMp4AndTheOriginalIsRemoved() {
        val original = file("clip.webm", "flat")
        val temp = file(".clip.vrclip-3d.part.mp4", "3d")
        val target = File(folder.root, "clip.mp4")

        assertTrue(StereoConversionWorker.moveIntoPlace(temp, target, original))
        assertEquals("3d", target.readText())
        assertFalse(original.exists())
        assertEquals(listOf("clip.mp4"), folder.root.list()!!.toList())
    }

    @Test
    fun originalIsRestoredWhenTheNewFileCannotBeMoved() {
        val original = file("clip.mp4", "flat")
        val missing = File(folder.root, ".clip.vrclip-3d.part.mp4")

        assertFalse(StereoConversionWorker.moveIntoPlace(missing, original, original))
        assertEquals("flat", original.readText())
        assertEquals(listOf("clip.mp4"), folder.root.list()!!.toList())
    }
}
