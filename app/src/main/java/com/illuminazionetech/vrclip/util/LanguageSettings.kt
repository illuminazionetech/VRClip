package com.illuminazionetech.vrclip.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import com.illuminazionetech.vrclip.R
import java.util.Locale

// Do not modify
private const val SIMPLIFIED_CHINESE = 1
private const val ENGLISH = 2
private const val CZECH = 3
private const val FRENCH = 4
private const val GERMAN = 5
private const val NORWEGIAN_BOKMAL = 6
private const val DANISH = 7
private const val SPANISH = 8
private const val TURKISH = 9
private const val UKRAINIAN = 10
private const val RUSSIAN = 11
private const val ARABIC = 12
private const val PERSIAN = 13
private const val INDONESIAN = 14
private const val FILIPINO = 15
private const val ITALIAN = 16
private const val DUTCH = 17
private const val PORTUGUESE_BRAZIL = 18
private const val JAPANESE = 19
private const val POLISH = 20
private const val HUNGARIAN = 21
private const val MALAY = 22
private const val TRADITIONAL_CHINESE = 23
private const val VIETNAMESE = 24
private const val BELARUSIAN = 25
private const val CROATIAN = 26
private const val BASQUE = 27
private const val HINDI = 28
private const val MALAYALAM = 29
private const val SINHALA = 30
private const val SERBIAN = 31
private const val AZERBAIJANI = 32
private const val NORWEGIAN_NYNORSK = 33
private const val PUNJABI = 34
private const val TAMIL = 35
private const val KOREAN = 36
private const val SWEDISH = 37
private const val PORTUGUESE_PORTUGAL = 38
private const val CATALAN = 39
private const val HEBREW = 40
private const val PORTUGUESE = 41
private const val THAI = 42
private const val BENGALI = 43
private const val KHMER = 44
private const val KANNADA = 45
private const val GREEK = 46
private const val MONGOLIAN = 47

/**
 * Builds a locale exactly as the legacy constructor did (keeping codes such as "in" and "he"
 * as-is), so locales saved by earlier versions still compare equal to these keys.
 */
@Suppress("DEPRECATION")
private fun locale(language: String, country: String = ""): Locale = Locale(language, country)

val LocaleLanguageCodeMap =
    mapOf(
        locale("ar") to ARABIC,
        locale("az") to AZERBAIJANI,
        locale("eu") to BASQUE,
        locale("be") to BELARUSIAN,
        locale("bn") to BENGALI,
        locale("ca") to CATALAN,
        Locale.forLanguageTag("zh-Hans") to SIMPLIFIED_CHINESE,
        Locale.forLanguageTag("zh-Hant") to TRADITIONAL_CHINESE,
        locale("hr") to CROATIAN,
        locale("cs") to CZECH,
        locale("da") to DANISH,
        locale("nl") to DUTCH,
        locale("en", "US") to ENGLISH,
        locale("fil") to FILIPINO,
        locale("fr") to FRENCH,
        locale("de") to GERMAN,
        locale("el") to GREEK,
        locale("he") to HEBREW,
        locale("hi") to HINDI,
        locale("hu") to HUNGARIAN,
        locale("in") to INDONESIAN,
        locale("it") to ITALIAN,
        locale("ja") to JAPANESE,
        locale("kn") to KANNADA,
        locale("km") to KHMER,
        locale("ko") to KOREAN,
        locale("ms") to MALAY,
        locale("ml") to MALAYALAM,
        locale("mn") to MONGOLIAN,
        locale("nb") to NORWEGIAN_BOKMAL,
        locale("nn") to NORWEGIAN_NYNORSK,
        locale("fa") to PERSIAN,
        locale("pl") to POLISH,
        locale("pt") to PORTUGUESE,
        locale("pt", "PT") to PORTUGUESE_PORTUGAL,
        locale("pt", "BR") to PORTUGUESE_BRAZIL,
        locale("pa") to PUNJABI,
        locale("ru") to RUSSIAN,
        locale("sr") to SERBIAN,
        locale("si") to SINHALA,
        locale("es") to SPANISH,
        locale("sv") to SWEDISH,
        locale("ta") to TAMIL,
        locale("th") to THAI,
        locale("tr") to TURKISH,
        locale("uk") to UKRAINIAN,
        locale("vi") to VIETNAMESE,
    )

@Composable
fun Locale?.toDisplayName(): String =
    this?.getDisplayName(this) ?: stringResource(id = R.string.follow_system)

fun setLanguage(locale: Locale?) {
    val localeList =
        locale?.let { LocaleListCompat.create(it) } ?: LocaleListCompat.getEmptyLocaleList()
    AppCompatDelegate.setApplicationLocales(localeList)
}
