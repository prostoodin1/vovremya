package com.vovremya.alarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vovremya.alarm.BuildConfig
import com.vovremya.alarm.VovremyaApplication
import com.vovremya.alarm.data.AppSettings
import com.vovremya.alarm.data.AccentTheme
import com.vovremya.alarm.data.BackgroundStyle
import com.vovremya.alarm.data.CalendarInfo
import com.vovremya.alarm.data.CalendarSyncRepairResult
import com.vovremya.alarm.data.NavigationStyle
import com.vovremya.alarm.data.EventAlarmRule
import com.vovremya.alarm.data.EventRuleScope
import com.vovremya.alarm.data.AlarmDelivery
import com.vovremya.alarm.data.LauncherIcon
import com.vovremya.alarm.data.SwipeAction
import com.vovremya.alarm.data.SwipeDirection
import com.vovremya.alarm.data.ScheduledAlarm
import com.vovremya.alarm.data.SignalEffects
import com.vovremya.alarm.data.SyncDiagnostics
import com.vovremya.alarm.data.SyncResult
import com.vovremya.alarm.data.ThemeMode
import com.vovremya.alarm.data.UpdateChannel
import com.vovremya.alarm.data.QuickDismissSettings
import com.vovremya.alarm.localization.tr
import com.vovremya.alarm.update.AvailableRelease
import com.vovremya.alarm.update.UpdateCheckResult
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val alarms: List<ScheduledAlarm> = emptyList(),
    val skippedAlarms: List<ScheduledAlarm> = emptyList(),
    val calendars: List<CalendarInfo> = emptyList(),
    val lastSyncMillis: Long? = null,
    val syncing: Boolean = false,
    val message: String? = null,
    val lastError: String? = null,
    val diagnostics: SyncDiagnostics? = null,
    val githubConfigured: Boolean = BuildConfig.GITHUB_REPOSITORY.isNotBlank(),
    val availableReleases: List<AvailableRelease> = emptyList(),
    val releaseCatalogLoading: Boolean = false,
    val downloadingReleaseTag: String? = null,
    val downloadedReleaseTags: Set<String> = emptySet(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as VovremyaApplication).container
    private val calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    private val syncing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val lastError = MutableStateFlow<String?>(null)
    private val diagnostics = MutableStateFlow<SyncDiagnostics?>(null)
    private val availableReleases = MutableStateFlow<List<AvailableRelease>>(emptyList())
    private val releaseCatalogLoading = MutableStateFlow(false)
    private val downloadingReleaseTag = MutableStateFlow<String?>(null)
    private val downloadedReleaseTags = MutableStateFlow<Set<String>>(emptySet())
    private var calendarObserverJob: Job? = null
    private var initialRemoteSyncRequested = false

    val state = combine(
        container.settingsStore.settings,
        container.settingsStore.scheduledAlarms,
        container.settingsStore.lastSyncMillis,
        calendars,
        syncing,
    ) { settings, alarms, lastSync, availableCalendars, isSyncing ->
        MainUiState(
            settings = settings,
            alarms = alarms.filter { it.alarmAtMillis > System.currentTimeMillis() },
            calendars = availableCalendars,
            lastSyncMillis = lastSync,
            syncing = isSyncing,
        )
    }.combine(message) { state, currentMessage -> state.copy(message = currentMessage) }
        .combine(container.settingsStore.skippedAlarms) { state, skipped ->
            state.copy(
                skippedAlarms = skipped
                    .filter { it.eventStartMillis > System.currentTimeMillis() }
                    .sortedBy(ScheduledAlarm::eventStartMillis),
            )
        }
        .combine(lastError) { state, error -> state.copy(lastError = error) }
        .combine(diagnostics) { state, currentDiagnostics -> state.copy(diagnostics = currentDiagnostics) }
        .combine(availableReleases) { state, releases -> state.copy(availableReleases = releases) }
        .combine(releaseCatalogLoading) { state, loading -> state.copy(releaseCatalogLoading = loading) }
        .combine(downloadingReleaseTag) { state, tag -> state.copy(downloadingReleaseTag = tag) }
        .combine(downloadedReleaseTags) { state, tags -> state.copy(downloadedReleaseTags = tags) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun onCalendarPermissionAvailable() {
        if (calendarObserverJob?.isActive != true) {
            calendarObserverJob = viewModelScope.launch {
                runCatching {
                    container.calendarRepository.calendarChanges().collectLatest {
                        delay(750)
                        syncNow(showMessage = false)
                    }
                }
            }
        }
        val requestRemoteSync = !initialRemoteSyncRequested && container.calendarRepository.hasWritePermission()
        initialRemoteSyncRequested = initialRemoteSyncRequested || requestRemoteSync
        runSync(
            showMessage = false,
            requestRemoteSync = requestRemoteSync,
            scheduleFollowUp = requestRemoteSync,
        )
    }

    fun syncNow(showMessage: Boolean = true) {
        runSync(
            showMessage = showMessage,
            requestRemoteSync = showMessage,
            scheduleFollowUp = showMessage,
        )
    }

    private fun runSync(
        showMessage: Boolean,
        requestRemoteSync: Boolean,
        scheduleFollowUp: Boolean,
    ) {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            var remoteSyncRequested = false
            var repairResult = CalendarSyncRepairResult()
            runCatching {
                calendars.value = container.calendarRepository.getCalendars()
                if (requestRemoteSync) {
                    repairResult = container.calendarRepository.repairAndRequestCalendarSync(
                        calendars = calendars.value,
                        selectedCalendarIds = state.value.settings.selectedCalendarIds,
                    )
                    remoteSyncRequested = repairResult.requestedAccounts > 0
                    if (remoteSyncRequested) delay(6_000) else if (repairResult.updatedCalendars > 0) delay(400)
                    calendars.value = container.calendarRepository.getCalendars()
                }
                container.alarmScheduler.syncFromCalendar()
            }
                .onSuccess { result ->
                    diagnostics.value = result.diagnostics
                    lastError.value = null
                    if (showMessage) {
                        message.value = when {
                            repairResult.writePermissionMissing -> tr("Разрешите управление календарями для восстановления синхронизации")
                            repairResult.failedCalendars > 0 -> tr("Android не разрешил включить календарей: %d", repairResult.failedCalendars)
                            repairResult.updatedCalendars > 0 -> tr(
                                "Синхронизация восстановлена для календарей: %d. Найдено событий: %d",
                                repairResult.updatedCalendars,
                                result.diagnostics.usableTimedEvents,
                            )
                            else -> syncMessage(result)
                        }
                    }
                }
                .onFailure {
                    lastError.value = "${it::class.java.simpleName}: ${it.message.orEmpty().take(180)}"
                    if (showMessage) message.value = tr("Не удалось прочитать календарь")
                }
            syncing.value = false
            if (scheduleFollowUp && remoteSyncRequested) {
                delay(12_000)
                runSync(showMessage = false, requestRemoteSync = false, scheduleFollowUp = false)
            }
        }
    }

    fun setLeadMinutes(minutes: Int) = updateAndSync {
        container.settingsStore.setLeadMinutes(minutes)
    }

    fun setLatestEventMinutes(minutes: Int) = updateAndSync {
        container.settingsStore.setLatestEventMinutes(minutes)
    }

    fun setLatestEventEnabled(enabled: Boolean) = updateAndSync {
        container.settingsStore.setLatestEventEnabled(enabled)
    }

    fun setDailySyncMinutes(minutes: Int) {
        viewModelScope.launch {
            container.settingsStore.setDailySyncMinutes(minutes)
            container.dailySyncScheduler.scheduleNext(minutes)
            syncNow(showMessage = false)
        }
    }

    fun setLookAheadDays(days: Int) = updateAndSync {
        container.settingsStore.setLookAheadDays(days)
    }

    fun setAllEventsPerDay(enabled: Boolean) = updateAndSync {
        container.settingsStore.setAllEventsPerDay(enabled)
    }

    fun setIncludeAllDayEvents(enabled: Boolean) = updateAndSync {
        container.settingsStore.setIncludeAllDayEvents(enabled)
    }

    fun setAllDayEventMinutes(minutes: Int) = updateAndSync {
        container.settingsStore.setAllDayEventMinutes(minutes)
    }

    fun setAlarmSoundEnabled(enabled: Boolean) = updateAndSync {
        container.settingsStore.setAlarmSoundEnabled(enabled)
    }

    fun setAlarmVibrationEnabled(enabled: Boolean) = updateAndSync {
        container.settingsStore.setAlarmVibrationEnabled(enabled)
    }

    fun setReminderVibrationEnabled(enabled: Boolean) = updateAndSync {
        container.settingsStore.setReminderVibrationEnabled(enabled)
    }

    fun setAlarmEffects(value: SignalEffects) = updateAndSync {
        container.settingsStore.setAlarmEffects(value)
    }

    fun setReminderEffects(value: SignalEffects) = updateAndSync {
        container.settingsStore.setReminderEffects(value)
    }

    fun setQuickDismiss(value: QuickDismissSettings) = updateAndSync {
        container.settingsStore.setQuickDismiss(value)
    }

    fun setAlarmSoundUri(uri: String?) = updateAndSync {
        container.settingsStore.setAlarmSoundUri(uri)
    }

    fun setSnoozeMinutes(minutes: Int) = updateAndSync {
        container.settingsStore.setSnoozeMinutes(minutes)
    }

    fun setAutoSilenceMinutes(minutes: Int) = updateAndSync {
        container.settingsStore.setAutoSilenceMinutes(minutes)
    }

    fun setReminderAutoDismissEnabled(enabled: Boolean) = updateAndSync {
        container.settingsStore.setReminderAutoDismissEnabled(enabled)
    }

    fun setReminderAutoDismissMinutes(minutes: Int) = updateAndSync {
        container.settingsStore.setReminderAutoDismissMinutes(minutes)
    }

    fun toggleDay(day: DayOfWeek) = updateAndSync {
        val current = state.value.settings.enabledDays
        container.settingsStore.setEnabledDays(
            if (day in current) current - day else current + day,
        )
    }

    fun toggleCalendar(calendarId: Long) = updateAndSync {
        val available = state.value.calendars
        if (available.none { it.id == calendarId }) return@updateAndSync
        val allIds = available.map(CalendarInfo::id).toSet()
        val current = state.value.settings.selectedCalendarIds
        val explicit = if (current.isEmpty()) allIds else current.intersect(allIds)
        val changed = if (calendarId in explicit) explicit - calendarId else explicit + calendarId
        if (changed.isEmpty()) {
            message.value = tr("Оставьте хотя бы один календарь")
            return@updateAndSync
        }
        container.settingsStore.setSelectedCalendarIds(if (changed == allIds) emptySet() else changed)
    }

    fun selectAllCalendars() = updateAndSync {
        container.settingsStore.setSelectedCalendarIds(emptySet())
    }

    fun setAutomaticUpdates(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setAutomaticUpdates(enabled) }
    }

    fun setUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch { container.settingsStore.setUpdateChannel(channel) }
    }

    fun setAppLanguageTag(tag: String) {
        viewModelScope.launch { container.settingsStore.setAppLanguageTag(tag) }
    }

    fun syncAppLanguageTag(tag: String) {
        if (state.value.settings.appLanguageTag != tag) setAppLanguageTag(tag)
    }

    fun setAdvancedMode(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setAdvancedMode(enabled) }
    }

    fun setNavigationStyle(style: NavigationStyle) {
        viewModelScope.launch { container.settingsStore.setNavigationStyle(style) }
    }

    fun setBottomBarHideSeconds(seconds: Int) {
        viewModelScope.launch { container.settingsStore.setBottomBarHideSeconds(seconds) }
    }

    fun setReduceAnimations(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setReduceAnimations(enabled) }
    }

    fun toggleImportantEventTitle(title: String) = updateAndSync {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return@updateAndSync
        val current = state.value.settings.importantEventTitles
        val existing = current.firstOrNull { it.equals(cleanTitle, ignoreCase = true) }
        container.settingsStore.setImportantEventTitles(
            if (existing == null) current + cleanTitle else current - existing,
        )
    }

    fun toggleImportantCalendar(calendarId: Long) = updateAndSync {
        if (state.value.calendars.none { it.id == calendarId }) return@updateAndSync
        val current = state.value.settings.importantCalendarIds
        container.settingsStore.setImportantCalendarIds(
            if (calendarId in current) current - calendarId else current + calendarId,
        )
    }

    fun setShowImportantTab(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setShowImportantTab(enabled) }
    }

    fun setIncludeUnselectedCalendarsAsSilent(enabled: Boolean) = updateAndSync {
        container.settingsStore.setIncludeUnselectedCalendarsAsSilent(enabled)
        if (!enabled) container.settingsStore.setShowAllEventsTab(false)
    }

    fun setShowAllEventsTab(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setShowAllEventsTab(enabled) }
    }

    fun saveEventAlarmRule(
        alarm: ScheduledAlarm,
        scope: EventRuleScope,
        leadMinutes: Int,
        delivery: AlarmDelivery,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        effects: SignalEffects,
    ) = updateAndSync {
        container.settingsStore.setEventAlarmRule(
            EventAlarmRule(
                match = if (scope == EventRuleScope.THIS_EVENT) alarm.key else alarm.title.trim().lowercase(Locale.ROOT),
                title = alarm.title,
                scope = scope,
                leadMinutes = leadMinutes,
                delivery = delivery,
                soundEnabled = soundEnabled,
                vibrationEnabled = vibrationEnabled,
                effects = effects,
            ),
        )
        message.value = tr("Настройки события сохранены")
    }

    fun removeEventAlarmRule(alarm: ScheduledAlarm, scope: EventRuleScope) = updateAndSync {
        val match = if (scope == EventRuleScope.THIS_EVENT) alarm.key else alarm.title.trim().lowercase(Locale.ROOT)
        container.settingsStore.removeEventAlarmRule(scope, match)
        message.value = tr("Индивидуальные настройки удалены")
    }

    fun muteAlarm(alarm: ScheduledAlarm) = saveEventAlarmRule(
        alarm = alarm,
        scope = EventRuleScope.THIS_EVENT,
        leadMinutes = ((alarm.eventStartMillis - alarm.alarmAtMillis) / 60_000L).toInt().coerceAtLeast(0),
        delivery = AlarmDelivery.SILENT_REMINDER,
        soundEnabled = false,
        vibrationEnabled = state.value.settings.reminderVibrationEnabled,
        effects = state.value.settings.reminderEffects,
    )

    fun setCalendarLeadMinutes(calendarId: Long, minutes: Int?) = updateAndSync {
        container.settingsStore.setCalendarLeadMinutes(calendarId, minutes)
    }

    fun setDefaultEventRuleScope(scope: EventRuleScope) {
        viewModelScope.launch { container.settingsStore.setDefaultEventRuleScope(scope) }
    }

    fun setFullSwipeEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setFullSwipeEnabled(enabled) }
    }

    fun setSwipeDirection(direction: SwipeDirection) {
        viewModelScope.launch { container.settingsStore.setSwipeDirection(direction) }
    }

    fun setLeftSwipeAction(action: SwipeAction) {
        viewModelScope.launch { container.settingsStore.setLeftSwipeAction(action) }
    }

    fun setRightSwipeAction(action: SwipeAction) {
        viewModelScope.launch { container.settingsStore.setRightSwipeAction(action) }
    }

    fun setSwipePreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setSwipePreviewEnabled(enabled) }
    }

    fun setLauncherIcon(icon: LauncherIcon) {
        viewModelScope.launch {
            container.settingsStore.setLauncherIcon(icon)
            container.launcherIconManager.apply(icon)
        }
    }

    fun skipAlarm(alarm: ScheduledAlarm) {
        container.alarmScheduler.cancel(alarm)
        updateAndSync {
            container.settingsStore.skipAlarm(alarm)
            message.value = tr("Событие пропущено")
        }
    }

    fun restoreAlarm(alarm: ScheduledAlarm) = updateAndSync {
        container.settingsStore.restoreAlarm(alarm)
        message.value = tr("Событие возвращено")
    }

    fun skipAllToday() {
        val today = LocalDate.now()
        val alarms = state.value.alarms.filter { it.eventDate() == today }
        if (alarms.isEmpty()) return
        alarms.forEach(container.alarmScheduler::cancel)
        updateAndSync {
            container.settingsStore.skipDate(today, alarms)
            message.value = tr("Все события на сегодня пропущены")
        }
    }

    fun restoreAllToday() = updateAndSync {
        container.settingsStore.restoreDate(LocalDate.now())
        message.value = tr("Все события на сегодня возвращены")
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settingsStore.setThemeMode(mode) }
    }

    fun setAccentTheme(theme: AccentTheme) {
        viewModelScope.launch { container.settingsStore.setAccentTheme(theme) }
    }

    fun setCustomAccentColor(color: Int) {
        viewModelScope.launch { container.settingsStore.setCustomAccentColor(color) }
    }

    fun setBackgroundStyle(style: BackgroundStyle) {
        viewModelScope.launch { container.settingsStore.setBackgroundStyle(style) }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            message.value = when (
                val result = container.updateManager.checkAndDownloadUpdate(
                    channel = state.value.settings.updateChannel,
                )
            ) {
                UpdateCheckResult.NotConfigured -> "Сначала укажите GitHub-репозиторий в gradle.properties"
                UpdateCheckResult.UpToDate -> tr("Установлена последняя версия")
                UpdateCheckResult.NoApkAsset -> tr("В последнем релизе нет APK")
                UpdateCheckResult.NotDue -> tr("Установлена последняя версия")
                is UpdateCheckResult.Downloaded -> downloadMessage(result)
                is UpdateCheckResult.Failed -> tr("Обновление: %s", result.reason)
            }
        }
    }

    fun loadReleaseCatalog() {
        if (releaseCatalogLoading.value) return
        viewModelScope.launch {
            releaseCatalogLoading.value = true
            container.updateManager.loadReleaseCatalog()
                .onSuccess { releases ->
                    availableReleases.value = releases
                    downloadedReleaseTags.value = container.updateManager.downloadedReleaseTags(releases)
                    if (releases.isEmpty()) message.value = tr("В GitHub Releases пока нет APK")
                }
                .onFailure { error ->
                    message.value = tr("Не удалось загрузить список версий: %s", error.message.orEmpty())
                }
            releaseCatalogLoading.value = false
        }
    }

    fun downloadRelease(release: AvailableRelease) {
        if (downloadingReleaseTag.value != null) return
        viewModelScope.launch {
            downloadingReleaseTag.value = release.tag
            message.value = when (val result = container.updateManager.downloadRelease(release)) {
                is UpdateCheckResult.Downloaded -> {
                    downloadedReleaseTags.value += release.tag
                    downloadMessage(result)
                }
                is UpdateCheckResult.Failed -> tr("Загрузка версии %s: %s", release.version, result.reason)
                else -> tr("Не удалось загрузить версию %s", release.version)
            }
            downloadingReleaseTag.value = null
        }
    }

    fun installRelease(release: AvailableRelease) {
        viewModelScope.launch {
            message.value = when (val result = container.updateManager.installRelease(release)) {
                is UpdateCheckResult.Downloaded -> tr("Версия %s готова к установке", result.version)
                is UpdateCheckResult.Failed -> {
                    downloadedReleaseTags.value -= release.tag
                    tr("Установка версии %s: %s", release.version, result.reason)
                }
                else -> tr("Не удалось подготовить установку версии %s", release.version)
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }

    private fun updateAndSync(change: suspend () -> Unit) {
        viewModelScope.launch {
            change()
            syncNow(showMessage = false)
        }
    }

    private fun downloadMessage(result: UpdateCheckResult.Downloaded): String =
        if (result.isDowngrade) {
            tr("Старая версия %s загружена. Android не установит её поверх новой", result.version)
        } else {
            tr("Версия %s загружена", result.version)
        }

    private fun syncMessage(result: SyncResult): String {
        val d = result.diagnostics
        return when {
            d.readErrors.isNotEmpty() && result.alarms.isEmpty() ->
                tr("Календарь прочитан с ошибками — откройте скрытый журнал")
            result.alarms.isNotEmpty() && result.exact ->
                tr("Найдено событий: %d; будильников: %d", d.usableTimedEvents, result.alarms.size)
            result.alarms.isNotEmpty() ->
                tr("Будильников: %d; разрешите точное время", result.alarms.size)
            d.totalInstances == 0 && d.unsyncedCalendars > 0 ->
                tr("Событий не найдено. У %d календарей выключена синхронизация", d.unsyncedCalendars)
            d.totalInstances == 0 ->
                tr("В ближайшие %d дней календарь не вернул событий", d.lookAheadDays)
            d.excludedAllDay == d.totalInstances ->
                tr("Найдены только события на весь день — для них будильник не ставится")
            d.excludedCalendar > 0 ->
                tr("События найдены, но их календари отключены в настройках")
            d.excludedDay > 0 ->
                tr("События найдены, но эти дни недели отключены")
            d.excludedCutoff > 0 ->
                tr("События найдены, но начинаются позже заданной метки")
            d.excludedPastAlarm > 0 ->
                tr("События найдены, но время будильника для них уже прошло")
            d.excludedUserSkipped > 0 ->
                tr("События пропущены пользователем")
            else -> tr("Подходящих событий пока нет")
        }
    }

    private fun ScheduledAlarm.eventDate(): LocalDate = Instant.ofEpochMilli(eventStartMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}
