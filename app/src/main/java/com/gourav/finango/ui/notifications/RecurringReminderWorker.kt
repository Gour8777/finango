package com.gourav.finango.ui.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class RecurringReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override suspend fun doWork(): Result {
        try {
            val user = auth.currentUser
            if (user == null) {
                android.util.Log.d("RecReminder", "No currentUser, skipping")
                return Result.success()
            }
            val uid = user.uid
            val thresholdDays = inputData.getInt("thresholdDays", 1)

            android.util.Log.d("RecReminder", "Running for uid=$uid thresholdDays=$thresholdDays")

            val snaps = db.collection("users")
                .document(uid)
                .collection("recurringTransactions")
                .whereEqualTo("isActive", true)
                .get()
                .await()

            android.util.Log.d("RecReminder", "Active recurring templates: ${snaps.size()}")

            val today = clearTime(Date())
            var notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

            for (doc in snaps.documents) {
                val data = doc.data ?: continue
                val nextDue = (data["nextDueDate"] as? String)?.takeIf { it.isNotBlank() } ?: continue
                val endDateStr = (data["endDate"] as? String)?.takeIf { it.isNotBlank() }
                val title = (data["title"] as? String) ?: "Recurring"
                val amount = (data["amount"]?.toString()) ?: ""
                val category = (data["category"] as? String) ?: ""

                val nextDate = safeParse(nextDue) ?: continue
                val endDate = endDateStr?.let { safeParse(it) }

                if (endDate != null && nextDate.after(endDate)) {
                    android.util.Log.d("RecReminder", "Skipping ${doc.id} – after endDate")
                    continue
                }

                val daysUntil = daysBetween(today, nextDate)

                android.util.Log.d(
                    "RecReminder",
                    "doc=${doc.id} nextDue=$nextDue daysUntil=$daysUntil endDate=$endDateStr"
                )

                if (daysUntil in 0..thresholdDays) {
                    android.util.Log.d("RecReminder", "doc=${doc.id} is within window, sending notif")

                    val recurringType = (data["type"] as? String) ?: "expense"
                    val body = if (recurringType.equals("income", ignoreCase = true)) {
                        "₹$amount • $category • expected on $nextDue"
                    } else {
                        "₹$amount • $category • due on $nextDue"
                    }

                    postNotification(applicationContext, notifId++, title, body)

                    val notifData = hashMapOf(
                        "title" to title,
                        "body" to body,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "txId" to doc.id,
                        "type" to "recurring",
                        "severity" to "info"
                    )

                    // 🔴 Important: await this so the write actually completes in the worker
                    db.collection("users")
                        .document(uid)
                        .collection("notifications")
                        .add(notifData)
                        .await()

                    android.util.Log.d("RecReminder", "Saved Firestore notification for ${doc.id}")
                }
            }

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("RecReminder", "Error in doWork: ${e.localizedMessage}")
            return Result.retry()
        }
    }


    private fun clearTime(d: Date): Date {
        val c = Calendar.getInstance().apply { time = d }
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.time
    }

    private fun safeParse(s: String): Date? = try {
        df.isLenient = false
        df.parse(s)
    } catch (_: Exception) { null }

    private fun daysBetween(a: Date, b: Date): Long {
        val diff = b.time - a.time
        return diff / (24 * 60 * 60 * 1000)
    }



}
