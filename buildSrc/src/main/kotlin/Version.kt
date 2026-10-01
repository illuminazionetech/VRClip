/**
 * Build-time versioning source of truth. Mirrors the runtime `Version` parser in
 * `UpdateUtil.kt` (`app/src/main/java/com/illuminazionetech/vrclip/util/UpdateUtil.kt`), the
 * versionCode formula, variant ordering (Alpha < Beta < RC < Stable) and tag format
 * (`vMAJOR.MINOR.PATCH[-alpha|beta|rc.BUILD]`) must stay identical between the two, since one
 * builds the APK's version and the other compares it against GitHub release tags to detect
 * updates. `VersionParityTest` in `app/src/test` asserts both parse the same sample strings
 * identically, update both together and keep that test green.
 *
 * Nobody edits a version number by hand: CI computes the next version from the commits since the
 * last release tag (`.github/scripts/next-version.sh`) and passes it in with
 * `-Pvrclip.versionName=X.Y.Z`. Local builds fall back to the most recent `vX.Y.Z` git tag.
 */
sealed class Version(val major: Int, val minor: Int, val patch: Int, val build: Int = 0) {
    abstract val name: String
    abstract val code: Long

    class Alpha(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
        Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override val name: String
            get() = "${major}.${minor}.${patch}-alpha.$build"

        override val code: Long
            get() = major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + ALPHA
    }

    class Beta(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
        Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override val name: String
            get() = "${major}.${minor}.${patch}-beta.$build"

        override val code: Long
            get() = major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + BETA
    }

    class Stable(versionMajor: Int, versionMinor: Int, versionPatch: Int) :
        Version(versionMajor, versionMinor, versionPatch) {
        override val name: String
            get() = "${major}.${minor}.${patch}"

        override val code: Long
            get() = major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + STABLE
    }

    class ReleaseCandidate(
        versionMajor: Int,
        versionMinor: Int,
        versionPatch: Int,
        versionBuild: Int,
    ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override val name: String
            get() = "${major}.${minor}.${patch}-rc.$build"

        override val code: Long
            get() =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + RELEASE_CANDIDATE
    }

    companion object {
        private val pattern = Regex("""v?(\d+)\.(\d+)\.(\d+)(-(\w+)\.(\d+))?""")

        /** Parses `1.2.3`, `v1.2.3`, `1.2.3-beta.4`; returns null for anything else. */
        fun parse(text: String): Version? {
            val match = pattern.find(text.trim()) ?: return null
            val (major, minor, patch) = match.destructured.let { (a, b, c) ->
                Triple(a.toInt(), b.toInt(), c.toInt())
            }
            val build = match.groupValues[6].toIntOrNull() ?: 0
            return when (match.groupValues[5]) {
                "alpha" -> Alpha(major, minor, patch, build)
                "beta" -> Beta(major, minor, patch, build)
                "rc" -> ReleaseCandidate(major, minor, patch, build)
                else -> Stable(major, minor, patch)
            }
        }
    }
}

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

/** Name of the Gradle property CI uses to inject the computed release version. */
const val VERSION_NAME_PROPERTY = "vrclip.versionName"

private val FALLBACK_VERSION = Version.Stable(0, 0, 1)

/**
 * Resolves the version for this build: the explicit [VERSION_NAME_PROPERTY] when CI passes one,
 * otherwise the latest `vX.Y.Z` tag reachable from HEAD ([latestTag], queried lazily), otherwise
 * [FALLBACK_VERSION] (shallow clones without tags).
 */
fun resolveAppVersion(explicitVersionName: String?, latestTag: () -> String?): Version {
    explicitVersionName
        ?.takeIf { it.isNotBlank() }
        ?.let { name ->
            return Version.parse(name)
                ?: error("$VERSION_NAME_PROPERTY='$name' is not a valid MAJOR.MINOR.PATCH version")
        }
    return latestTag()?.let { Version.parse(it) } ?: FALLBACK_VERSION
}
