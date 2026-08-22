package com.vovremya.alarm.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.vovremyaDataStore by preferencesDataStore(name = "vovremya_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val advancedMode = booleanPreferencesKey("advanced_mode")
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
        val reminderVibrationEnabled = booleanPreferencesKey("reminder_vibration_enabled")
        val alarmHighBrightness = booleanPreferencesKey("alarm_high_brightness")
        val alarmTorchEnabled = booleanPreferencesKey("alarm_torch_enabled")
        val alarmTorchMode = stringPreferencesKey("alarm_torch_mode")
        val alarmTorchBlinkMillis = intPreferencesKey("alarm_torch_blink_millis")
        val alarmTorchRepeatCount = intPreferencesKey("alarm_torch_repeat_count")
        val alarmVibrationIntensity = intPreferencesKey("alarm_vibration_intensity")
        val reminderHighBrightness = booleanPreferencesKey("reminder_high_brightness")
        val reminderTorchEnabled = booleanPreferencesKey("reminder_torch_enabled")
        val reminderTorchMode = stringPreferencesKey("reminder_torch_mode")
        val reminderTorchBlinkMillis = intPreferencesKey("reminder_torch_blink_millis")
        val reminderTorchRepeatCount = intPreferencesKey("reminder_torch_repeat_count")
        val reminderVibrationIntensity = intPreferencesKey("reminder_vibration_intensity")
        val quickDismissEnabled = booleanPreferencesKey("quick_dismiss_enabled")
        val quickDismissAfterMinutes = intPreferencesKey("quick_dismiss_after_minutes")
        val quickDismissMode = stringPreferencesKey("quick_dismiss_mode")
        val alarmSoundUri = stringPreferencesKey("alarm_sound_uri")
        val snoozeMinutes = intPreferencesKey("snooze_minutes")
        val autoSilenceMinutes = intPreferencesKey("auto_silence_minutes")
        val reminderAutoDismissEnabled = booleanPreferencesKey("reminder_auto_dismiss_enabled")
        val reminderAutoDismissMinutes = intPreferencesKey("reminder_auto_dismiss_minutes")
        val enabledDays = stringPreferencesKey("enabled_days")
        val calendarIds = stringPreferencesKey("calendar_ids")
        val automaticUpdates = booleanPreferencesKey("automatic_updates")
        val updateChannel = stringPreferencesKey("update_channel")
        val appLanguageTag = stringPreferencesKey("app_language_tag")
        val navigationStyle = stringPreferencesKey("navigation_style")
        val bottomBarHideSeconds = intPreferencesKey("bottom_bar_hide_seconds")
        val reduceAnimations = booleanPreferencesKey("reduce_animations")
        val importantEventTitles = stringPreferencesKey("important_event_titles")
        val importantCalendarIds = stringPreferencesKey("important_calendar_ids")
        val showImportantTab = booleanPreferencesKey("show_important_tab")
        val includeUnselectedCalendarsAsSilent = booleanPreferencesKey("include_unselected_calendars_silent")
        val showAllEventsTab = booleanPreferencesKey("show_all_events_tab")
        val eventAlarmRules = stringPreferencesKey("event_alarm_rules")
        val calendarLeadMinutes = stringPreferencesKey("calendar_lead_minutes")
        val defaultEventRuleScope = stringPreferencesKey("default_event_rule_scope")
        val fullSwipeEnabled = booleanPreferencesKey("full_swipe_enabled")
        val swipeDirection = stringPreferencesKey("swipe_direction")
        val leftSwipeAction = stringPreferencesKey("left_swipe_action")
        val rightSwipeAction = stringPreferencesKey("right_swipe_action")
        val swipePreviewEnabled = booleanPreferencesKey("swipe_preview_enabled")
        val launcherIcon = stringPreferencesKey("launcher_icon")
        val skippedEventKeys = stringPreferencesKey("skipped_event_keys")
        val skippedDates = stringPreferencesKey("skipped_dates")
        val skippedAlarms = stringPreferencesKey("skipped_alarms")
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

    val skippedAlarms: Flow<List<ScheduledAlarm>> = context.vovremyaDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { decodeAlarms(it[Keys.skippedAlarms]) }

    val lastSyncMillis: Flow<Long?> = context.vovremyaDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[Keys.lastSyncMillis] }

    suspend fun setAdvancedMode(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.advancedMode] = enabled
    }

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

    suspend fun setReminderVibrationEnabled(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.reminderVibrationEnabled] = enabled
    }

    suspend fun setAlarmEffects(value: SignalEffects) = context.vovremyaDataStore.edit {
        writeEffects(it, value, alarm = true)
    }

    suspend fun setReminderEffects(value: SignalEffects) = context.vovremyaDataStore.edit {
        writeEffects(it, value, alarm = false)
    }

    suspend fun setQuickDismiss(value: QuickDismissSettings) = context.vovremyaDataStore.edit {
        it[Keys.quickDismissEnabled] = value.enabled
        it[Keys.quickDismissAfterMinutes] = value.afterMinutes.coerceIn(0, 23 * 60 + 59)
        it[Keys.quickDismissMode] = value.mode.name
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

    suspend fun setReminderAutoDismissEnabled(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.reminderAutoDismissEnabled] = enabled
    }

    suspend fun setReminderAutoDismissMinutes(value: Int) = context.vovremyaDataStore.edit {
        it[Keys.reminderAutoDismissMinutes] = value.coerceIn(1, 60)
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

    suspend fun setUpdateChannel(value: UpdateChannel) = context.vovremyaDataStore.edit {
        it[Keys.updateChannel] = value.name
    }

    suspend fun setAppLanguageTag(value: String) = context.vovremyaDataStore.edit {
        it[Keys.appLanguageTag] = value
    }

    suspend fun setNavigationStyle(value: NavigationStyle) = context.vovremyaDataStore.edit {
        it[Keys.navigationStyle] = value.name
    }

    suspend fun setBottomBarHideSeconds(value: Int) = context.vovremyaDataStore.edit {
        it[Keys.bottomBarHideSeconds] = value.coerceIn(0, 30)
    }

    suspend fun setReduceAnimations(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.reduceAnimations] = enabled
    }

    suspend fun setImportantEventTitles(values: Set<String>) = context.vovremyaDataStore.edit {
        it[Keys.importantEventTitles] = encodeStringSet(values.map(String::trim).filter(String::isNotBlank).toSet())
    }

    suspend fun setImportantCalendarIds(values: Set<Long>) = context.vovremyaDataStore.edit {
        it[Keys.importantCalendarIds] = values.sorted().joinToString(",")
    }

    suspend fun setShowImportantTab(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.showImportantTab] = enabled
    }

    suspend fun setIncludeUnselectedCalendarsAsSilent(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.includeUnselectedCalendarsAsSilent] = enabled
    }

    suspend fun setShowAllEventsTab(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.showAllEventsTab] = enabled
    }

    suspend fun setEventAlarmRule(rule: EventAlarmRule) = context.vovremyaDataStore.edit { preferences ->
        val normalized = rule.copy(
            match = rule.match.trim(),
            title = rule.title.trim(),
            leadMinutes = rule.leadMinutes.coerceIn(0, 14 * 24 * 60),
            effects = rule.effects.copy(
                torchBlinkMillis = rule.effects.torchBlinkMillis.coerceIn(100, 2_000),
                torchRepeatCount = rule.effects.torchRepeatCount.coerceIn(1, 100),
                vibrationIntensity = rule.effects.vibrationIntensity.coerceIn(1, 100),
            ),
        )
        val rules = decodeEventRules(preferences[Keys.eventAlarmRules])
            .filterNot { it.scope == normalized.scope && it.match == normalized.match } + normalized
        preferences[Keys.eventAlarmRules] = encodeEventRules(rules)
    }

    suspend fun removeEventAlarmRule(scope: EventRuleScope, match: String) = context.vovremyaDataStore.edit { preferences ->
        preferences[Keys.eventAlarmRules] = encodeEventRules(
            decodeEventRules(preferences[Keys.eventAlarmRules])
                .filterNot { it.scope == scope && it.match == match },
        )
    }

    suspend fun setCalendarLeadMinutes(calendarId: Long, minutes: Int?) = context.vovremyaDataStore.edit { preferences ->
        val current = decodeCalendarLeadMinutes(preferences[Keys.calendarLeadMinutes]).toMutableMap()
        if (minutes == null) current.remove(calendarId)
        else current[calendarId] = minutes.coerceIn(0, 14 * 24 * 60)
        preferences[Keys.calendarLeadMinutes] = encodeCalendarLeadMinutes(current)
    }

    suspend fun setDefaultEventRuleScope(scope: EventRuleScope) = context.vovremyaDataStore.edit {
        it[Keys.defaultEventRuleScope] = scope.name
    }

    suspend fun setFullSwipeEnabled(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.fullSwipeEnabled] = enabled
    }

    suspend fun setSwipeDirection(direction: SwipeDirection) = context.vovremyaDataStore.edit {
        it[Keys.swipeDirection] = direction.name
    }

    suspend fun setLeftSwipeAction(action: SwipeAction) = context.vovremyaDataStore.edit {
        it[Keys.leftSwipeAction] = action.name
    }

    suspend fun setRightSwipeAction(action: SwipeAction) = context.vovremyaDataStore.edit {
        it[Keys.rightSwipeAction] = action.name
    }

    suspend fun setSwipePreviewEnabled(enabled: Boolean) = context.vovremyaDataStore.edit {
        it[Keys.swipePreviewEnabled] = enabled
    }

    suspend fun setLauncherIcon(icon: LauncherIcon) = context.vovremyaDataStore.edit {
        it[Keys.launcherIcon] = icon.name
    }

    suspend fun skipAlarm(alarm: ScheduledAlarm) = context.vovremyaDataStore.edit { preferences ->
        preferences[Keys.skippedEventKeys] = encodeStringSet(
            decodeStringSet(preferences[Keys.skippedEventKeys]) + alarm.key,
        )
        val skipped = decodeAlarms(preferences[Keys.skippedAlarms])
            .filterNot { it.key == alarm.key } + alarm
        preferences[Keys.skippedAlarms] = encodeAlarms(skipped.sortedBy(ScheduledAlarm::eventStartMillis))
        preferences[Keys.scheduledAlarms] = encodeAlarms(
            decodeAlarms(preferences[Keys.scheduledAlarms]).filterNot { it.key == alarm.key },
        )
    }

    suspend fun restoreAlarm(alarm: ScheduledAlarm) = context.vovremyaDataStore.edit { preferences ->
        preferences[Keys.skippedEventKeys] = encodeStringSet(
            decodeStringSet(preferences[Keys.skippedEventKeys]) - alarm.key,
        )
        preferences[Keys.skippedAlarms] = encodeAlarms(
            decodeAlarms(preferences[Keys.skippedAlarms]).filterNot { it.key == alarm.key },
        )
        val eventDate = alarm.eventDate()
        preferences[Keys.skippedDates] = encodeDates(
            decodeDates(preferences[Keys.skippedDates]) - eventDate,
        )
    }

    suspend fun skipDate(date: LocalDate, alarms: List<ScheduledAlarm>) = context.vovremyaDataStore.edit { preferences ->
        preferences[Keys.skippedDates] = encodeDates(
            decodeDates(preferences[Keys.skippedDates]) + date,
        )
        val skipped = (decodeAlarms(preferences[Keys.skippedAlarms]) + alarms)
            .distinctBy(ScheduledAlarm::key)
            .sortedBy(ScheduledAlarm::eventStartMillis)
        preferences[Keys.skippedAlarms] = encodeAlarms(skipped)
        val keys = alarms.mapTo(mutableSetOf(), ScheduledAlarm::key)
        preferences[Keys.scheduledAlarms] = encodeAlarms(
            decodeAlarms(preferences[Keys.scheduledAlarms]).filterNot { it.key in keys },
        )
    }

    suspend fun restoreDate(date: LocalDate) = context.vovremyaDataStore.edit { preferences ->
        val restored = decodeAlarms(preferences[Keys.skippedAlarms]).filter { it.eventDate() == date }
        preferences[Keys.skippedDates] = encodeDates(
            decodeDates(preferences[Keys.skippedDates]) - date,
        )
        preferences[Keys.skippedEventKeys] = encodeStringSet(
            decodeStringSet(preferences[Keys.skippedEventKeys]) - restored.map(ScheduledAlarm::key).toSet(),
        )
        preferences[Keys.skippedAlarms] = encodeAlarms(
            decodeAlarms(preferences[Keys.skippedAlarms]).filterNot { it.eventDate() == date },
        )
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

    private fun writeEffects(preferences: MutablePreferences, value: SignalEffects, alarm: Boolean) {
        val intensity = value.vibrationIntensity.coerceIn(1, 100)
        val blinkMillis = value.torchBlinkMillis.coerceIn(100, 2_000)
        val repeatCount = value.torchRepeatCount.coerceIn(1, 100)
        if (alarm) {
            preferences[Keys.alarmHighBrightness] = value.highBrightnessEnabled
            preferences[Keys.alarmTorchEnabled] = value.torchEnabled
            preferences[Keys.alarmTorchMode] = value.torchMode.name
            preferences[Keys.alarmTorchBlinkMillis] = blinkMillis
            preferences[Keys.alarmTorchRepeatCount] = repeatCount
            preferences[Keys.alarmVibrationIntensity] = intensity
        } else {
            preferences[Keys.reminderHighBrightness] = value.highBrightnessEnabled
            preferences[Keys.reminderTorchEnabled] = value.torchEnabled
            preferences[Keys.reminderTorchMode] = value.torchMode.name
            preferences[Keys.reminderTorchBlinkMillis] = blinkMillis
            preferences[Keys.reminderTorchRepeatCount] = repeatCount
            preferences[Keys.reminderVibrationIntensity] = intensity
        }
    }

    private fun readEffects(preferences: Preferences, alarm: Boolean): SignalEffects = SignalEffects(
        highBrightnessEnabled = preferences[
            if (alarm) Keys.alarmHighBrightness else Keys.reminderHighBrightness
        ] ?: false,
        torchEnabled = preferences[
            if (alarm) Keys.alarmTorchEnabled else Keys.reminderTorchEnabled
        ] ?: false,
        torchMode = preferences[
            if (alarm) Keys.alarmTorchMode else Keys.reminderTorchMode
        ]?.let { stored -> TorchMode.entries.firstOrNull { it.name == stored } } ?: TorchMode.BLINK,
        torchBlinkMillis = (preferences[
            if (alarm) Keys.alarmTorchBlinkMillis else Keys.reminderTorchBlinkMillis
        ] ?: 500).coerceIn(100, 2_000),
        torchRepeatCount = (preferences[
            if (alarm) Keys.alarmTorchRepeatCount else Keys.reminderTorchRepeatCount
        ] ?: 10).coerceIn(1, 100),
        vibrationIntensity = (preferences[
            if (alarm) Keys.alarmVibrationIntensity else Keys.reminderVibrationIntensity
        ] ?: if (alarm) 100 else 60).coerceIn(1, 100),
    )

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
            advancedMode = preferences[Keys.advancedMode] ?: false,
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
            reminderVibrationEnabled = preferences[Keys.reminderVibrationEnabled] ?: false,
            alarmEffects = readEffects(preferences, alarm = true),
            reminderEffects = readEffects(preferences, alarm = false),
            quickDismiss = QuickDismissSettings(
                enabled = preferences[Keys.quickDismissEnabled] ?: true,
                afterMinutes = (preferences[Keys.quickDismissAfterMinutes] ?: 10 * 60)
                    .coerceIn(0, 23 * 60 + 59),
                mode = preferences[Keys.quickDismissMode]
                    ?.let { stored -> QuickDismissMode.entries.firstOrNull { it.name == stored } }
                    ?: QuickDismissMode.BUTTON,
            ),
            alarmSoundUri = preferences[Keys.alarmSoundUri].orEmpty(),
            snoozeMinutes = preferences[Keys.snoozeMinutes] ?: 10,
            autoSilenceMinutes = preferences[Keys.autoSilenceMinutes] ?: 10,
            reminderAutoDismissEnabled = preferences[Keys.reminderAutoDismissEnabled] ?: false,
            reminderAutoDismissMinutes = (preferences[Keys.reminderAutoDismissMinutes] ?: 5).coerceIn(1, 60),
            enabledDays = days,
            selectedCalendarIds = calendars,
            automaticUpdates = preferences[Keys.automaticUpdates] ?: true,
            updateChannel = preferences[Keys.updateChannel]
                ?.let { stored -> UpdateChannel.entries.firstOrNull { it.name == stored } }
                ?: UpdateChannel.STABLE,
            appLanguageTag = preferences[Keys.appLanguageTag].orEmpty(),
            navigationStyle = preferences[Keys.navigationStyle]
                ?.let { stored -> NavigationStyle.entries.firstOrNull { it.name == stored } }
                ?: NavigationStyle.CLASSIC,
            bottomBarHideSeconds = (preferences[Keys.bottomBarHideSeconds] ?: 5).coerceIn(0, 30),
            reduceAnimations = preferences[Keys.reduceAnimations] ?: false,
            importantEventTitles = decodeStringSet(preferences[Keys.importantEventTitles]),
            importantCalendarIds = decodeLongSet(preferences[Keys.importantCalendarIds]),
            showImportantTab = preferences[Keys.showImportantTab] ?: false,
            includeUnselectedCalendarsAsSilent = preferences[Keys.includeUnselectedCalendarsAsSilent] ?: false,
            showAllEventsTab = preferences[Keys.showAllEventsTab] ?: false,
            eventAlarmRules = decodeEventRules(preferences[Keys.eventAlarmRules]),
            calendarLeadMinutes = decodeCalendarLeadMinutes(preferences[Keys.calendarLeadMinutes]),
            defaultEventRuleScope = preferences[Keys.defaultEventRuleScope]
                ?.let { stored -> EventRuleScope.entries.firstOrNull { it.name == stored } }
                ?: EventRuleScope.SAME_TITLE,
            fullSwipeEnabled = preferences[Keys.fullSwipeEnabled] ?: false,
            swipeDirection = preferences[Keys.swipeDirection]
                ?.let { stored -> SwipeDirection.entries.firstOrNull { it.name == stored } }
                ?: SwipeDirection.BOTH,
            leftSwipeAction = preferences[Keys.leftSwipeAction]
                ?.let { stored -> SwipeAction.entries.firstOrNull { it.name == stored } }
                ?: SwipeAction.SILENT,
            rightSwipeAction = preferences[Keys.rightSwipeAction]
                ?.let { stored -> SwipeAction.entries.firstOrNull { it.name == stored } }
                ?: SwipeAction.SKIP,
            swipePreviewEnabled = preferences[Keys.swipePreviewEnabled] ?: false,
            launcherIcon = preferences[Keys.launcherIcon]
                ?.let { stored -> LauncherIcon.entries.firstOrNull { it.name == stored } }
                ?: LauncherIcon.CLASSIC,
            skippedEventKeys = decodeStringSet(preferences[Keys.skippedEventKeys]),
            skippedDates = decodeDates(preferences[Keys.skippedDates]),
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
                put("delivery", alarm.delivery.name)
                put("soundEnabled", alarm.soundEnabled)
                put("vibrationEnabled", alarm.vibrationEnabled)
                put("highBrightness", alarm.effects.highBrightnessEnabled)
                put("torchEnabled", alarm.effects.torchEnabled)
                put("torchMode", alarm.effects.torchMode.name)
                put("torchBlinkMillis", alarm.effects.torchBlinkMillis)
                put("torchRepeatCount", alarm.effects.torchRepeatCount)
                put("vibrationIntensity", alarm.effects.vibrationIntensity)
                put("quickDismissEnabled", alarm.quickDismiss.enabled)
                put("quickDismissAfterMinutes", alarm.quickDismiss.afterMinutes)
                put("quickDismissMode", alarm.quickDismiss.mode.name)
                put("soundUri", alarm.soundUri)
                put("snoozeMinutes", alarm.snoozeMinutes)
                put("autoSilenceMinutes", alarm.autoSilenceMinutes)
                put("reminderAutoDismissEnabled", alarm.reminderAutoDismissEnabled)
                put("reminderAutoDismissMinutes", alarm.reminderAutoDismissMinutes)
                put("important", alarm.isImportant)
                put("unselectedCalendar", alarm.fromUnselectedCalendar)
                put("customRule", alarm.customRuleApplied)
            })
        }
    }.toString()

    private fun decodeAlarms(raw: String?): List<ScheduledAlarm> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyList()
        val json = JSONArray(raw)
        val hasDelivery = (0 until json.length()).all { index ->
            json.getJSONObject(index).has("delivery")
        }
        val decoded = buildList {
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
                        delivery = item.optString("delivery")
                            .let { stored -> AlarmDelivery.entries.firstOrNull { it.name == stored } }
                            ?: AlarmDelivery.ALARM,
                        soundEnabled = item.optBoolean("soundEnabled", true),
                        vibrationEnabled = item.optBoolean("vibrationEnabled", true),
                        effects = SignalEffects(
                            highBrightnessEnabled = item.optBoolean("highBrightness", false),
                            torchEnabled = item.optBoolean("torchEnabled", false),
                            torchMode = item.optString("torchMode")
                                .let { stored -> TorchMode.entries.firstOrNull { it.name == stored } }
                                ?: TorchMode.BLINK,
                            torchBlinkMillis = item.optInt("torchBlinkMillis", 500).coerceIn(100, 2_000),
                            torchRepeatCount = item.optInt("torchRepeatCount", 10).coerceIn(1, 100),
                            vibrationIntensity = item.optInt("vibrationIntensity", 100).coerceIn(1, 100),
                        ),
                        quickDismiss = QuickDismissSettings(
                            enabled = item.optBoolean("quickDismissEnabled", true),
                            afterMinutes = item.optInt("quickDismissAfterMinutes", 10 * 60)
                                .coerceIn(0, 23 * 60 + 59),
                            mode = item.optString("quickDismissMode")
                                .let { stored -> QuickDismissMode.entries.firstOrNull { it.name == stored } }
                                ?: QuickDismissMode.BUTTON,
                        ),
                        soundUri = item.optString("soundUri"),
                        snoozeMinutes = item.optInt("snoozeMinutes", 10).coerceIn(1, 120),
                        autoSilenceMinutes = item.optInt("autoSilenceMinutes", 10).coerceIn(1, 60),
                        reminderAutoDismissEnabled = item.optBoolean("reminderAutoDismissEnabled", false),
                        reminderAutoDismissMinutes = item.optInt("reminderAutoDismissMinutes", 5).coerceIn(1, 60),
                        isImportant = item.optBoolean("important", false),
                        fromUnselectedCalendar = item.optBoolean("unselectedCalendar", false),
                        customRuleApplied = item.optBoolean("customRule", false),
                    ),
                )
            }
        }
        if (hasDelivery) decoded else migrateLegacyDeliveries(decoded)
    }.getOrDefault(emptyList())

    private fun migrateLegacyDeliveries(alarms: List<ScheduledAlarm>): List<ScheduledAlarm> = alarms
        .groupBy { it.eventDate() }
        .values
        .flatMap { dayAlarms ->
            dayAlarms.sortedBy(ScheduledAlarm::eventStartMillis).mapIndexed { index, alarm ->
                val delivery = if (index == 0) AlarmDelivery.ALARM else AlarmDelivery.SILENT_REMINDER
                alarm.copy(
                    delivery = delivery,
                    soundEnabled = delivery == AlarmDelivery.ALARM && alarm.soundEnabled,
                    vibrationEnabled = delivery == AlarmDelivery.ALARM && alarm.vibrationEnabled,
                )
            }
        }
        .sortedBy(ScheduledAlarm::alarmAtMillis)

    private fun encodeStringSet(values: Set<String>): String = JSONArray().apply {
        values.sorted().forEach(::put)
    }.toString()

    private fun decodeStringSet(raw: String?): Set<String> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptySet()
        val json = JSONArray(raw)
        buildSet { repeat(json.length()) { index -> add(json.getString(index)) } }
    }.getOrDefault(emptySet())

    private fun decodeLongSet(raw: String?): Set<Long> = raw
        ?.split(',')
        ?.mapNotNull(String::toLongOrNull)
        ?.toSet()
        ?: emptySet()

    private fun encodeEventRules(rules: List<EventAlarmRule>): String = JSONArray().apply {
        rules.sortedWith(compareBy(EventAlarmRule::scope, EventAlarmRule::match)).forEach { rule ->
            put(JSONObject().apply {
                put("match", rule.match)
                put("title", rule.title)
                put("scope", rule.scope.name)
                put("leadMinutes", rule.leadMinutes)
                put("delivery", rule.delivery.name)
                put("soundEnabled", rule.soundEnabled)
                put("vibrationEnabled", rule.vibrationEnabled)
                put("highBrightness", rule.effects.highBrightnessEnabled)
                put("torchEnabled", rule.effects.torchEnabled)
                put("torchMode", rule.effects.torchMode.name)
                put("torchBlinkMillis", rule.effects.torchBlinkMillis)
                put("torchRepeatCount", rule.effects.torchRepeatCount)
                put("vibrationIntensity", rule.effects.vibrationIntensity)
            })
        }
    }.toString()

    private fun decodeEventRules(raw: String?): List<EventAlarmRule> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyList()
        val json = JSONArray(raw)
        buildList {
            repeat(json.length()) { index ->
                val item = json.getJSONObject(index)
                val match = item.optString("match").trim()
                if (match.isBlank()) return@repeat
                add(
                    EventAlarmRule(
                        match = match,
                        title = item.optString("title").trim(),
                        scope = item.optString("scope")
                            .let { stored -> EventRuleScope.entries.firstOrNull { it.name == stored } }
                            ?: EventRuleScope.THIS_EVENT,
                        leadMinutes = item.optInt("leadMinutes", 90).coerceIn(0, 14 * 24 * 60),
                        delivery = item.optString("delivery")
                            .let { stored -> AlarmDelivery.entries.firstOrNull { it.name == stored } }
                            ?: AlarmDelivery.ALARM,
                        soundEnabled = item.optBoolean("soundEnabled", true),
                        vibrationEnabled = item.optBoolean("vibrationEnabled", true),
                        effects = SignalEffects(
                            highBrightnessEnabled = item.optBoolean("highBrightness", false),
                            torchEnabled = item.optBoolean("torchEnabled", false),
                            torchMode = item.optString("torchMode")
                                .let { stored -> TorchMode.entries.firstOrNull { it.name == stored } }
                                ?: TorchMode.BLINK,
                            torchBlinkMillis = item.optInt("torchBlinkMillis", 500).coerceIn(100, 2_000),
                            torchRepeatCount = item.optInt("torchRepeatCount", 10).coerceIn(1, 100),
                            vibrationIntensity = item.optInt("vibrationIntensity", 100).coerceIn(1, 100),
                        ),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeCalendarLeadMinutes(values: Map<Long, Int>): String = JSONObject().apply {
        values.toSortedMap().forEach { (calendarId, minutes) -> put(calendarId.toString(), minutes) }
    }.toString()

    private fun decodeCalendarLeadMinutes(raw: String?): Map<Long, Int> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyMap()
        val json = JSONObject(raw)
        buildMap {
            json.keys().forEach { key ->
                key.toLongOrNull()?.let { id -> put(id, json.optInt(key, 90).coerceIn(0, 14 * 24 * 60)) }
            }
        }
    }.getOrDefault(emptyMap())

    private fun encodeDates(values: Set<LocalDate>): String = values.sorted().joinToString(",")

    private fun decodeDates(raw: String?): Set<LocalDate> = raw
        ?.split(',')
        ?.mapNotNull { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
        ?.toSet()
        .orEmpty()

    private fun ScheduledAlarm.eventDate(): LocalDate = Instant.ofEpochMilli(eventStartMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}
