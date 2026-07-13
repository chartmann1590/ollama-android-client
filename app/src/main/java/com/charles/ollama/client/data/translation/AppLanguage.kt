package com.charles.ollama.client.data.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

data class AppLanguage(
    val tag: String,
    val displayName: String,
    val nativeName: String
)

object AppLanguages {
    val english = AppLanguage(
        tag = "en",
        displayName = "English",
        nativeName = "English"
    )

    val all: List<AppLanguage> = buildList {
        add(english)
        TranslateLanguage.getAllLanguages()
            .filterNot { it == english.tag }
            .map { tag ->
                val locale = Locale.forLanguageTag(tag)
                AppLanguage(
                    tag = tag,
                    displayName = locale.getDisplayName(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) },
                    nativeName = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
                )
            }
            .sortedBy { it.displayName }
            .forEach(::add)
    }

    fun find(tag: String): AppLanguage =
        all.firstOrNull { it.tag == tag } ?: english
}
