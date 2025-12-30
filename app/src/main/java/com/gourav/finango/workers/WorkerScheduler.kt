package com.gourav.finango.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gourav.finango.ui.notifications.RecurringReminderWorker
import com.gourav.finango.ui.notifications.hasNotifiedToday
import com.gourav.finango.ui.notifications.setNotifiedToday
import java.util.concurrent.TimeUnit

/** Schedule the daily background job (doesn't run immediately). */
fun scheduleRecurringWorker(context: Context) {
    val periodic = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .addTag("recurring-worker-periodic")
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "recurring-worker",
        ExistingPeriodicWorkPolicy.UPDATE,
        periodic
    )
}

/** Run it once right now (so DAILY due items fire instantly). */
fun runRecurringWorkerNow(context: Context, uid: String) {
    val oneTime = OneTimeWorkRequestBuilder<RecurringWorker>()
        .setInputData(workDataOf("uid" to uid))
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .addTag("recurring-worker-now")
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "recurring-worker-now",
        ExistingWorkPolicy.REPLACE,
        oneTime
    )
}
fun scheduleRecurringReminder(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED) // to fetch Firestore
        .build()

    val work = PeriodicWorkRequestBuilder<RecurringReminderWorker>(24, TimeUnit.HOURS)
        .setConstraints(constraints)
        .setInputData(workDataOf("thresholdDays" to 1)) // 1 day before
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "recurring_reminder_work",
        ExistingPeriodicWorkPolicy.KEEP,
        work
    )
}
fun triggerImmediateRecurringReminderOncePerDay(context: Context) {
    if (!hasNotifiedToday(context)) {
        val oneTimeWork = OneTimeWorkRequestBuilder<RecurringReminderWorker>()
            .setInputData(workDataOf("thresholdDays" to 1))
            .build()

        WorkManager.getInstance(context).enqueue(oneTimeWork)
        setNotifiedToday(context)
    }
}
fun debugRunReminderNow(context: Context) {
    val oneTimeWork = OneTimeWorkRequestBuilder<RecurringReminderWorker>()
        .setInputData(workDataOf("thresholdDays" to 365)) // big window so it always matches
        .build()

    WorkManager.getInstance(context).enqueue(oneTimeWork)
}

