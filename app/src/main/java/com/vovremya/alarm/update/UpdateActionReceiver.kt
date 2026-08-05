package com.vovremya.alarm.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vovremya.alarm.notifications.NotificationHelper
import java.io.File

class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val helper = NotificationHelper(context)
        val scheduler = UpdateNightScheduler(context)
        when (intent?.action) {
            ACTION_CANCEL_UPDATE -> {
                scheduler.cancel()
                helper.cancelUpdate()
            }

            ACTION_INSTALL_TONIGHT -> {
                val payload = intent.payload() ?: return
                val triggerAt = runCatching {
                    scheduler.schedule(payload.version, payload.apk, payload.isDowngrade)
                }.getOrNull() ?: return
                helper.showUpdateScheduled(payload.version, triggerAt)
            }

            ACTION_SHOW_NIGHT_UPDATE -> {
                val payload = intent.payload() ?: run {
                    scheduler.clear()
                    return
                }
                scheduler.clear()
                helper.showUpdate(payload.version, payload.apk, payload.isDowngrade, scheduled = true)
            }
        }
    }

    private fun Intent.payload(): Payload? {
        val version = getStringExtra(EXTRA_VERSION).orEmpty()
        val apk = getStringExtra(EXTRA_APK_PATH)?.let(::File) ?: return null
        if (version.isBlank() || !apk.isFile) return null
        return Payload(version, apk, getBooleanExtra(EXTRA_IS_DOWNGRADE, false))
    }

    private data class Payload(val version: String, val apk: File, val isDowngrade: Boolean)

    companion object {
        const val ACTION_CANCEL_UPDATE = "com.vovremya.alarm.action.CANCEL_UPDATE"
        const val ACTION_INSTALL_TONIGHT = "com.vovremya.alarm.action.INSTALL_UPDATE_TONIGHT"
        const val ACTION_SHOW_NIGHT_UPDATE = "com.vovremya.alarm.action.SHOW_NIGHT_UPDATE"
        const val EXTRA_APK_PATH = "apk_path"
        const val EXTRA_VERSION = "version"
        const val EXTRA_IS_DOWNGRADE = "is_downgrade"
    }
}
