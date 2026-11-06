package org.un1box.android

/**
 * What language the app draws its text in, and the one switch the whole UI reads.
 *
 * The screens are written in English and never look at this: [Text] (LocalizedText.kt)
 * asks [t] on the way in, and this decides whether [I18nZh] answers. That keeps one
 * catalogue as the whole of the translation, and anything it does not know stays in
 * English instead of going blank.
 */
enum class UiLanguage(val tag: String, val label: String) {
    /** Follow the phone's language: Chinese for a Chinese phone, English otherwise. */
    AUTO("auto", "Follow system"),
    CHINESE("zh", "简体中文"),
    ENGLISH("en", "English");

    companion object {
        fun of(tag: String?): UiLanguage = entries.firstOrNull { it.name == tag } ?: AUTO
    }
}

object I18n {
    @Volatile private var chosen: UiLanguage = UiLanguage.AUTO
    @Volatile private var systemChinese = false

    /** The phone's language tag, e.g. "zh-Hans-CN"; called at start and on a configuration change. */
    fun onSystemLanguage(tag: String?) {
        systemChinese = tag?.startsWith("zh", ignoreCase = true) == true
    }

    fun choice(): UiLanguage = chosen

    fun choose(language: UiLanguage) {
        chosen = language
    }

    /** True while the UI draws in Chinese. */
    val chinese: Boolean
        get() = when (chosen) {
            UiLanguage.AUTO -> systemChinese
            UiLanguage.CHINESE -> true
            UiLanguage.ENGLISH -> false
        }

    /** The string to show for [text]: the Chinese catalogue's entry, or [text] itself. */
    fun t(text: String): String = if (text.isEmpty() || !chinese) text else I18nZh.translate(text)
}
