package com.vovremya.alarm

import android.content.Context
import com.vovremya.alarm.data.CalendarRepository
import com.vovremya.alarm.data.SettingsStore
import com.vovremya.alarm.domain.AlarmScheduler
import com.vovremya.alarm.domain.DailySyncScheduler
import com.vovremya.alarm.notifications.NotificationHelper
import com.vovremya.alarm.launcher.LauncherIconManager
import com.vovremya.alarm.update.UpdateManager

class AppContainer(context: Context) {
    val settingsStore = SettingsStore(context)
    val calendarRepository = CalendarRepository(context)
    val notificationHelper = NotificationHelper(context)
    val alarmScheduler = AlarmScheduler(context, calendarRepository, settingsStore)
    val dailySyncScheduler = DailySyncScheduler(context)
    val updateManager = UpdateManager(context, notificationHelper)
    val launcherIconManager = LauncherIconManager(context)
}
