package com.gourav.finango.ui.notifications

// NotificationUtils.kt
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gourav.finango.R

const val NOTIF_CHANNEL_ID = "recurring_reminders"
const val NOTIF_CHANNEL_NAME = "Recurring payments"
const val NOTIF_CHANNEL_DESC = "Reminders for upcoming recurring payments"

fun ensureNotificationChannel(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID, NOTIF_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply { description = NOTIF_CHANNEL_DESC }
            nm.createNotificationChannel(channel)
        }
    }
}

fun postNotification(ctx: Context, id: Int, title: String, body: String) {
    ensureNotificationChannel(ctx)
    val notif = NotificationCompat.Builder(ctx, NOTIF_CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(body)
        .setSmallIcon(R.drawable.logo) // replace with your icon
        .setAutoCancel(true)
        .build()
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(id, notif)
}
