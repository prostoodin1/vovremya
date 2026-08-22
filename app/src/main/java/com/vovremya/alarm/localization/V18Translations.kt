package com.vovremya.alarm.localization

private val v18English = mapOf(
    "Автоматически закрывать напоминания" to "Automatically dismiss reminders",
    "Вибрация для остальных событий" to "Vibration for later events",
    "Выбрано календарей: %d" to "Calendars selected: %d",
    "Выбрано названий: %d" to "Titles selected: %d",
    "Жесты событий" to "Event gestures",
    "Закрывать экран и уведомление через %d мин" to "Dismiss the screen and notification after %d min",
    "Календарей: %d" to "Calendars: %d",
    "Календарь, уведомления и сигналы" to "Calendar, notifications and alerts",
    "Напоминание останется, пока вы его не закроете" to "The reminder stays until you dismiss it",
    "Настройки отдельных событий и календарей" to "Settings for individual events and calendars",
    "Нажмите, чтобы подтвердить" to "Tap to confirm",
    "По названию" to "By title",
    "Показать результат и дождаться подтверждения касанием или вторым свайпом" to "Preview the result and wait for a tap or second swipe to confirm",
    "После проверки здесь появятся календари" to "Calendars will appear here after a check",
    "Свайп, направление и подтверждение" to "Swipe, direction and confirmation",
    "Список календарей" to "Calendar list",
    "Только вибрация, без звука" to "Vibration only, without sound",
    "Фиксировать действие перед выполнением" to "Preview action before running it",
    "Целые календари" to "Whole calendars",
    "Через сколько закрывать" to "Dismiss after",
)

private val v18Spanish = mapOf(
    "Автоматически закрывать напоминания" to "Cerrar recordatorios automáticamente",
    "Вибрация для остальных событий" to "Vibración para eventos posteriores",
    "Выбрано календарей: %d" to "Calendarios seleccionados: %d",
    "Выбрано названий: %d" to "Títulos seleccionados: %d",
    "Жесты событий" to "Gestos de eventos",
    "Закрывать экран и уведомление через %d мин" to "Cerrar la pantalla y la notificación tras %d min",
    "Календарей: %d" to "Calendarios: %d",
    "Календарь, уведомления и сигналы" to "Calendario, notificaciones y avisos",
    "Напоминание останется, пока вы его не закроете" to "El recordatorio permanece hasta que lo cierres",
    "Настройки отдельных событий и календарей" to "Ajustes de eventos y calendarios individuales",
    "Нажмите, чтобы подтвердить" to "Toca para confirmar",
    "По названию" to "Por título",
    "Показать результат и дождаться подтверждения касанием или вторым свайпом" to "Mostrar el resultado y esperar un toque o segundo deslizamiento para confirmar",
    "После проверки здесь появятся календари" to "Los calendarios aparecerán aquí después de una comprobación",
    "Свайп, направление и подтверждение" to "Deslizamiento, dirección y confirmación",
    "Список календарей" to "Lista de calendarios",
    "Только вибрация, без звука" to "Solo vibración, sin sonido",
    "Фиксировать действие перед выполнением" to "Previsualizar la acción antes de ejecutarla",
    "Целые календари" to "Calendarios completos",
    "Через сколько закрывать" to "Cerrar después de",
)

private val v18Languages = setOf(
    "ru", "en", "de", "fr", "es", "uk", "it", "pt",
    "pl", "nl", "tr", "cs", "ro", "el", "ja", "ko",
)

internal val v18Translations: Map<String, Map<String, String>> = v18Languages.associateWith { language ->
    when (language) {
        "ru" -> v18English.keys.associateWith { it }
        "es" -> v18English.mapValues { (source, english) -> v18Spanish[source] ?: english }
        else -> v18English
    }
}
