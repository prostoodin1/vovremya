package com.vovremya.alarm.localization

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun `uses all six supported phone languages`() {
        val expectedNames = mapOf(
            "ru" to "Вовремя",
            "en" to "On Time",
            "de" to "Pünktlich",
            "fr" to "À l’heure",
            "es" to "A tiempo",
            "uk" to "Вчасно",
        )

        expectedNames.forEach { (language, expected) ->
            Locale.setDefault(Locale.forLanguageTag(language))
            assertEquals(expected, tr("Вовремя"))
        }
    }

    @Test
    fun `falls back to English for unsupported phone language`() {
        Locale.setDefault(Locale.forLanguageTag("it"))

        assertEquals("Settings", tr("Настройки"))
    }
}
