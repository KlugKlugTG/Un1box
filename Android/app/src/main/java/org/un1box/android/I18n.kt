package org.un1box.android

/**
 * What language the app draws its text in, and the one switch the whole UI reads.
 *
 * The screens are written in English and never look at this: [Text] (LocalizedText.kt)
 * asks [t] on the way in, and this decides which catalogue answers -- [I18nZh] or
 * [I18nRu]. A catalogue is the whole of one translation, and anything it does not
 * know stays in English instead of going blank.
 */
enum class UiLanguage(val tag: String, val label: String) {
    /** Follow the phone's language: the matching catalogue on a Chinese or Russian phone, English otherwise. */
    AUTO("auto", "Follow system"),
    CHINESE("zh", "简体中文"),
    RUSSIAN("ru", "Русский"),
    ENGLISH("en", "English");

    companion object {
        fun of(tag: String?): UiLanguage = entries.firstOrNull { it.name == tag } ?: AUTO
    }
}

object I18n {
    @Volatile private var chosen: UiLanguage = UiLanguage.AUTO
    @Volatile private var systemChinese = false
    @Volatile private var systemRussian = false

    /** The phone's language tag, e.g. "zh-Hans-CN" or "ru-RU"; called at start and on a configuration change. */
    fun onSystemLanguage(tag: String?) {
        systemChinese = tag?.startsWith("zh", ignoreCase = true) == true
        systemRussian = tag?.startsWith("ru", ignoreCase = true) == true
    }

    fun choice(): UiLanguage = chosen

    fun choose(language: UiLanguage) {
        chosen = language
    }

    /** The language the UI draws in: the choice, or the phone's language under [UiLanguage.AUTO]. */
    fun language(): UiLanguage = when (chosen) {
        UiLanguage.AUTO -> when {
            systemChinese -> UiLanguage.CHINESE
            systemRussian -> UiLanguage.RUSSIAN
            else -> UiLanguage.ENGLISH
        }
        else -> chosen
    }

    /** The string to show for [text]: the chosen catalogue's entry, or [text] itself. */
    fun t(text: String): String = when {
        text.isEmpty() -> text
        else -> when (language()) {
            UiLanguage.CHINESE -> I18nZh.translate(text)
            UiLanguage.RUSSIAN -> I18nRu.translate(text)
            else -> text
        }
    }
}
