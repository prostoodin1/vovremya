package com.vovremya.alarm.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.vovremyaDataStore by preferencesDataStore(name = "vovremya_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val leadMinutes = intPreferencesKey("lead_minutes")
        val latestEventMinutes = intPreferencesKey("latest_event_minutes")
        val latestEventEnabled = booleanPreferencesKey("latest_event_enabled")
        val dailySyncMinutes = intPreferencesKey("daily_sync_minutes")
        val lookAheadDays = intPreferencesKey("look_ahead_days")
        val allEventsPerDay = booleanPreferencesKey("all_events_per_day")
        val includeAllDayEvents = booleanPreferencesKey("include_all_day_events")
        val allDayEventMinutes = intPreferencesKey("all_day_event_minutes")
        val alarmSoundEnabled = booleanPreferencesKey("alarm_sound_enabled")
        val alarmVibrationEnabled = booleanPreferencesKey("alarm_vibration_enabled")
        val alarmSoundUri = stringPreferencesKey("alarm_sound_uri")
        val snoozeMinutes = intPreferencesKey("snooze_minutes")
        val autoSilenceMinutes = intPreferencesKey("auto_silence_minutes")
        val enabledDays = stringPreferencesKey("enabled_days")
        val calendarIds = stringPreferencesKey("calendar_ids")
        val automaticUpdates = booleanPreferencesKey("automatic_updates")
        val themeMode = stringPreferencesKey("theme_mode")
        val accentTheme = stringPreferencesKey("accent_theme")
        val customAccentColor = intPreferencesKey("custom_accent_color")
        val backgroundStyle = stringPreferencesKey("background_style")
        val scheduledAlarms = stringPreferencesKey("scheduled_alarms")
        val lastSyncMillis = longPreferencesKey("last_sync_millis")
    }

    val settings: Flow<AppSettings> = context.vovremyaDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map(::settingsFrom)

    val scheduledAlarms: Flow<List<ScheduledAlarm>> = context.vovremyaDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { decodeAlarms(it[Keys.scheduledAlarms]) }

    val lastSyncMillis: Flow<Long?> = context.vovremyaDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[Keys.lastSyncMillis] }

    suspend fun setLeadMinutes(value: Int) = context.vovremyaDataStore.edit {
        it[Keys.leadMinutes] = value.coerceIn(0, 14 * 24 * 60)
    }

    suspend fun setLatestEventMinutes(value: Int) = context.vovremyaDataStore.edit {
        it[Keys.latestEventMinutes] = value.coerceIn(0, 23 * 60 + 59)
    }

    suspend fun setLatestEventEnabled(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.latestEventEnabled] = enabled
    }

    suspend fun setDailySyncMinutes(value: Int) = context.vovremyaDataStore.edit {
        it[Keys.dailySyncMinutes] = value.coerceIn(0, 23 * 60 + 59)
    }

    suspend fun setLookAheadDays(value: Int) = context.vovremyaDataStore.edit {
        it[Keys.lookAheadDays] = value.coerceIn(1, 90)
    }

    suspend fun setAllEventsPerDay(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.allEventsPerDay] = enabled
    }

    suspend fun setIncludeAllDayEvents(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.includeAllDayEvents] = enabled
    }

    suspend fun setAllDayEventMinutes(value: Int) = context.vovremyaDataStore.edit {
        it[Keys.allDayEventMinutes] = value.coerceIn(0, 23 * 60 + 59)
    }

    suspend fun setAlarmSoundEnabled(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.alarmSoundEnabled] = enabled
    }

    suspend fun setAlarmVibrationEnabled(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.alarmVibrationEnabled] = enabled
    }

    suspend fun setAlarmSoundUri(uri: String?) = context.vovremyaDataStore.edit {
        it[Keys.alarmSoundUri] = uri.orEmpty()
    }

    suspend fun setSnoozeMinutes(value: Int) = context.vovremyaDataStore.edit {
        it[Keys.snoozeMinutes] = value.coerceIn(1, 120)
    }

    suspend fun setAutoSilenceMinutes(value: Int) = context.vovremyaDataStore.edit {
        it[Keys.autoSilenceMinutes] = value.coerceIn(1, 60)
    }

    suspend fun setEnabledDays(days: Set<DayOfWeek>) = context.vovremyaDataStore.edit {
        it[Keys.enabledDays] = days.sortedBy(DayOfWeek::getValue).joinToString(",") { day -> day.value.toString() }
    }

    suspend fun setSelectedCalendarIds(ids: Set<Long>) = context.vovremyaDataStore.edit {
        it[Keys.calendarIds] = ids.sorted().joinToString(",")
    }

    suspend fun setAutomaticUpdates(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.automaticUpdates] = enabled
    }

    suspend fun setThemeMode(value: ThemeMode) = context.vovremyaDataStore.edit {
        it[Keys.themeMode] = value.name
    }

    suspend fun setAccentTheme(value: AccentTheme) = context.vovremyaDataStore.edit {
        it[Keys.accentTheme] = value.name
    }

    suspend fun setCustomAccentColor(@androidx.annotation.ColorInt value: Int) = context.vovremyaDataStore.edit {
        it[Keys.customAccentColor] = value or 0xFF000000.toInt()
        it[Keys.accentTheme] = AccentTheme.CUSTOM.name
    }

    suspend fun setBackgroundStyle(value: BackgroundStyle) = context.vovremyaDataStore.edit {
        it[Keys.backgroundStyle] = value.name
    }

    suspend fun saveScheduledAlarms(alarms: List<ScheduledAlarm>) = context.vovremyaDataStore.edit {
        it[Keys.scheduledAlarms] = encodeAlarms(alarms)
        it[Keys.lastSyncMillis] = System.currentTimeMillis()
    }

    private fun settingsFrom(preferences: Preferences): AppSettings {
        val days = preferences[Keys.enabledDays]
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull()?.let(DayOfWeek::of) }
            ?.toSet()
            ?: DayOfWeek.entries.toSet()
        val calendars = preferences[Keys.calendarIds]
            ?.split(',')
            ?.mapNotNull(String::toLongOrNull)
            ?.toSet()
            ?: emptySet()
        return AppSettings(
            leadMinutes = preferences[Keys.leadMinutes] ?: 90,
            latestEventMinutes = preferences[Keys.latestEventMinutes] ?: (23 * 60 + 59),
            latestEventEnabled = preferences[Keys.latestEventEnabled]
                ?: ((preferences[Keys.latestEventMinutes] ?: (23 * 60 + 59)) < 23 * 60 + 59),
            dailySyncMinutes = preferences[Keys.dailySyncMinutes] ?: 19 * 60,
            lookAheadDays = preferences[Keys.lookAheadDays] ?: 21,
            allEventsPerDay = preferences[Keys.allEventsPerDay] ?: true,
            includeAllDayEvents = preferences[Keys.includeAllDayEvents] ?: false,
            allDayEventMinutes = preferences[Keys.allDayEventMinutes] ?: 9 * 60,
            alarmSoundEnabled = preferences[Keys.alarmSoundEnabled] ?: true,
            alarmVibrationEnabled = preferences[Keys.alarmVibrationEnabled] ?: true,
            alarmSoundUri = preferences[Keys.alarmSoundUri].orEmpty(),
            snoozeMinutes = preferences[Keys.snoozeMinutes] ?: 10,
            autoSilenceMinutes = preferences[Keys.autoSilenceMinutes] ?: 10,
            enabledDays = days,
            selectedCalendarIds = calendars,
            automaticUpdates = preferences[Keys.automaticUpdates] ?: true,
            themeMode = preferences[Keys.themeMode]
                ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: ThemeMode.SYSTEM,
            accentTheme = preferences[Keys.accentTheme]
                ?.let { stored -> AccentTheme.entries.firstOrNull { it.name == stored } }
                ?: AccentTheme.VIOLET,
            customAccentColor = preferences[Keys.customAccentColor] ?: 0xFF6558D3.toInt(),
            backgroundStyle = preferences[Keys.backgroundStyle]
                ?.let { stored -> BackgroundStyle.entries.firstOrNull { it.name == stored } }
                ?: BackgroundStyle.STANDARD,
        )
    }

    private fun encodeAlarms(alarms: List<ScheduledAlarm>): String = JSONArray().apply {
        alarms.forEach { alarm ->
            put(JSONObject().apply {
                put("key", alarm.key)
                put("eventId", alarm.eventId)
                put("eventStart", alarm.eventStartMillis)
                put("alarmAt", alarm.alarmAtMillis)
                put("title", alarm.title)
                put("location", alarm.location)
                put("calendarId", alarm.calendarId)
                put("calendarName", alarm.calendarName)
                put("calendarColor", alarm.calendarColor)
                put("allDay", alarm.allDay)
                put("soundEnabled", alarm.soundEnabled)
                put("vibrationEnabled", alarm.vibrationEnabled)
                put("soundUri", alarm.soundUri)
                put("snoozeMinutes", alarm.snoozeMinutes)
                put("autoSilenceMinutes", alarm.autoSilenceMinutes)
            })
        }
    }.toString()

    private fun decodeAlarms(raw: String?): List<ScheduledAlarm> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyList()
        val json = JSONArray(raw)
        buildList {
            repeat(json.length()) { index ->
                val item = json.getJSONObject(index)
                add(
                    ScheduledAlarm(
                        key = item.getString("key"),
                        eventId = item.getLong("eventId"),
                        eventStartMillis = item.getLong("eventStart"),
                        alarmAtMillis = item.getLong("alarmAt"),
                        title = item.getString("title"),
                        location = item.optString("location").takeIf { it.isNotBlank() && it != "null" },
                        calendarId = item.getLong("calendarId"),
                        calendarName = item.getString("calendarName"),
                        calendarColor = item.getInt("calendarColor"),
                        allDay = item.optBoolean("allDay", false),
                        soundEnabled = item.optBoolean("soundEnabled", true),
                        vibrationEnabled = item.optBoolean("vibrationEnabled", true),
                        soundUri = item.optString("soundUri"),
                        snoozeMinutes = item.optInt("snoozeMinutes", 10).coerceIn(1, 120),
                        autoSilenceMinutes = item.optInt("autoSilenceMinutes", 10).coerceIn(1, 60),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}
