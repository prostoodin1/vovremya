package com.vovremya.alarm.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.vovremya.alarm.localization.tr
import com.vovremya.alarm.notifications.NotificationHelper
import com.vovremya.alarm.ui.theme.VovremyaTheme
import java.io.File

class UpdateInstallerActivity : ComponentActivity() {
    private var apkPath: String? = null
    private var launchedSettings = false
    private var installerLaunched = false
    private val unknownSources = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (canInstallPackages()) launchInstaller() else finishUpdatePrompt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showAboveLockScreen()
        apkPath = intent.getStringExtra(EXTRA_APK_PATH)
        val version = intent.getStringExtra(EXTRA_VERSION).orEmpty().ifBlank { "—" }
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
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
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
                                    tr("Доступно обновление"),
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
                                    tr("Установить сейчас? Android покажет системное подтверждение."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        installing = true
                                        prepareInstall()
                                    },
                                    enabled = !installing,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                ) {
                                    if (installing) {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
                                    }
                                    Spacer(Modifier.size(10.dp))
                                    Text(tr(if (installing) "Подготавливаем обновление…" else "Установить"))
                                }
                                OutlinedButton(
                                    onClick = ::finishUpdatePrompt,
                                    enabled = !installing,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                ) {
                                    Text(tr("Позже"))
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

    private fun launchInstaller() {
        if (installerLaunched) return
        launchedSettings = false
        val path = apkPath ?: return finishUpdatePrompt()
        val apk = File(path)
        val allowedDirectories = listOfNotNull(
            getExternalFilesDir("updates")?.canonicalFile,
            filesDir.resolve("updates").canonicalFile,
        )
        val canonicalApk = apk.canonicalFile
        if (!canonicalApk.isFile || allowedDirectories.none { canonicalApk.toPath().startsWith(it.toPath()) }) {
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

    private fun finishUpdatePrompt() {
        NotificationHelper(this).cancelUpdate()
        finish()
    }

    companion object {
        const val EXTRA_APK_PATH = "apk_path"
        const val EXTRA_VERSION = "version"
    }
}
