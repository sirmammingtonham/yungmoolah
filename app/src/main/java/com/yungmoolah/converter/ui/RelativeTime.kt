package com.yungmoolah.converter.ui

import android.text.format.DateUtils

/** "Updated 2 hours ago"-style wording for the sync status line. */
fun relativeTimeLabel(millis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
    if (millis == null || millis <= 0L) return "never"
    if (nowMillis - millis < DateUtils.MINUTE_IN_MILLIS) return "just now"
    return DateUtils.getRelativeTimeSpanString(
        millis,
        nowMillis,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}
