package com.vovremya.alarm.localization

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalizationTest {
    private lateinit var originalLocale: Locale

    @Before
    fun rememberLocale() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `uses all sixteen supported phone languages`() {
        val expectedNames = mapOf(
            "ru" to "Вовремя",
            "en" to "On Time",
            "de" to "Pünktlich",
            "fr" to "À l’heure",
            "es" to "A tiempo",
            "uk" to "Вчасно",
            "it" to "In tempo",
            "pt" to "Na hora certa",
            "pl" to "Na czas",
            "nl" to "Op tijd",
            "tr" to "Zamanında",
            "cs" to "Na čas",
            "ro" to "La Timp",
            "el" to "Στην Ώρα",
            "ja" to "オンタイム",
            "ko" to "정시에",
        )

        expectedNames.forEach { (language, expected) ->
            Locale.setDefault(Locale.forLanguageTag(language))
            assertEquals(expected, tr("Вовремя"))
        }
    }

    @Test
    fun `falls back to English for unsupported phone language`() {
        Locale.setDefault(Locale.forLanguageTag("ar"))

        assertEquals("Settings", tr("Настройки"))
    }

    @Test
    fun `additional language catalogs are complete and keep format arguments`() {
        assertEquals(10, additionalTranslations.size)
        assertTrue(additionalTranslations.values.all { it.size == 331 })
        assertTrue(additionalTranslations.values.all { catalog -> catalog.values.none(String::isBlank) })

        Locale.setDefault(Locale.ITALIAN)
        assertEquals("Versione 1.5.0", tr("Версия %s", "1.5.0"))
    }

    @Test
    fun `new appearance and diagnostics strings are translated`() {
        Locale.setDefault(Locale.ENGLISH)

        assertEquals("Custom color", tr("Свой цвет"))
        assertEquals("OLED black", tr("Чёрный OLED"))
        assertEquals("Events returned by Android", tr("Android передал события"))
        assertEquals("Repair and sync", tr("Исправить и синхронизировать"))
        assertEquals("Allow calendar management", tr("Разрешить управление календарями"))
        assertEquals(
            "Found: 3 · matching: 2 · Instances: 3 · Events: 1 · sync off: 1 · hidden: 1",
            tr(
                "Найдено: %d · подходящих: %d · Instances: %d · Events: %d · без синхронизации: %d · скрыто: %d",
                3,
                2,
                3,
                1,
                1,
                1,
            ),
        )
    }

    @Test
    fun `version 1_6 strings cover every supported language`() {
        assertEquals(16, v16Translations.size)
        assertTrue(v16Translations.values.all { it.size == 35 })
        assertTrue(v16Translations.values.all { catalog -> catalog.values.none(String::isBlank) })
        val formatArgument = Regex("%[ds]")
        v16Translations.values.forEach { catalog ->
            catalog.forEach { (source, translated) ->
                assertEquals(
                    formatArgument.findAll(source).map { it.value }.toList(),
                    formatArgument.findAll(translated).map { it.value }.toList(),
                )
            }
        }

        Locale.setDefault(Locale.ITALIAN)
        assertEquals("Installalo stasera", tr("Установить ночью"))
        Locale.setDefault(Locale.JAPANESE)
        assertEquals("重要な出来事", tr("Важные мероприятия"))
    }

    @Test
    fun `version 1_7 strings have safe text and matching format arguments`() {
        assertEquals(16, v17Translations.size)
        assertTrue(v17Translations.values.all { it.size == 32 })
        assertTrue(v17Translations.values.all { catalog -> catalog.values.none(String::isBlank) })
        val formatArgument = Regex("%[ds]")
        v17Translations.values.forEach { catalog ->
            catalog.forEach { (source, translated) ->
                assertEquals(
                    formatArgument.findAll(source).map { it.value }.toList(),
                    formatArgument.findAll(translated).map { it.value }.toList(),
                )
            }
        }

        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Event settings", tr("Настройка события"))
        Locale.setDefault(Locale.forLanguageTag("es"))
        assertEquals("Ajustes del evento", tr("Настройка события"))
    }

    @Test
    fun `version 1_8 strings have safe text and matching format arguments`() {
        assertEquals(16, v18Translations.size)
        assertTrue(v18Translations.values.all { it.size == 20 })
        assertTrue(v18Translations.values.all { catalog -> catalog.values.none(String::isBlank) })
        val formatArgument = Regex("%[ds]")
        v18Translations.values.forEach { catalog ->
            catalog.forEach { (source, translated) ->
                assertEquals(
                    formatArgument.findAll(source).map { it.value }.toList(),
                    formatArgument.findAll(translated).map { it.value }.toList(),
                )
            }
        }

        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Whole calendars", tr("Целые календари"))
        Locale.setDefault(Locale.forLanguageTag("es"))
        assertEquals("Toca para confirmar", tr("Нажмите, чтобы подтвердить"))
    }
}
