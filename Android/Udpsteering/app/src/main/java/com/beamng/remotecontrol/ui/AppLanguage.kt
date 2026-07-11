package com.beamng.remotecontrol.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Single source of truth for the languages the app ships.
 *
 * Adding a language = ONE entry here + a `values-<tag>/strings.xml` translation
 * + one `<locale>` line in `res/xml/locales_config.xml`. Nothing else changes:
 * the settings screen iterates [entries], selection/persistence is handled by
 * AppCompat's per-app locales (autoStoreLocales).
 *
 * [nativeName] is deliberately written in the language itself (never
 * translated) — that's how users find their own language in a foreign UI.
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    TURKISH("tr", "Türkçe"),
    GERMAN("de", "Deutsch");

    companion object {
        /** Effective language: explicit per-app choice, else system, else English. */
        fun current(): AppLanguage {
            val applied = AppCompatDelegate.getApplicationLocales()
            val tag = if (!applied.isEmpty) {
                applied[0]?.language
            } else {
                Locale.getDefault().language
            }
            return entries.firstOrNull { it.tag == tag } ?: ENGLISH
        }

        /** Applies and persists the choice; running activities recreate themselves. */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language.tag),
            )
        }
    }
}
