package com.illuminazionetech.vrclip.util

import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

fun Long.toLocalizedString(locale: Locale = Locale.getDefault()): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        .format(Date.from(Instant.ofEpochMilli(this)))
}
