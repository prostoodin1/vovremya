package com.vovremya.alarm.localization

private val v17English = mapOf(
    "10 вариантов с разными формами и цветами; старая иконка выбрана по умолчанию" to "10 variants with different shapes and colors; the classic icon is selected by default",
    "Android может обновить значок на рабочем столе через несколько секунд." to "Android may take a few seconds to update the Home screen icon.",
    "Будильник" to "Alarm",
    "Вернуть общее время: %s" to "Restore default timing: %s",
    "Вибрировать при показе экрана" to "Vibrate when showing the screen",
    "Включать вспышку во время сигнала" to "Use the flashlight during the alert",
    "Время для календаря" to "Calendar timing",
    "Все события этого календаря будут напоминать за выбранное время, если у события нет собственного правила." to "All events in this calendar will use this timing unless an event has its own rule.",
    "Выполнять выбранное действие одним длинным свайпом" to "Run the selected action with one long swipe",
    "Высокая яркость" to "High brightness",
    "Действие при свайпе влево" to "Swipe left action",
    "Действие при свайпе вправо" to "Swipe right action",
    "Для этого режима уже сохранено индивидуальное правило" to "A custom rule is already saved for this mode",
    "За сколько минут до события" to "Minutes before the event",
    "Иконка приложения" to "App icon",
    "Индивидуальное время · %s заранее" to "Custom timing · %s early",
    "Индивидуальные настройки удалены" to "Custom settings removed",
    "Использовать мелодию будильника" to "Use the alarm sound",
    "Как по умолчанию сохранять настройку, открытую из карточки события" to "Default scope for settings opened from an event card",
    "Настроить" to "Configure",
    "Настроить время календаря" to "Configure calendar timing",
    "Настройка события" to "Event settings",
    "Настройки события сохранены" to "Event settings saved",
    "Подсветить экран во время сигнала" to "Brighten the screen during the alert",
    "Полное смахивание события" to "Full event swipe",
    "Правила событий" to "Event rules",
    "Применять правило" to "Apply rule",
    "Разрешённые направления" to "Allowed directions",
    "Сбросить правило" to "Reset rule",
    "Своё время: %s заранее" to "Custom timing: %s early",
    "Сила вибрации: %d%%" to "Vibration strength: %d%%",
    "Тип сигнала" to "Alert type",
)

private val v17Spanish = mapOf(
    "10 вариантов с разными формами и цветами; старая иконка выбрана по умолчанию" to "10 variantes con formas y colores distintos; el icono clásico está seleccionado por defecto",
    "Android может обновить значок на рабочем столе через несколько секунд." to "Android puede tardar unos segundos en actualizar el icono de la pantalla de inicio.",
    "Будильник" to "Alarma",
    "Вернуть общее время: %s" to "Restablecer el tiempo general: %s",
    "Вибрировать при показе экрана" to "Vibrar al mostrar la pantalla",
    "Включать вспышку во время сигнала" to "Usar el flash durante el aviso",
    "Время для календаря" to "Tiempo del calendario",
    "Все события этого календаря будут напоминать за выбранное время, если у события нет собственного правила." to "Todos los eventos de este calendario usarán este tiempo salvo que tengan una regla propia.",
    "Выполнять выбранное действие одним длинным свайпом" to "Ejecutar la acción elegida con un deslizamiento largo",
    "Высокая яркость" to "Brillo alto",
    "Действие при свайпе влево" to "Acción al deslizar a la izquierda",
    "Действие при свайпе вправо" to "Acción al deslizar a la derecha",
    "Для этого режима уже сохранено индивидуальное правило" to "Ya existe una regla personalizada para este modo",
    "За сколько минут до события" to "Minutos antes del evento",
    "Иконка приложения" to "Icono de la aplicación",
    "Индивидуальное время · %s заранее" to "Tiempo personalizado · %s antes",
    "Индивидуальные настройки удалены" to "Ajustes personalizados eliminados",
    "Использовать мелодию будильника" to "Usar el sonido de la alarma",
    "Как по умолчанию сохранять настройку, открытую из карточки события" to "Ámbito predeterminado para los ajustes abiertos desde un evento",
    "Настроить" to "Configurar",
    "Настроить время календаря" to "Configurar el tiempo del calendario",
    "Настройка события" to "Ajustes del evento",
    "Настройки события сохранены" to "Ajustes del evento guardados",
    "Подсветить экран во время сигнала" to "Iluminar la pantalla durante el aviso",
    "Полное смахивание события" to "Deslizamiento completo del evento",
    "Правила событий" to "Reglas de eventos",
    "Применять правило" to "Aplicar regla",
    "Разрешённые направления" to "Direcciones permitidas",
    "Сбросить правило" to "Restablecer regla",
    "Своё время: %s заранее" to "Tiempo propio: %s antes",
    "Сила вибрации: %d%%" to "Intensidad de vibración: %d%%",
    "Тип сигнала" to "Tipo de aviso",
)

private val v17Languages = setOf(
    "ru", "en", "de", "fr", "es", "uk", "it", "pt",
    "pl", "nl", "tr", "cs", "ro", "el", "ja", "ko",
)

/**
 * Beta 1.7 strings are complete for Russian, English and Spanish. Other supported
 * locales deliberately receive the English wording instead of leaking Russian UI.
 */
internal val v17Translations: Map<String, Map<String, String>> = v17Languages.associateWith { language ->
    when (language) {
        "ru" -> v17English.keys.associateWith { it }
        "es" -> v17English.mapValues { (source, english) -> v17Spanish[source] ?: english }
        else -> v17English
    }
}
