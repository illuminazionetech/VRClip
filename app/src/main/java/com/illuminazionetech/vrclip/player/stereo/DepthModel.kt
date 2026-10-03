package com.illuminazionetech.vrclip.player.stereo

/**
 * The two builds of Depth Anything V2 Small (Apache-2.0) that VRClip uses for 2D to 3D. Both are
 * exported from the official weights by `tools/depth-model/export.py` as LiteRT graphs that run
 * entirely on mobile GPUs, with float16 weights; the size and SHA-256 pinned here must match
 * `tools/depth-model/models.json`, which CI checks the published files against.
 *
 * Input: [inputSize] x [inputSize] RGB in 0..1 (any frame is stretched to the square). Output:
 * relative inverse depth for every input pixel, larger meaning nearer.
 */
enum class DepthModel(
    val fileName: String,
    val inputSize: Int,
    val bytes: Long,
    val sha256: String,
) {
    /** Fewer patches, for live conversion while a video plays: about twice as fast. */
    Live(
        fileName = "depth_anything_v2_small_live_364.tflite",
        inputSize = 364,
        bytes = 49_659_680L,
        sha256 = "875590dad368b1f3c97d1051ca4f3c48d75f51dc48c0c95f9224500028b6e273",
    ),

    /** The resolution the model was trained at, for permanent conversions and fast devices. */
    Full(
        fileName = "depth_anything_v2_small_full_518.tflite",
        inputSize = 518,
        bytes = 50_723_824L,
        sha256 = "ba32f1012b4e80d6ac79c91dd56dc90b029c404cf8680b6b29d2ffa1b692d23a",
    );

    val url: String
        get() = RELEASE_URL + fileName

    /** The self-test image, stored at [inputSize] so it is decoded without any scaling. */
    val selfTestImage: String
        get() = "depth/selftest_$inputSize.webp"

    /** Expected output for [selfTestImage], averaged over the 14 px patch grid. */
    val selfTestExpected: String
        get() = "depth/selftest_${name.lowercase()}.bin"

    companion object {
        const val RELEASE_URL =
            "https://github.com/illuminazionetech/VRClip/releases/download/depth-model/"

        val totalBytes: Long
            get() = entries.sumOf { it.bytes }
    }
}
