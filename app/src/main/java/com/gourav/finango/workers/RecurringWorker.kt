package com.gourav.finango.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Runs through users/{uid}/recurringTransactions and generates real transactions
 * with ONLY these 7 fields: type, category, description, amount, currency, date, timestamp.
 * Supports DAILY/WEEKLY/MONTHLY/YEARLY and backfills missed periods (bounded).
 */
class RecurringWorker(appContext: Context, params: WorkerParameters)
    : CoroutineWorker(appContext, params) {

    private val db by lazy { FirebaseFirestore.getInstance() }

    // ---- date helpers (API 24 safe) ----
    private fun sdf() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private fun fmt(cal: Calendar) = sdf().format(cal.time)
    private fun todayCal(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    private fun parseDateOrNull(s: String?): Calendar? {
        if (s.isNullOrBlank()) return null
        return try {
            Calendar.getInstance().apply {
                time = sdf().parse(s)!!
                set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
        } catch (_: Exception) { null }
    }
    private fun nextMonthlyAnchored(from: Calendar, anchorDay: Int): Calendar {
        val cal = from.clone() as Calendar
        cal.add(Calendar.MONTH, 1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, anchorDay.coerceIn(1, maxDay))
        return cal
    }
    private fun nextYearlyAnchored(from: Calendar, anchorMonth0: Int, anchorDay: Int): Calendar {
        val cal = from.clone() as Calendar
        cal.add(Calendar.YEAR, 1)
        cal.set(Calendar.MONTH, anchorMonth0)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, anchorDay.coerceIn(1, maxDay))
        return cal
    }
    private fun nextWeeklySameWeekday(from: Calendar): Calendar {
        val cal = from.clone() as Calendar
        cal.add(Calendar.WEEK_OF_YEAR, 1)
        return cal
    }
    private fun periodKey(frequency: String, due: Calendar): String =
        when (frequency) {
            "Weekly"  -> SimpleDateFormat("YYYY-ww", Locale.US).format(due.time) // ISO-ish
            "Monthly" -> SimpleDateFormat("yyyy-MM", Locale.US).format(due.time)
            "Yearly"  -> SimpleDateFormat("yyyy", Locale.US).format(due.time)
            else      -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(due.time) // DAILY
        }

    override suspend fun doWork(): Result {
        // accept uid via inputData for immediate runs; fallback to FirebaseAuth
        val inputUid = inputData.getString("uid")
        val uid = inputUid ?: FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.d("RecurringWorker", "No UID; skipping run.")
            return Result.success()
        }

        val userRef = db.collection("users").document(uid)
        val tplCol = userRef.collection("recurringTransactions")

        val today = todayCal()
        val todayStr = fmt(today)
        Log.d("RecurringWorker", "Start. uid=$uid Today=$todayStr")

        val templates = try {
            tplCol.whereEqualTo("isActive", true)
                .whereLessThanOrEqualTo("nextDueDate", todayStr)
                .get().await()
        } catch (e: Exception) {
            Log.e("RecurringWorker", "Query failed: ${e.localizedMessage}")
            return Result.retry()
        }

        if (templates.isEmpty) {
            Log.d("RecurringWorker", "No due templates.")
            return Result.success()
        }
        Log.d("RecurringWorker", "Due templates: ${templates.size()}")

        for (doc in templates.documents) {
            val tplRef = doc.reference
            val tpl = doc.data ?: continue

            val frequency = (tpl["frequency"] as? String) ?: "Monthly"
            val title = (tpl["title"] as? String) ?: "Recurring"
            val amount = (tpl["amount"] as? Number)?.toDouble() ?: 0.0
            val currency = (tpl["currency"] as? String) ?: "INR"
            val category = (tpl["category"] as? String) ?: "General"
            val type = (tpl["type"] as? String) ?: "expense"

            val anchorDay = (tpl["anchorDay"] as? Number)?.toInt() ?: today.get(Calendar.DAY_OF_MONTH)
            val anchorMonth1 = (tpl["anchorMonth"] as? Number)?.toInt() ?: (today.get(Calendar.MONTH) + 1)
            val anchorMonth0 = anchorMonth1 - 1

            var nextDueStr: String? = tpl["nextDueDate"] as? String ?: continue
            val endDateStr = tpl["endDate"] as? String
            val endDate = parseDateOrNull(endDateStr)

            val MAX_BACKFILL = 36
            var generated = 0

            Log.d("RecurringWorker", "Processing tpl=${doc.id} freq=$frequency title=$title amount=$amount next=$nextDueStr end=$endDateStr")

            while (true) {
                val nextDue = parseDateOrNull(nextDueStr) ?: break
                if (nextDue.after(today)) break
                if (endDate != null && nextDue.after(endDate)) {
                    try {
                        tplRef.update(mapOf("isActive" to false, "updatedAt" to FieldValue.serverTimestamp())).await()
                        Log.d("RecurringWorker", "Deactivated tpl=${doc.id} (end reached).")
                    } catch (e: Exception) {
                        Log.e("RecurringWorker", "Failed to deactivate tpl=${doc.id}: ${e.localizedMessage}")
                    }
                    break
                }
                if (generated >= MAX_BACKFILL) {
                    Log.d("RecurringWorker", "Max backfill reached for tpl=${doc.id}")
                    break
                }

                val currentPeriodKey = periodKey(frequency, nextDue)

                val resultNext: String? = try {
                    db.runTransaction { tx ->
                        val fresh = tx.get(tplRef)
                        if (!fresh.exists()) return@runTransaction null
                        val data = fresh.data ?: return@runTransaction null

                        val freshNext = data["nextDueDate"] as? String ?: return@runTransaction null
                        if (freshNext != nextDueStr) return@runTransaction null

                        val lastGen = data["lastGeneratedPeriod"] as? String
                        if (lastGen == currentPeriodKey) return@runTransaction null

                        val txnRef = userRef.collection("transactions").document()
                        val payload = mapOf(
                            "type" to type,
                            "category" to category,
                            "description" to title,
                            "amount" to amount,
                            "currency" to currency,
                            "date" to fmt(nextDue),
                            "timestamp" to FieldValue.serverTimestamp()
                        )
                        tx.set(txnRef, payload)

                        val following: Calendar = when (frequency) {
                            "Daily"   -> (nextDue.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                            "Weekly"  -> nextWeeklySameWeekday(nextDue)
                            "Monthly" -> nextMonthlyAnchored(nextDue, anchorDay)
                            "Yearly"  -> nextYearlyAnchored(nextDue, anchorMonth0, anchorDay)
                            else      -> nextMonthlyAnchored(nextDue, anchorDay)
                        }

                        val updates = mutableMapOf<String, Any>(
                            "nextDueDate" to fmt(following),
                            "lastGeneratedPeriod" to currentPeriodKey,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        tx.update(tplRef, updates)

                        fmt(following)
                    }.await()
                } catch (e: Exception) {
                    Log.e("RecurringWorker", "Txn failed tpl=${doc.id}: ${e.localizedMessage}")
                    null
                }

                if (resultNext != null) {
                    Log.d("RecurringWorker", "Generated txn tpl=${doc.id} on $nextDueStr; next=${resultNext}")
                    nextDueStr = resultNext
                    generated++
                } else {
                    // re-read latest state and decide
                    val freshDoc = try { tplRef.get().await() } catch (e: Exception) {
                        Log.e("RecurringWorker", "Refetch failed tpl=${doc.id}: ${e.localizedMessage}")
                        null
                    }
                    nextDueStr = freshDoc?.getString("nextDueDate")
                    if (nextDueStr == null) {
                        Log.d("RecurringWorker", "Stop tpl=${doc.id} (no nextDueDate).")
                        break
                    }
                }
            }

            Log.d("RecurringWorker", "Done tpl=${doc.id}; generated=$generated")
        }

        Log.d("RecurringWorker", "Finish.")
        return Result.success()
    }
}
