package com.vovremya.alarm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.NotificationsNone
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
import androidx.core.content.ContextCompat
import com.vovremya.alarm.VovremyaApplication
import com.vovremya.alarm.data.AlarmDelivery
import com.vovremya.alarm.data.AppSettings
import com.vovremya.alarm.data.QuickDismissMode
import com.vovremya.alarm.data.QuickDismissSettings
import com.vovremya.alarm.data.ScheduledAlarm
import com.vovremya.alarm.data.SignalEffects
import com.vovremya.alarm.data.TorchMode
import com.vovremya.alarm.domain.AlarmBehavior
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
    private var originalBrightness: Float? = null
    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var torchEnabled = false
    private var torchTogglesRemaining = 0
    private var torchBlinkMillis = 500L
    private val signalHandler = Handler(Looper.getMainLooper())
    private val autoSilence = Runnable { stopSignal() }
    private val torchPulse = object : Runnable {
        override fun run() {
            if (torchTogglesRemaining <= 0) {
                setTorch(false)
                return
            }
            setTorch(!torchEnabled)
            torchTogglesRemaining--
            signalHandler.postDelayed(this, torchBlinkMillis)
        }
    }

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
        val eventStart = intent.getLongExtra(AlarmPayload.EXTRA_EVENT_START, 0L)
        val location = intent.getStringExtra(AlarmPayload.EXTRA_LOCATION)
        val allDay = intent.getBooleanExtra(AlarmPayload.EXTRA_ALL_DAY, false)
        val delivery = intent.getStringExtra(AlarmPayload.EXTRA_DELIVERY)
            ?.let { stored -> AlarmDelivery.entries.firstOrNull { it.name == stored } }
            ?: AlarmDelivery.ALARM
        val isReminder = delivery == AlarmDelivery.SILENT_REMINDER
        val title = intent.getStringExtra(AlarmPayload.EXTRA_TITLE).orEmpty().ifBlank {
            tr(if (isReminder) "Напоминание о событии" else "Пора собираться")
        }
        val soundEnabled = !isReminder && intent.getBooleanExtra(AlarmPayload.EXTRA_SOUND_ENABLED, true)
        val vibrationEnabled = intent.getBooleanExtra(AlarmPayload.EXTRA_VIBRATION_ENABLED, true)
        val effects = SignalEffects(
            highBrightnessEnabled = intent.getBooleanExtra(AlarmPayload.EXTRA_HIGH_BRIGHTNESS, false),
            torchEnabled = intent.getBooleanExtra(AlarmPayload.EXTRA_TORCH_ENABLED, false),
            torchMode = intent.getStringExtra(AlarmPayload.EXTRA_TORCH_MODE)
                ?.let { stored -> TorchMode.entries.firstOrNull { it.name == stored } }
                ?: TorchMode.BLINK,
            torchBlinkMillis = intent.getIntExtra(AlarmPayload.EXTRA_TORCH_BLINK_MILLIS, 500)
                .coerceIn(100, 2_000),
            torchRepeatCount = intent.getIntExtra(AlarmPayload.EXTRA_TORCH_REPEAT_COUNT, 10)
                .coerceIn(1, 100),
            vibrationIntensity = intent.getIntExtra(AlarmPayload.EXTRA_VIBRATION_INTENSITY, 100)
                .coerceIn(1, 100),
        )
        val quickDismissSettings = QuickDismissSettings(
            enabled = intent.getBooleanExtra(AlarmPayload.EXTRA_QUICK_DISMISS_ENABLED, true),
            afterMinutes = intent.getIntExtra(AlarmPayload.EXTRA_QUICK_DISMISS_AFTER_MINUTES, 10 * 60)
                .coerceIn(0, 23 * 60 + 59),
            mode = intent.getStringExtra(AlarmPayload.EXTRA_QUICK_DISMISS_MODE)
                ?.let { stored -> QuickDismissMode.entries.firstOrNull { it.name == stored } }
                ?: QuickDismissMode.BUTTON,
        )
        val quickDismiss = !isReminder && AlarmBehavior.usesQuickDismiss(quickDismissSettings)
        val soundUri = intent.getStringExtra(AlarmPayload.EXTRA_SOUND_URI).orEmpty()
        val snoozeMinutes = intent.getIntExtra(AlarmPayload.EXTRA_SNOOZE_MINUTES, 10).coerceIn(1, 120)
        val autoSilenceMinutes = intent.getIntExtra(AlarmPayload.EXTRA_AUTO_SILENCE_MINUTES, 10).coerceIn(1, 60)
        startSignal(soundEnabled, vibrationEnabled, soundUri, autoSilenceMinutes, effects)
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
                    isReminder = isReminder,
                    vibrationEnabled = vibrationEnabled,
                    quickDismiss = quickDismiss,
                    quickDismissMode = quickDismissSettings.mode,
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
                            effects = effects,
                            quickDismiss = quickDismissSettings,
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
        effects: SignalEffects,
    ) {
        applyHighBrightness(effects.highBrightnessEnabled)
        startTorch(effects)
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
            val amplitude = AlarmBehavior.vibrationAmplitude(effects.vibrationIntensity)
            vibrator = getSystemService(Vibrator::class.java)?.apply {
                vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 700, 350, 700),
                        intArrayOf(0, amplitude, 0, amplitude),
                        0,
                    ),
                )
            }
        }
        signalHandler.postDelayed(autoSilence, autoSilenceMinutes * 60_000L)
    }

    private fun applyHighBrightness(enabled: Boolean) {
        if (!enabled || originalBrightness != null) return
        originalBrightness = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = 1f }
    }

    private fun restoreBrightness() {
        val brightness = originalBrightness ?: return
        window.attributes = window.attributes.apply { screenBrightness = brightness }
        originalBrightness = null
    }

    private fun startTorch(effects: SignalEffects) {
        if (!effects.torchEnabled || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = getSystemService(CameraManager::class.java) ?: return
        val cameraId = runCatching {
            manager.cameraIdList.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull() ?: return
        cameraManager = manager
        torchCameraId = cameraId
        if (effects.torchMode == TorchMode.STEADY) {
            setTorch(true)
        } else {
            torchBlinkMillis = effects.torchBlinkMillis.coerceIn(100, 2_000).toLong()
            torchTogglesRemaining = (effects.torchRepeatCount.coerceIn(1, 100) * 2) - 1
            setTorch(true)
            signalHandler.postDelayed(torchPulse, torchBlinkMillis)
        }
    }

    private fun setTorch(enabled: Boolean) {
        val manager = cameraManager ?: return
        val cameraId = torchCameraId ?: return
        runCatching { manager.setTorchMode(cameraId, enabled) }
            .onSuccess { torchEnabled = enabled }
        if (!enabled) torchEnabled = false
    }

    private fun stopSignal() {
        signalHandler.removeCallbacks(autoSilence)
        ringtone?.stop()
        vibrator?.cancel()
        signalHandler.removeCallbacks(torchPulse)
        setTorch(false)
        restoreBrightness()
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
        effects: SignalEffects,
        quickDismiss: QuickDismissSettings,
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
                effects = effects,
                quickDismiss = quickDismiss,
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
    isReminder: Boolean,
    vibrationEnabled: Boolean,
    quickDismiss: Boolean,
    quickDismissMode: QuickDismissMode,
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
            .then(
                if (quickDismiss && quickDismissMode == QuickDismissMode.TAP_ANYWHERE) {
                    Modifier.clickable(onClick = onDismiss)
                } else {
                    Modifier
                },
            )
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
                    Icon(
                        if (isReminder) Icons.Rounded.NotificationsNone else Icons.Rounded.Alarm,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
            Text(
                tr(
                    when {
                        !isReminder -> "ПОРА СОБИРАТЬСЯ"
                        vibrationEnabled -> "НАПОМИНАНИЕ · ВИБРАЦИЯ"
                        else -> "ТИХОЕ НАПОМИНАНИЕ"
                    },
                ),
                color = primary,
                fontWeight = FontWeight.SemiBold,
            )
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
            if (quickDismiss && quickDismissMode == QuickDismissMode.TAP_ANYWHERE) {
                Text(
                    tr("Коснитесь экрана, чтобы выключить"),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF241C52)),
                ) {
                    Text(
                        tr(
                            when {
                                isReminder -> "Закрыть"
                                quickDismiss -> "Готово"
                                else -> "Я встал"
                            },
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (!isReminder && !quickDismiss) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(20.dp),
                ) { Text(tr("Отложить на %d мин", snoozeMinutes), color = Color.White) }
            }
        }
    }
}

private fun formatEventTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm", appLocale()))
