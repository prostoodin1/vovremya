package com.vovremya.alarm.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.vovremya.alarm.BuildConfig
import com.vovremya.alarm.data.UpdateChannel
import com.vovremya.alarm.notifications.NotificationHelper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class UpdateManager(
    private val context: Context,
    private val notificationHelper: NotificationHelper,
) {
    suspend fun loadReleaseCatalog(): Result<List<AvailableRelease>> = withContext(Dispatchers.IO) {
        runCatching {
            val repository = configuredRepository()
            val releases = requestJsonArray("https://api.github.com/repos/$repository/releases?per_page=50")
            parseAvailableReleases(releases)
        }
    }

    suspend fun downloadRelease(release: AvailableRelease): UpdateCheckResult = withContext(Dispatchers.IO) {
        downloadAndPrepare(
            version = release.version,
            apkUrl = release.apkUrl,
            requireNewerVersion = false,
            isDowngrade = release.relation == ReleaseRelation.OLDER,
            notifyWhenReady = false,
        )
    }

    suspend fun installRelease(release: AvailableRelease): UpdateCheckResult {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val apk = releaseFile(release.version)
                check(apk.isFile && apk.length() > 0L) { "Файл версии не найден — скачайте его снова" }
                validateDownloadedApk(apk, requireNewerVersion = false)
                UpdateCheckResult.Downloaded(
                    version = release.version,
                    file = apk,
                    isDowngrade = release.relation == ReleaseRelation.OLDER,
                )
            }.getOrElse {
                UpdateCheckResult.Failed(it.message ?: "Не удалось подготовить установку")
            }
        }
        if (result is UpdateCheckResult.Downloaded) {
            notificationHelper.openUpdatePrompt(result.version, result.file, result.isDowngrade)
        }
        return result
    }

    fun downloadedReleaseTags(releases: List<AvailableRelease>): Set<String> =
        releases.asSequence()
            .filter { release -> releaseFile(release.version).let { it.isFile && it.length() > 0L } }
            .mapTo(mutableSetOf(), AvailableRelease::tag)

    suspend fun checkAndDownloadUpdate(
        force: Boolean = true,
        channel: UpdateChannel = UpdateChannel.STABLE,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val preferences = context.getSharedPreferences(UPDATE_PREFERENCES, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - preferences.getLong(KEY_LAST_CHECK, 0L) < AUTO_CHECK_INTERVAL_MILLIS) {
            return@withContext UpdateCheckResult.NotDue
        }
        if (!force) preferences.edit().putLong(KEY_LAST_CHECK, now).apply()
        val repository = runCatching(::configuredRepository).getOrElse {
            return@withContext UpdateCheckResult.NotConfigured
        }
        runCatching {
            val releases = requestJsonArray("https://api.github.com/repos/$repository/releases?per_page=30")
            val release = selectRelease(releases, channel)
                ?: return@runCatching UpdateCheckResult.UpToDate
            val version = release.getString("tag_name").removePrefix("v")
            if (compareVersions(version, BuildConfig.VERSION_NAME.removeSuffix("-debug")) <= 0) {
                return@runCatching UpdateCheckResult.UpToDate
            }
            val assets = release.getJSONArray("assets")
            val apkUrl = (0 until assets.length())
                .map(assets::getJSONObject)
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.getString("browser_download_url")
                ?: return@runCatching UpdateCheckResult.NoApkAsset
            downloadAndPrepare(
                version = version,
                apkUrl = apkUrl,
                requireNewerVersion = true,
                isDowngrade = false,
                notifyWhenReady = true,
            )
        }.getOrElse {
            UpdateCheckResult.Failed(it.message ?: "Ошибка сети")
        }
    }

    private fun configuredRepository(): String {
        val repository = BuildConfig.GITHUB_REPOSITORY.trim().trim('/')
        check(repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            "GitHub-репозиторий не настроен"
        }
        return repository
    }

    private fun downloadAndPrepare(
        version: String,
        apkUrl: String,
        requireNewerVersion: Boolean,
        isDowngrade: Boolean,
        notifyWhenReady: Boolean,
    ): UpdateCheckResult {
        var temporary: File? = null
        return runCatching {
            val directory = updateDirectory()
            directory.mkdirs()
            val apk = releaseFile(version)
            val downloadFile = directory.resolve(".${apk.name}.download")
            temporary = downloadFile
            downloadFile.delete()
            download(apkUrl, downloadFile)
            validateDownloadedApk(downloadFile, requireNewerVersion)
            if (apk.exists()) apk.delete()
            check(downloadFile.renameTo(apk)) { "Не удалось сохранить APK" }
            if (notifyWhenReady) notificationHelper.showUpdate(version, apk, isDowngrade)
            UpdateCheckResult.Downloaded(version, apk, isDowngrade)
        }.getOrElse {
            temporary?.delete()
            UpdateCheckResult.Failed(it.message ?: "Ошибка загрузки")
        }
    }

    private fun updateDirectory(): File =
        context.getExternalFilesDir("updates") ?: context.filesDir.resolve("updates")

    internal fun releaseFile(version: String): File {
        val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "-")
        return updateDirectory().resolve("vovremya-$safeVersion.apk")
    }

    private fun requestJsonArray(url: String): JSONArray {
        val connection = open(url)
        return try {
            check(connection.responseCode in 200..299) { "GitHub вернул ${connection.responseCode}" }
            JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    internal fun selectRelease(releases: JSONArray, channel: UpdateChannel): JSONObject? =
        (0 until releases.length())
            .map(releases::getJSONObject)
            .filterNot { it.optBoolean("draft", false) }
            .filter { releaseChannel(it) == channel }
            .maxWithOrNull(
                Comparator { left, right ->
                    compareVersions(
                        left.optString("tag_name").removePrefix("v"),
                        right.optString("tag_name").removePrefix("v"),
                    )
                },
            )

    internal fun parseAvailableReleases(releases: JSONArray): List<AvailableRelease> =
        (0 until releases.length())
            .map(releases::getJSONObject)
            .filterNot { it.optBoolean("draft", false) }
            .mapNotNull { release ->
                val channel = releaseChannel(release) ?: return@mapNotNull null
                val tag = release.optString("tag_name")
                val version = tag.removePrefix("v")
                if (version.isBlank()) return@mapNotNull null
                val assets = release.optJSONArray("assets") ?: return@mapNotNull null
                val apk = (0 until assets.length())
                    .map(assets::getJSONObject)
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    ?: return@mapNotNull null
                val comparison = compareVersions(version, BuildConfig.VERSION_NAME.removeSuffix("-debug"))
                AvailableRelease(
                    tag = tag,
                    version = version,
                    name = release.optString("name").ifBlank { tag },
                    prerelease = release.optBoolean("prerelease", false),
                    channel = channel,
                    publishedAt = release.optString("published_at"),
                    apkUrl = apk.optString("browser_download_url"),
                    relation = when {
                        comparison > 0 -> ReleaseRelation.NEWER
                        comparison < 0 -> ReleaseRelation.OLDER
                        else -> ReleaseRelation.CURRENT
                    },
                ).takeIf { it.apkUrl.startsWith("https://") }
            }
            .sortedWith { left, right -> compareVersions(right.version, left.version) }

    internal fun releaseChannel(release: JSONObject): UpdateChannel? {
        if (!release.optBoolean("prerelease", false)) return UpdateChannel.STABLE
        val identity = "${release.optString("tag_name")} ${release.optString("name")}".lowercase()
        return if ("alpha" in identity) null else UpdateChannel.BETA
    }

    private fun download(url: String, destination: File) {
        val connection = open(url)
        try {
            check(connection.responseCode in 200..299) { "Загрузка вернула ${connection.responseCode}" }
            connection.inputStream.use { input ->
                destination.outputStream().buffered().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        setRequestProperty("User-Agent", "Vovremya/${BuildConfig.VERSION_NAME}")
    }

    @Suppress("DEPRECATION")
    private fun validateDownloadedApk(apk: File, requireNewerVersion: Boolean) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("Загруженный файл не является APK")
        check(archive.packageName == context.packageName) { "APK выпущен для другого приложения" }
        if (requireNewerVersion) {
            check(versionCode(archive) > BuildConfig.VERSION_CODE) { "В APK нет более новой версии" }
        }

        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archiveCertificates = certificateDigests(archive)
        val installedCertificates = certificateDigests(installed)
        check(archiveCertificates.isNotEmpty() && archiveCertificates == installedCertificates) {
            "Подпись APK не совпадает с установленным приложением"
        }
    }

    @Suppress("DEPRECATION")
    private fun certificateDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            info.signatures.orEmpty()
        }
        return signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    internal fun compareVersions(left: String, right: String): Int {
        val a = SemanticVersion.parse(left)
        val b = SemanticVersion.parse(right)
        repeat(maxOf(a.core.size, b.core.size)) { index ->
            val comparison = a.core.getOrElse(index) { 0 }.compareTo(b.core.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        if (a.preRelease.isEmpty() && b.preRelease.isNotEmpty()) return 1
        if (a.preRelease.isNotEmpty() && b.preRelease.isEmpty()) return -1
        repeat(maxOf(a.preRelease.size, b.preRelease.size)) { index ->
            val leftPart = a.preRelease.getOrNull(index) ?: return -1
            val rightPart = b.preRelease.getOrNull(index) ?: return 1
            val leftNumber = leftPart.toIntOrNull()
            val rightNumber = rightPart.toIntOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> leftPart.compareTo(rightPart, ignoreCase = true)
            }
            if (comparison != 0) return comparison
        }
        return 0
    }

    private data class SemanticVersion(
        val core: List<Int>,
        val preRelease: List<String>,
    ) {
        companion object {
            fun parse(value: String): SemanticVersion {
                val normalized = value.trim().removePrefix("v").substringBefore('+')
                val parts = normalized.split('-', limit = 2)
                return SemanticVersion(
                    core = parts.first().split('.').map { it.toIntOrNull() ?: 0 },
                    preRelease = parts.getOrNull(1)?.split('.')?.filter(String::isNotBlank).orEmpty(),
                )
            }
        }
    }

    private companion object {
        const val UPDATE_PREFERENCES = "github_update_state"
        const val KEY_LAST_CHECK = "last_automatic_check"
        const val AUTO_CHECK_INTERVAL_MILLIS = 20L * 60 * 60_000
    }
}

sealed interface UpdateCheckResult {
    data object NotConfigured : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object NoApkAsset : UpdateCheckResult
    data object NotDue : UpdateCheckResult
    data class Downloaded(
        val version: String,
        val file: File,
        val isDowngrade: Boolean = false,
    ) : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}

data class AvailableRelease(
    val tag: String,
    val version: String,
    val name: String,
    val prerelease: Boolean,
    val channel: UpdateChannel,
    val publishedAt: String,
    val apkUrl: String,
    val relation: ReleaseRelation,
)

enum class ReleaseRelation { NEWER, CURRENT, OLDER }
