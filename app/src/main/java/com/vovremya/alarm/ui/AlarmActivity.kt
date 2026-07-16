package com.vovremya.alarm.ui

import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vovremya.alarm.VovremyaApplication
import com.vovremya.alarm.data.AppSettings
import com.vovremya.alarm.data.ScheduledAlarm
import com.vovremya.alarm.domain.AlarmPayload
import com.vovremya.alarm.localization.appLocale
import com.vovremya.alarm.localization.tr
import com.vovremya.alarm.ui.theme.VovremyaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val signalHandler = Handler(Looper.getMainLooper())
    private val autoSilence = Runnable { stopSignal() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val key = intent.getStringExtra(AlarmPayload.EXTRA_KEY).orEmpty()
        val title = intent.getStringExtra(AlarmPayload.EXTRA_TITLE).orEmpty().ifBlank { tr("Пора собираться") }
        val eventStart = intent.getLongExtra(AlarmPayload.EXTRA_EVENT_START, 0L)
        val location = intent.getStringExtra(AlarmPayload.EXTRA_LOCATION)
        val allDay = intent.getBooleanExtra(AlarmPayload.EXTRA_ALL_DAY, false)
        val soundEnabled = intent.getBooleanExtra(AlarmPayload.EXTRA_SOUND_ENABLED, true)
        val vibrationEnabled = intent.getBooleanExtra(AlarmPayload.EXTRA_VIBRATION_ENABLED, true)
        val soundUri = intent.getStringExtra(AlarmPayload.EXTRA_SOUND_URI).orEmpty()
        val snoozeMinutes = intent.getIntExtra(AlarmPayload.EXTRA_SNOOZE_MINUTES, 10).coerceIn(1, 120)
        val autoSilenceMinutes = intent.getIntExtra(AlarmPayload.EXTRA_AUTO_SILENCE_MINUTES, 10).coerceIn(1, 60)
        startSignal(soundEnabled, vibrationEnabled, soundUri, autoSilenceMinutes)
        setContent {
            val settings by (application as VovremyaApplication).container.settingsStore.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            VovremyaTheme(
                accentTheme = settings.accentTheme,
                customAccentColor = settings.customAccentColor,
                backgroundStyle = settings.backgroundStyle,
                darkTheme = true,
            ) {
                AlarmScreen(
                    title = title,
                    eventStartMillis = eventStart,
                    allDay = allDay,
                    location = location,
                    snoozeMinutes = snoozeMinutes,
                    onDismiss = { stopAndClose(key) },
                    onSnooze = {
                        snooze(
                            key = key,
                            title = title,
                            eventStart = eventStart,
                            location = location,
                            allDay = allDay,
                            soundEnabled = soundEnabled,
                            vibrationEnabled = vibrationEnabled,
                            soundUri = soundUri,
                            snoozeMinutes = snoozeMinutes,
                            autoSilenceMinutes = autoSilenceMinutes,
                        )
                        stopAndClose(key)
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        stopSignal()
        super.onDestroy()
    }

    private fun startSignal(
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        soundUri: String,
        autoSilenceMinutes: Int,
    ) {
        if (soundEnabled) {
            val configuredUri = soundUri.takeIf(String::isNotBlank)?.let(Uri::parse)
            val alarmUri = configuredUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, alarmUri)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }
        if (vibrationEnabled) {
            vibrator = getSystemService(Vibrator::class.java)?.apply {
                vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 350, 700), 0))
            }
        }
        signalHandler.postDelayed(autoSilence, autoSilenceMinutes * 60_000L)
    }

    private fun stopSignal() {
        signalHandler.removeCallbacks(autoSilence)
        ringtone?.stop()
        vibrator?.cancel()
        ringtone = null
    }

    private fun stopAndClose(key: String) {
        stopSignal()
        (application as VovremyaApplication).container.notificationHelper.cancelAlarm(key)
        finishAndRemoveTask()
    }

    private fun snooze(
        key: String,
        title: String,
        eventStart: Long,
        location: String?,
        allDay: Boolean,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        soundUri: String,
        snoozeMinutes: Int,
        autoSilenceMinutes: Int,
    ) {
        val alarmAt = System.currentTimeMillis() + snoozeMinutes * 60_000L
        (application as VovremyaApplication).container.alarmScheduler.schedule(
            ScheduledAlarm(
                key = "$key:snooze:$alarmAt",
                eventId = 0,
                eventStartMillis = eventStart,
                alarmAtMillis = alarmAt,
                title = title,
                location = location,
                calendarId = 0,
                calendarName = "",
                calendarColor = 0,
                allDay = allDay,
                soundEnabled = soundEnabled,
                vibrationEnabled = vibrationEnabled,
                soundUri = soundUri,
                snoozeMinutes = snoozeMinutes,
                autoSilenceMinutes = autoSilenceMinutes,
            ),
        )
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    eventStartMillis: Long,
    allDay: Boolean,
    location: String?,
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "alarmPulse")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alarmScale",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(lerp(primary, Color.Black, .62f), Color(0xFF151322), Color(0xFF101018)),
                ),
            )
            .padding(28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(126.dp)
                    .scale(scale)
                    .background(primary.copy(alpha = .2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(90.dp).background(primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Alarm, null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(40.dp))
            Text(tr("ПОРА СОБИРАТЬСЯ"), color = primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(
                if (allDay) tr("Весь день") else formatEventTime(eventStartMillis),
                color = Color.White,
                fontSize = if (allDay) 42.sp else 64.sp,
                lineHeight = 68.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (!location.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(location, color = Color(0xFFBDB7CC), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(56.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF241C52)),
            ) { Text(tr("Я встал"), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text(tr("Отложить на %d мин", snoozeMinutes), color = Color.White) }
        }
    }
}

private fun formatEventTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm", appLocale()))
