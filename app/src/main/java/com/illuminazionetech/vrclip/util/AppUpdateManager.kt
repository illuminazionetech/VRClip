package com.illuminazionetech.vrclip.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.illuminazionetech.vrclip.App
import com.illuminazionetech.vrclip.BuildConfig
import com.illuminazionetech.vrclip.UpdateInstallReceiver
import com.illuminazionetech.vrclip.util.PreferenceUtil.getInt
import com.illuminazionetech.vrclip.util.PreferenceUtil.getString
import com.illuminazionetech.vrclip.util.PreferenceUtil.updateString
import com.illuminazionetech.vrclip.util.UpdateUtil.toVersion
import com.illuminazionetech.vrclip.util.UpdateUtil.version
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The in-app updater: finds the newest GitHub release for this build's channel, downloads the APK
 * matching the device ABI, verifies it, and hands it to [PackageInstaller].
 *
 * Every downloaded APK is checked before the installer ever sees it:
 * - its size and SHA-256 must match what GitHub reports for the release asset;
 * - it must be this app's package, with a higher version code than the installed one;
 * - it must be signed with the same certificate as the installed app. Android would refuse the
 *   update anyway, but checking first lets the app explain what happened (the 1.1.0 releases were
 *   signed with throwaway CI keys, so moving past them needs one reinstall) instead of showing a
 *   generic "App not installed".
 *
 * State is exposed as a [StateFlow] so the startup prompt, the settings page and the notification
 * all drive the same process.
 */
object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val RELEASES_URL =
        "https://api.github.com/repos/illuminazionetech/VRClip/releases?per_page=30"
    const val RELEASES_PAGE_URL = "https://github.com/illuminazionetech/VRClip/releases/latest"
    private const val SKIPPED_VERSION = "update_skipped_version"

    sealed interface State {
        data object Idle : State

        data object Checking : State

        data object UpToDate : State

        data class Available(val release: UpdateUtil.Release, val asset: UpdateUtil.AssetsItem) :
            State {
            val versionName: String
                get() = release.version().toVersionName()
        }

        data class Downloading(
            val release: UpdateUtil.Release,
            val asset: UpdateUtil.AssetsItem,
            val downloadedBytes: Long,
            val totalBytes: Long,
        ) : State {
            val progress: Float
                get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else -1f
        }

        data class Installing(val release: UpdateUtil.Release) : State

        /** The update is signed with another key: it can only be installed after uninstalling. */
        data class SignatureChanged(val release: UpdateUtil.Release) : State

        data class Failed(val reason: Reason, val release: UpdateUtil.Release? = null) : State
    }

    enum class Reason {
        Network,
        RateLimited,
        NoAssetForDevice,
        Corrupted,
        InstallBlocked,
        InstallFailed,
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var downloadJob: Job? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    private val json = Json { ignoreUnknownKeys = true }

    private val updateDir: File
        get() = File(App.context.cacheDir, "updates").apply { mkdirs() }

    /**
     * Looks for an update. An automatic check ([manual] false) stays quiet about failures and about
     * a version the user chose to skip; a manual check reports everything.
     */
    suspend fun check(manual: Boolean): State {
        _state.value = State.Checking
        val result =
            withContext(Dispatchers.IO) {
                runCatching { fetchReleases() }
                    .fold(
                        onSuccess = { releases -> evaluate(releases, manual) },
                        onFailure = {
                            Log.w(TAG, "Update check failed", it)
                            State.Failed(
                                if (it is RateLimitException) Reason.RateLimited else Reason.Network
                            )
                        },
                    )
            }
        _state.value = if (!manual && result is State.Failed) State.Idle else result
        return _state.value
    }

    private fun evaluate(releases: List<UpdateUtil.Release>, manual: Boolean): State {
        val stableOnly = UPDATE_CHANNEL.getInt() == STABLE
        val latest = UpdateUtil.selectRelease(releases, stableOnly) ?: return State.UpToDate
        val latestVersion = latest.version()
        if (latestVersion <= currentVersion()) return State.UpToDate
        if (!manual && SKIPPED_VERSION.getString() == latestVersion.toVersionName()) {
            return State.Idle
        }
        val asset =
            UpdateUtil.selectAsset(latest, BuildConfig.FLAVOR, Build.SUPPORTED_ABIS.toList())
                ?: return State.Failed(Reason.NoAssetForDevice, latest)
        return State.Available(latest, asset)
    }

    private fun fetchReleases(): List<UpdateUtil.Release> {
        val request =
            Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "VRClip/${BuildConfig.VERSION_NAME}")
                .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 403 || response.code == 429) throw RateLimitException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return json.decodeFromString(response.body.string())
        }
    }

    private class RateLimitException : IOException("GitHub API rate limit")

    fun skip(state: State.Available) {
        SKIPPED_VERSION.updateString(state.versionName)
        _state.value = State.Idle
    }

    fun dismiss() {
        if (_state.value !is State.Downloading && _state.value !is State.Installing) {
            _state.value = State.Idle
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /** Downloads, verifies and installs [available]. Progress is reported through [state]. */
    fun downloadAndInstall(context: Context, available: State.Available) {
        if (downloadJob?.isActive == true) return
        downloadJob =
            App.applicationScope.launch(Dispatchers.IO) {
                val (release, asset) = available
                try {
                    val apk = download(release, asset)
                    when (verify(context, apk, asset)) {
                        Verification.Ok -> {
                            _state.value = State.Installing(release)
                            install(context, apk)
                        }
                        Verification.SignatureMismatch ->
                            _state.value = State.SignatureChanged(release)
                        Verification.Invalid -> {
                            apk.delete()
                            _state.value = State.Failed(Reason.Corrupted, release)
                        }
                    }
                } catch (e: CancellationException) {
                    _state.value = available
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Update download failed", e)
                    _state.value = State.Failed(Reason.Network, release)
                }
            }
    }

    private suspend fun download(release: UpdateUtil.Release, asset: UpdateUtil.AssetsItem): File =
        withContext(Dispatchers.IO) {
            val target = File(updateDir, asset.name ?: "update.apk")
            val expectedSize = asset.size ?: -1L
            // A previous download of the very same asset can be reused as is.
            if (
                target.exists() && target.length() == expectedSize && digestMatches(target, asset)
            ) {
                return@withContext target
            }
            val partial = File(updateDir, "${target.name}.part")
            partial.delete()
            val request =
                Request.Builder()
                    .url(asset.browserDownloadUrl ?: throw IOException("missing download URL"))
                    .header("User-Agent", "VRClip/${BuildConfig.VERSION_NAME}")
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: expectedSize
                var downloaded = 0L
                var lastReported = -1L
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            // Report roughly every 256 KiB to keep recompositions cheap.
                            if (downloaded - lastReported >= 256 * 1024 || downloaded == total) {
                                lastReported = downloaded
                                _state.value = State.Downloading(release, asset, downloaded, total)
                            }
                        }
                    }
                }
            }
            if (expectedSize > 0 && partial.length() != expectedSize) {
                partial.delete()
                throw IOException("incomplete download: ${partial.length()} of $expectedSize")
            }
            target.delete()
            if (!partial.renameTo(target)) throw IOException("cannot move the downloaded file")
            target
        }

    private enum class Verification {
        Ok,
        SignatureMismatch,
        Invalid,
    }

    private fun digestMatches(file: File, asset: UpdateUtil.AssetsItem): Boolean {
        val expected = UpdateUtil.sha256FromDigest(asset.digest) ?: return true
        return sha256(file) == expected
    }

    private fun verify(context: Context, apk: File, asset: UpdateUtil.AssetsItem): Verification {
        if (!digestMatches(apk, asset)) return Verification.Invalid
        val pm = context.packageManager
        val archive =
            pm.getPackageArchiveInfoCompat(
                apk.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES,
            ) ?: return Verification.Invalid
        if (archive.packageName != context.packageName) return Verification.Invalid
        val installed =
            pm.getPackageInfoCompat(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (archive.longVersionCode <= installed.longVersionCode) return Verification.Invalid
        val installedCerts = installed.certificateDigests()
        val archiveCerts = archive.certificateDigests()
        // Some platform versions do not report certificates for an archive; the installer still
        // enforces the signature in that case, so only a definite mismatch is reported here.
        if (
            installedCerts.isNotEmpty() &&
                archiveCerts.isNotEmpty() &&
                installedCerts.intersect(archiveCerts).isEmpty()
        ) {
            return Verification.SignatureMismatch
        }
        return Verification.Ok
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                if (Build.VERSION.SDK_INT >= 31) {
                    // Lets later updates install without a prompt once VRClip owns its updates.
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                if (Build.VERSION.SDK_INT >= 34) setRequestUpdateOwnership(true)
            }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val intent =
                    Intent(context, UpdateInstallReceiver::class.java)
                        .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS)
                val flags =
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(sessionId)
            throw e
        }
    }

    /** Called by [UpdateInstallReceiver] when the installer reports a final failure. */
    internal fun onInstallResult(status: Int) {
        val release = (_state.value as? State.Installing)?.release
        _state.update {
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> State.Idle
                PackageInstaller.STATUS_FAILURE_ABORTED ->
                    release?.let { r ->
                        UpdateUtil.selectAsset(r, BuildConfig.FLAVOR, Build.SUPPORTED_ABIS.toList())
                            ?.let { State.Available(r, it) }
                    } ?: State.Idle
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    release?.let { State.SignatureChanged(it) }
                        ?: State.Failed(Reason.InstallFailed)
                PackageInstaller.STATUS_FAILURE_BLOCKED ->
                    State.Failed(Reason.InstallBlocked, release)
                else -> State.Failed(Reason.InstallFailed, release)
            }
        }
    }

    /** Removes downloaded APKs that are no newer than the running app. */
    fun cleanUp() {
        runCatching {
            val current = currentVersion()
            updateDir.listFiles()?.forEach { file ->
                val archiveVersion =
                    App.context.packageManager
                        .getPackageArchiveInfoCompat(file.absolutePath, 0)
                        ?.versionName
                        .toVersion()
                if (file.name.endsWith(".part") || archiveVersion <= current) file.delete()
            }
            // Leftover from the previous updater, which downloaded into external files.
            App.context.getExternalFilesDir("apk")?.let { File(it, "latest.apk").delete() }
        }
    }

    fun currentVersion(): UpdateUtil.Version = BuildConfig.VERSION_NAME.toVersion()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun PackageInfo.certificateDigests(): Set<String> {
        val info = signingInfo ?: return emptySet()
        val certificates =
            if (info.hasMultipleSigners()) info.apkContentsSigners
            else info.signingCertificateHistory
        return certificates
            .orEmpty()
            .map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString(
                    ""
                ) {
                    "%02x".format(it)
                }
            }
            .toSet()
    }

    private fun PackageManager.getPackageArchiveInfoCompat(path: String, flags: Int): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") getPackageArchiveInfo(path, flags)
        }

    private fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
        }
}
