package com.vovremya.alarm.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.vovremya.alarm.data.LauncherIcon

class LauncherIconManager(private val context: Context) {
    fun apply(icon: LauncherIcon): Boolean {
        val selected = component(icon)
        val selectedEnabled = runCatching {
            context.packageManager.setComponentEnabledSetting(
                selected,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }.isSuccess
        if (!selectedEnabled) return false
        LauncherIcon.entries.filterNot { it == icon }.forEach { candidate ->
            runCatching {
                context.packageManager.setComponentEnabledSetting(
                    component(candidate),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
        return true
    }

    private fun component(icon: LauncherIcon): ComponentName = ComponentName(
        context,
        "$COMPONENT_PACKAGE.Launcher${icon.aliasSuffix}",
    )

    private val LauncherIcon.aliasSuffix: String
        get() = name.lowercase().replaceFirstChar(Char::uppercase)

    private companion object {
        // Activity aliases live in the manifest namespace, which differs from the
        // application id in debug builds (com.vovremya.alarm.debug).
        const val COMPONENT_PACKAGE = "com.vovremya.alarm"
    }
}
