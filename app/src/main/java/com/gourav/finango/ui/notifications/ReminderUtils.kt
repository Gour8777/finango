package com.gourav.finango.ui.notifications

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS_NAME = "recurring_reminder_prefs"
private const val KEY_LAST_NOTIFIED = "last_notified_date"

fun hasNotifiedToday(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val lastDate = prefs.getString(KEY_LAST_NOTIFIED, null)
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return lastDate == today
}

fun setNotifiedToday(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    prefs.edit().putString(KEY_LAST_NOTIFIED, today).apply()
}
fun resetNotifiedToday(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_LAST_NOTIFIED, "2000-01-01").apply()
}
