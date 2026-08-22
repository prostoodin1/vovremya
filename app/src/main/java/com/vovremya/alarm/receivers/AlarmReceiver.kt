package com.vovremya.alarm.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vovremya.alarm.VovremyaApplication

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VovremyaApplication
        app.container.notificationHelper.showAlarm(intent)
    }
}
