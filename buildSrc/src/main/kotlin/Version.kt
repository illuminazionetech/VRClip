/**
 * Build-time versioning source of truth. Mirrors the runtime `Version` parser in
 * `UpdateUtil.kt` (`app/src/main/java/com/illuminazionetech/vrclip/util/UpdateUtil.kt`) — the
 * versionCode formula, variant ordering (Alpha < Beta < RC < Stable) and tag format
 * (`vMAJOR.MINOR.PATCH[-alpha|beta|rc.BUILD]`) must stay identical between the two, since one
 * builds the APK's version and the other compares it against GitHub release tags to detect
 * updates. `VersionParityTest` in `app/src/test` asserts both parse the same sample strings
 * identically — update both together and keep that test green.
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

val currentVersion: Version = Version.Stable(versionMajor = 1, versionMinor = 0, versionPatch = 2)
