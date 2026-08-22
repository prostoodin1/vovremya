package com.vovremya.alarm.update

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.vovremya.alarm.localization.tr
import com.vovremya.alarm.notifications.NotificationHelper
import com.vovremya.alarm.ui.theme.VovremyaTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateInstallerActivity : ComponentActivity() {
    private var apkPath: String? = null
    private var launchedSettings = false
    private var installerLaunched = false
    private var downgradeState by mutableStateOf(DowngradeState.IDLE)
    private var downgradeFileName by mutableStateOf("")
    private var downgradeError by mutableStateOf("")
    private val unknownSources = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (canInstallPackages()) launchInstaller() else finishUpdatePrompt()
    }
    private val createDowngradeDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument(APK_MIME),
    ) { uri ->
        if (uri == null) {
            downgradeState = DowngradeState.IDLE
        } else {
            copyDowngradeApk(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showAboveLockScreen()
        apkPath = intent.getStringExtra(EXTRA_APK_PATH)
        val version = intent.getStringExtra(EXTRA_VERSION).orEmpty().ifBlank { "—" }
        val isDowngrade = intent.getBooleanExtra(EXTRA_IS_DOWNGRADE, false)
        setContent {
            var installing by rememberSaveable { mutableStateOf(false) }
            var visible by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }
            BackHandler { finishUpdatePrompt() }
            VovremyaTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + scaleIn(
                            initialScale = .94f,
                            animationSpec = spring(dampingRatio = .86f, stiffness = 330f),
                        ),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 10.dp,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 28.dp, vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.size(88.dp),
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.SystemUpdate,
                                            contentDescription = null,
                                            modifier = Modifier.size(46.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    tr(if (isDowngrade) "Переход на старую версию" else "Доступно обновление"),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    tr("Версия %s уже загружена и готова к установке", version),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    tr(
                                        if (isDowngrade) {
                                            when (downgradeState) {
                                                DowngradeState.IDLE -> "Android запрещает ставить старую версию поверх новой. Сначала сохраним APK в папку «Загрузки»."
                                                DowngradeState.SAVING -> "Сохраняем проверенный APK в папку «Загрузки»…"
                                                DowngradeState.READY -> "APK сохранён: %s\n\n1. Удалите текущую версию кнопкой ниже.\n2. Откройте «Загрузки» и установите сохранённый APK."
                                                DowngradeState.FAILED -> "Не удалось сохранить APK: %s"
                                            }
                                        } else {
                                            "Установить сейчас? Android покажет системное подтверждение."
                                        },
                                        *when (downgradeState) {
                                            DowngradeState.READY -> arrayOf(downgradeFileName)
                                            DowngradeState.FAILED -> arrayOf(downgradeError)
                                            else -> emptyArray()
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (isDowngrade) {
                                            if (downgradeState == DowngradeState.READY) uninstallCurrentVersion()
                                            else prepareDowngrade(version)
                                        } else {
                                            installing = true
                                            prepareInstall()
                                        }
                                    },
                                    enabled = !installing && downgradeState != DowngradeState.SAVING,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                ) {
                                    if (installing || downgradeState == DowngradeState.SAVING) {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
                                    }
                                    Spacer(Modifier.size(10.dp))
                                    Text(
                                        tr(
                                            when {
                                                installing -> "Подготавливаем обновление…"
                                                !isDowngrade -> "Установить"
                                                downgradeState == DowngradeState.READY -> "Удалить текущую версию"
                                                downgradeState == DowngradeState.SAVING -> "Сохраняем APK…"
                                                else -> "Подготовить переход"
                                            },
                                        ),
                                    )
                                }
                                if (isDowngrade && downgradeState == DowngradeState.READY) {
                                    OutlinedButton(
                                        onClick = ::openDownloads,
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                    ) {
                                        Text(tr("Открыть «Загрузки»"))
                                    }
                                    Text(
                                        tr("При удалении выберите «Сохранить данные приложения», если Android предложит. Разрешения иногда нужно выдать заново."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                if (!isDowngrade) {
                                    OutlinedButton(
                                        onClick = { scheduleTonight(version) },
                                        enabled = !installing,
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                    ) {
                                        Text(tr("Установить ночью"))
                                    }
                                    Text(
                                        tr("В 03:00 Android откроет системное подтверждение установки"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                OutlinedButton(
                                    onClick = ::finishUpdatePrompt,
                                    enabled = !installing && downgradeState != DowngradeState.SAVING,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                ) {
                                    Text(tr("Отмена"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (launchedSettings && canInstallPackages()) launchInstaller()
    }

    private fun showAboveLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun prepareInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages()) {
            launchedSettings = true
            unknownSources.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
        } else {
            launchInstaller()
        }
    }

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private fun prepareDowngrade(version: String) {
        downgradeState = DowngradeState.SAVING
        downgradeError = ""
        downgradeFileName = "Vovremya-${version.replace(Regex("[^A-Za-z0-9._-]"), "-")}.apk"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            lifecycleScope.launch {
                runCatching { exportDowngradeToDownloads() }
                    .onSuccess { downgradeState = DowngradeState.READY }
                    .onFailure { showDowngradeError(it) }
            }
        } else {
            createDowngradeDocument.launch(downgradeFileName)
        }
    }

    private fun copyDowngradeApk(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val apk = validatedApk()
                    contentResolver.openOutputStream(uri, "w")?.use { output ->
                        apk.inputStream().buffered().use { input -> input.copyTo(output) }
                    } ?: error("Не удалось открыть выбранный файл")
                }
            }.onSuccess {
                downgradeState = DowngradeState.READY
            }.onFailure(::showDowngradeError)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun exportDowngradeToDownloads() = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, downgradeFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, APK_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Android не открыл папку «Загрузки»")
        try {
            val apk = validatedApk()
            contentResolver.openOutputStream(uri)?.use { output ->
                apk.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: error("Не удалось сохранить APK")
            contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun showDowngradeError(error: Throwable) {
        downgradeError = error.message ?: tr("неизвестная ошибка")
        downgradeState = DowngradeState.FAILED
    }

    private fun uninstallCurrentVersion() {
        NotificationHelper(this).cancelUpdate()
        startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).apply {
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
            },
        )
    }

    private fun openDownloads() {
        runCatching { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
            .onFailure {
                startActivity(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = APK_MIME
                    },
                )
            }
    }

    private fun launchInstaller() {
        if (installerLaunched) return
        launchedSettings = false
        val canonicalApk = runCatching(::validatedApk).getOrElse {
            finishUpdatePrompt()
            return
        }
        installerLaunched = true
        NotificationHelper(this).cancelUpdate()
        val uri = FileProvider.getUriForFile(this, "$packageName.files", canonicalApk)
        startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
            },
        )
        finish()
    }

    private fun scheduleTonight(version: String) {
        val apk = runCatching(::validatedApk).getOrElse {
            finishUpdatePrompt()
            return
        }
        val triggerAt = runCatching {
            UpdateNightScheduler(this).schedule(version, apk, isDowngrade = false)
        }.getOrElse {
            finishUpdatePrompt()
            return
        }
        NotificationHelper(this).showUpdateScheduled(version, triggerAt)
        finish()
    }

    private fun validatedApk(): File {
        val path = apkPath ?: error("Путь к APK не передан")
        val apk = File(path)
        val allowedDirectories = listOfNotNull(
            getExternalFilesDir("updates")?.canonicalFile,
            filesDir.resolve("updates").canonicalFile,
        )
        val canonicalApk = apk.canonicalFile
        check(canonicalApk.isFile && allowedDirectories.any { canonicalApk.toPath().startsWith(it.toPath()) }) {
            "APK не найден"
        }
        return canonicalApk
    }

    private fun finishUpdatePrompt() {
        NotificationHelper(this).cancelUpdate()
        finish()
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        const val EXTRA_APK_PATH = "apk_path"
        const val EXTRA_VERSION = "version"
        const val EXTRA_IS_DOWNGRADE = "is_downgrade"
    }

    private enum class DowngradeState { IDLE, SAVING, READY, FAILED }
}
