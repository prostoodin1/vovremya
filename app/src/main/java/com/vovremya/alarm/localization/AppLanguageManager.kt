package com.vovremya.alarm.localization

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

enum class AppLanguage(val tag: String, val nativeName: String) {
    SYSTEM("", ""),
    RUSSIAN("ru", "Русский"),
    ENGLISH("en", "English"),
    UKRAINIAN("uk", "Українська"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    SPANISH("es", "Español"),
    ITALIAN("it", "Italiano"),
    PORTUGUESE("pt", "Português"),
    POLISH("pl", "Polski"),
    DUTCH("nl", "Nederlands"),
    TURKISH("tr", "Türkçe"),
    CZECH("cs", "Čeština"),
    ROMANIAN("ro", "Română"),
    GREEK("el", "Ελληνικά"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    ;

    companion object {
        fun fromTag(tag: String): AppLanguage {
            val language = Locale.forLanguageTag(tag).language.lowercase(Locale.ROOT)
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}

object AppLanguageManager {
    private const val PREFERENCES = "app_language"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    fun wrapBaseContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = preferences(base).getString(KEY_LANGUAGE_TAG, "").orEmpty()
        val locale = localeFor(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }

    fun applyStoredLanguage(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val preferences = preferences(context)
            if (!preferences.contains(KEY_LANGUAGE_TAG)) return
            val tag = preferences.getString(KEY_LANGUAGE_TAG, "").orEmpty()
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.toLanguageTags() != tag) {
                context.getSystemService(LocaleManager::class.java).applicationLocales = localeList(tag)
            }
        } else {
            applyLegacyLocale(context, selectedTag(context))
        }
    }

    fun selectedTag(context: Context): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        if (locales.isEmpty) "" else locales[0].language
    } else {
        preferences(context).getString(KEY_LANGUAGE_TAG, "").orEmpty()
    }

    fun syncFromAndroid(context: Context): String {
        val tag = selectedTag(context)
        preferences(context).edit { putString(KEY_LANGUAGE_TAG, tag) }
        return tag
    }

    fun setLanguage(activity: Activity, language: AppLanguage) {
        preferences(activity).edit(commit = true) { putString(KEY_LANGUAGE_TAG, language.tag) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales = localeList(language.tag)
        } else {
            applyLegacyLocale(activity.applicationContext, language.tag)
            applyLegacyLocale(activity, language.tag)
            activity.recreate()
        }
    }

    private fun applyLegacyLocale(context: Context, tag: String) {
        val locale = localeFor(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }

    private fun localeFor(tag: String): Locale = if (tag.isBlank()) {
        Resources.getSystem().configuration.locales[0]
    } else {
        Locale.forLanguageTag(tag)
    }

    private fun localeList(tag: String): LocaleList = if (tag.isBlank()) {
        LocaleList.getEmptyLocaleList()
    } else {
        LocaleList.forLanguageTags(tag)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
