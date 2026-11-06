package com.cleantv.launcher.ui.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "on 18 Dec" this year, "on 18 Dec 2027" after that. */
fun releaseLabel(date: LocalDate): String {
    val pattern = if (date.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"
    return "on " + date.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
}
