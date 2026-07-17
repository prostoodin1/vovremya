package com.vovremya.alarm.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.vovremya.alarm.BuildConfig
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
    suspend fun checkAndDownloadUpdate(
        force: Boolean = true,
        allowPrerelease: Boolean = false,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val preferences = context.getSharedPreferences(UPDATE_PREFERENCES, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - preferences.getLong(KEY_LAST_CHECK, 0L) < AUTO_CHECK_INTERVAL_MILLIS) {
            return@withContext UpdateCheckResult.NotDue
        }
        if (!force) preferences.edit().putLong(KEY_LAST_CHECK, now).apply()
        val repository = BuildConfig.GITHUB_REPOSITORY.trim().trim('/')
        if (!repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            return@withContext UpdateCheckResult.NotConfigured
        }
        var temporary: File? = null
        runCatching {
            val releases = requestJsonArray("https://api.github.com/repos/$repository/releases?per_page=30")
            val release = selectRelease(releases, allowPrerelease)
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
            val directory = context.getExternalFilesDir("updates") ?: context.filesDir.resolve("updates")
            directory.mkdirs()
            val apk = directory.resolve("vovremya-$version.apk")
            val downloadFile = directory.resolve(".${apk.name}.download")
            temporary = downloadFile
            downloadFile.delete()
            download(apkUrl, downloadFile)
            validateDownloadedApk(downloadFile)
            if (apk.exists()) apk.delete()
            check(downloadFile.renameTo(apk)) { "Не удалось сохранить APK" }
            notificationHelper.showUpdate(version, apk)
            UpdateCheckResult.Downloaded(version, apk)
        }.getOrElse {
            temporary?.delete()
            UpdateCheckResult.Failed(it.message ?: "Ошибка сети")
        }
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

    internal fun selectRelease(releases: JSONArray, allowPrerelease: Boolean): JSONObject? =
        (0 until releases.length())
            .map(releases::getJSONObject)
            .filterNot { it.optBoolean("draft", false) }
            .filter { allowPrerelease || !it.optBoolean("prerelease", false) }
            .maxWithOrNull(
                Comparator { left, right ->
                    compareVersions(
                        left.optString("tag_name").removePrefix("v"),
                        right.optString("tag_name").removePrefix("v"),
                    )
                },
            )

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
    private fun validateDownloadedApk(apk: File) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("Загруженный файл не является APK")
        check(archive.packageName == context.packageName) { "APK выпущен для другого приложения" }
        check(versionCode(archive) > BuildConfig.VERSION_CODE) { "В APK нет более новой версии" }

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
    data class Downloaded(val version: String, val file: File) : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}
