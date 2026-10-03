package com.illuminazionetech.vrclip.player.stereo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** The models the app downloads must be the ones CI builds and publishes. */
class DepthModelPinsTest {

    private val manifest: String by lazy {
        // Unit tests run from the app module.
        listOf(File("../tools/depth-model/models.json"), File("tools/depth-model/models.json"))
            .first { it.isFile }
            .readText()
    }

    private fun field(model: DepthModel, key: String): String {
        val block =
            manifest.substringAfter("\"name\": \"${model.name.lowercase()}\"").substringBefore("}")
        return Regex("\"$key\": \"?([^\",\\n]+)\"?").find(block)!!.groupValues[1].trim()
    }

    @Test
    fun pinsMatchTheBuildManifest() {
        for (model in DepthModel.entries) {
            assertEquals(model.fileName, field(model, "file"))
            assertEquals(model.inputSize.toString(), field(model, "input_size"))
            assertEquals(model.bytes.toString(), field(model, "bytes"))
            assertEquals(model.sha256, field(model, "sha256"))
        }
    }

    @Test
    fun modelsDownloadFromTheDepthModelRelease() {
        val release = Regex("\"release\": \"([^\"]+)\"").find(manifest)!!.groupValues[1]
        for (model in DepthModel.entries) {
            assertEquals(
                "https://github.com/illuminazionetech/VRClip/releases/download/$release/" +
                    model.fileName,
                model.url,
            )
        }
    }
}
