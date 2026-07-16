package com.vovremya.alarm.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vovremya.alarm.ui.theme.VovremyaTheme
import com.vovremya.alarm.localization.tr
import java.io.File

class UpdateInstallerActivity : ComponentActivity() {
    private var apkPath: String? = null
    private var launchedSettings = false
    private val unknownSources = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (canInstallPackages()) launchInstaller() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apkPath = intent.getStringExtra(EXTRA_APK_PATH)
        setContent {
            VovremyaTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                ) {
                    Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                    CircularProgressIndicator()
                    Text(tr("Подготавливаем обновление…"), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        prepareInstall()
    }

    override fun onResume() {
        super.onResume()
        if (launchedSettings && canInstallPackages()) launchInstaller()
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
        launchedSettings = false
        val path = apkPath ?: return finish()
        val apk = File(path)
        val allowedDirectories = listOfNotNull(
            getExternalFilesDir("updates")?.canonicalFile,
            filesDir.resolve("updates").canonicalFile,
        )
        val canonicalApk = apk.canonicalFile
        if (!canonicalApk.isFile || allowedDirectories.none { canonicalApk.toPath().startsWith(it.toPath()) }) {
            finish()
            return
        }
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

    companion object {
        const val EXTRA_APK_PATH = "apk_path"
    }
}
