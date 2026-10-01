package com.illuminazionetech.vrclip.util

import com.illuminazionetech.vrclip.App.Companion.context
import com.illuminazionetech.vrclip.util.PreferenceUtil.getInt
import com.illuminazionetech.vrclip.util.PreferenceUtil.updateLong
import com.yausername.youtubedl_android.YoutubeDL
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Release/version helpers shared by the in-app updater ([AppUpdateManager]) and the yt-dlp engine
 * updater. The pure selection functions here are unit tested.
 */
object UpdateUtil {

    suspend fun updateYtDlp(): YoutubeDL.UpdateStatus? =
        withContext(Dispatchers.IO) {
            val channel =
                when (YT_DLP_UPDATE_CHANNEL.getInt()) {
                    YT_DLP_NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                    else -> YoutubeDL.UpdateChannel.STABLE
                }

            YoutubeDL.getInstance()
                .updateYoutubeDL(appContext = context, updateChannel = channel)
                .also {
                    if (it == YoutubeDL.UpdateStatus.DONE) {
                        YoutubeDL.getInstance().version(context)?.let {
                            PreferenceUtil.encodeString(YT_DLP_VERSION, it)
                        }
                    }
                    val now = System.currentTimeMillis()
                    YT_DLP_UPDATE_TIME.updateLong(now)
                }
        }

    /**
     * Picks the newest release a user on [stableOnly] should be offered: drafts are never offered,
     * and the stable channel only sees stable versions that are not marked as pre-releases. The
     * version comes from the tag (falling back to the release name).
     */
    fun selectRelease(releases: List<Release>, stableOnly: Boolean): Release? =
        releases
            .filter { it.draft != true }
            .filter { !stableOnly || (it.preRelease != true && it.version() is Version.Stable) }
            .filter { it.version().toNumber() > 0 }
            .maxByOrNull { it.version() }

    fun Release.version(): Version = (tagName ?: name).toVersion()

    /**
     * The APK asset for this build: same publish flavor (a `-generic-` or `-githubPreview-` token),
     * trying the device ABIs in preference order and falling back to the universal APK.
     */
    fun selectAsset(release: Release, flavor: String, supportedAbis: List<String>): AssetsItem? {
        val candidates =
            release.assets.orEmpty().filter {
                val name = it.name ?: return@filter false
                name.endsWith(".apk") && name.contains("-$flavor-")
            }
        return (supportedAbis + "universal").firstNotNullOfOrNull { abi ->
            candidates.find { assetMatchesAbi(it.name!!, abi) }
        }
    }

    /** The hex SHA-256 from a GitHub asset digest such as `sha256:ab12...`, or null. */
    fun sha256FromDigest(digest: String?): String? =
        digest
            ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.lowercase()
            ?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }

    /**
     * Matches [abi] as a delimited token inside [assetName], so that "x86" does not match an
     * "x86_64" asset name (both appear in the same release).
     */
    internal fun assetMatchesAbi(assetName: String, abi: String): Boolean {
        val regex = Regex("(?<![A-Za-z0-9_])${Regex.escape(abi)}(?![A-Za-z0-9_])")
        return regex.containsMatchIn(assetName)
    }

    @Serializable
    data class Release(
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        val draft: Boolean? = null,
        @SerialName("prerelease") val preRelease: Boolean? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<AssetsItem>? = null,
        val body: String? = null,
    )

    @Serializable
    data class AssetsItem(
        val name: String? = null,
        @SerialName("content_type") val contentType: String? = null,
        val size: Long? = null,
        @SerialName("download_count") val downloadCount: Int? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
        /** `sha256:<hex>`, computed by GitHub when the asset was uploaded. */
        val digest: String? = null,
    )

    private val pattern = Pattern.compile("""v?(\d+)\.(\d+)\.(\d+)(-(\w+)\.(\d+))?""")
    private val EMPTY_VERSION = Version.Stable()

    fun String?.toVersion(): Version =
        this?.run {
            val matcher = pattern.matcher(this)
            if (matcher.find()) {
                val major = matcher.group(1)?.toInt() ?: 0
                val minor = matcher.group(2)?.toInt() ?: 0
                val patch = matcher.group(3)?.toInt() ?: 0
                val buildNumber = matcher.group(6)?.toInt() ?: 0
                when (matcher.group(5).orEmpty()) {
                    "alpha" -> Version.Alpha(major, minor, patch, buildNumber)
                    "beta" -> Version.Beta(major, minor, patch, buildNumber)
                    "rc" -> Version.ReleaseCandidate(major, minor, patch, buildNumber)
                    else -> Version.Stable(major, minor, patch)
                }
            } else EMPTY_VERSION
        } ?: EMPTY_VERSION

    sealed class Version(val major: Int, val minor: Int, val patch: Int, val build: Int = 0) :
        Comparable<Version> {
        companion object {
            // private const val ABI = 1L
            private const val BUILD = 10L
            private const val VARIANT = 100L
            private const val PATCH = 10_000L
            private const val MINOR = 1_000_000L
            private const val MAJOR = 100_000_000L

            private const val STABLE = VARIANT * 4
            private const val ALPHA = VARIANT * 1
            private const val BETA = VARIANT * 2
            private const val RELEASE_CANDIDATE = VARIANT * 3
        }

        abstract fun toVersionName(): String

        abstract fun toNumber(): Long

        class Alpha(
            versionMajor: Int = 0,
            versionMinor: Int = 0,
            versionPatch: Int = 0,
            versionBuild: Int = 0,
        ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-alpha.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + ALPHA
        }

        class Beta(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
            Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-beta.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + BETA
        }

        class ReleaseCandidate(
            versionMajor: Int,
            versionMinor: Int,
            versionPatch: Int,
            versionBuild: Int,
        ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-rc.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + RELEASE_CANDIDATE
        }

        class Stable(versionMajor: Int = 0, versionMinor: Int = 0, versionPatch: Int = 0) :
            Version(versionMajor, versionMinor, versionPatch) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + STABLE
            // Prioritize stable versions

        }

        override operator fun compareTo(other: Version): Int =
            this.toNumber().compareTo(other.toNumber())
    }
}
