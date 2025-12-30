package com.gourav.finango.workers



import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.Date
import kotlin.math.abs

object AnomalyEngine {

    // -------------------- PUBLIC ENTRY POINT --------------------

    /**
     * Call this AFTER a new transaction doc is created and you have serverTimestamp.
     */
    fun runForNewTransaction(
        db: FirebaseFirestore,
        uid: String,
        txId: String,
        amount: Double,
        category: String,
        type: String,        // "income" or "expense"
        currency: String?,
        timestamp: Timestamp?
    ) {
        val txTime = timestamp?.toDate() ?: Date()

        // 1) Load last ~200 transactions for analytics + scoring
        db.collection("users").document(uid)
            .collection("transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(200)
            .get()
            .addOnSuccessListener { snap ->
                val history = mutableListOf<TxSample>()
                for (doc in snap.documents) {
                    val amt = doc.getDouble("amount") ?: continue
                    val cat = doc.getString("category")?.trim().takeUnless { it.isNullOrEmpty() } ?: "General"

                    val typ = doc.getString("type") ?: "expense"
                    val ts  = doc.getTimestamp("timestamp")?.toDate() ?: continue
                    history.add(TxSample(amt, cat, typ, ts))
                }

                if (history.size < 10) {
                    // too little history — skip analytics/anomaly
                    return@addOnSuccessListener
                }

                val current = TxSample(amount, category, type, txTime)

                // 2) Build analytics from history
                val analytics = buildAnalytics(history)

                // 3) Store analytics profile in Firestore
                writeAnalyticsProfile(db, uid, analytics)

                // 4) Score anomaly using analytics + recent history (for velocity/duplicates)
                val anomaly = computeAnomaly(current, currency, history, analytics)

                if (anomaly != null) {
                    // 5) If suspicious → write riskEvents + notifications
                    writeRiskAndNotification(
                        db = db,
                        uid = uid,
                        txId = txId,
                        amount = amount,
                        category = category,
                        result = anomaly
                    )
                }
            }
            .addOnFailureListener {
                // Ignore failures; don't break main flow.
            }
    }

    // -------------------- INTERNAL MODELS --------------------

    private data class TxSample(
        val amount: Double,
        val category: String,
        val type: String,
        val time: Date
    )

    private data class Stats(
        val median: Double,
        val mad: Double,
        val n: Int
    )

    private data class AnalyticsData(
        val byCategory: Map<String, Stats>,
        val overall: Stats,
        val byType: Map<String, Stats>,
        val hourHist: IntArray,  // size 24
        val dowHist: IntArray    // size 7 (0=Sun..6=Sat)
    )

    data class AnomalyResult(
        val score: Int,
        val severity: String,   // "low"|"med"|"high"
        val reasons: List<String>
    )

    // -------------------- ANALYTICS BUILD --------------------

    private fun buildAnalytics(history: List<TxSample>): AnalyticsData {
        val amountsOverall = mutableListOf<Double>()
        val amountsByCat = mutableMapOf<String, MutableList<Double>>()
        val amountsByType = mutableMapOf<String, MutableList<Double>>()
        val hourHist = IntArray(24) { 0 }
        val dowHist  = IntArray(7)  { 0 }

        for (h in history) {
            amountsOverall.add(h.amount)
            amountsByCat.getOrPut(h.category) { mutableListOf() }.add(h.amount)
            amountsByType.getOrPut(h.type) { mutableListOf() }.add(h.amount)

            val d = h.time
            hourHist[d.hours] = hourHist[d.hours] + 1
            dowHist[d.day]    = dowHist[d.day] + 1
        }

        fun stats(list: List<Double>): Stats {
            if (list.isEmpty()) return Stats(0.0, 1.0, 0)
            val med = median(list)
            val m   = mad(list, med)
            return Stats(med, m, list.size)
        }

        val catStats = mutableMapOf<String, Stats>()
        for ((cat, arr) in amountsByCat) {
            catStats[cat] = stats(arr)
        }

        val typeStats = mutableMapOf<String, Stats>()
        for ((typ, arr) in amountsByType) {
            typeStats[typ] = stats(arr)
        }

        val overallStats = stats(amountsOverall)

        return AnalyticsData(
            byCategory = catStats,
            overall = overallStats,
            byType = typeStats,
            hourHist = hourHist,
            dowHist = dowHist
        )
    }

    private fun writeAnalyticsProfile(
        db: FirebaseFirestore,
        uid: String,
        analytics: AnalyticsData
    ) {
        val byCatMap = mutableMapOf<String, Any>()

        for ((catRaw, s) in analytics.byCategory) {
            val cat = catRaw.trim()

            // 🚨 IMPORTANT: Firestore does NOT allow empty map keys
            if (cat.isEmpty()) continue
            // OR: val cat = if (catRaw.isBlank()) "General" else catRaw.trim()

            byCatMap[cat] = mapOf(
                "median" to s.median,
                "MAD"    to s.mad,
                "n"      to s.n,
                "updated_at" to FieldValue.serverTimestamp()
            )
        }


        val byTypeMap = mutableMapOf<String, Any>()
        for ((typ, s) in analytics.byType) {
            byTypeMap[typ] = mapOf(
                "median" to s.median,
                "MAD"    to s.mad,
                "n"      to s.n,
                "updated_at" to FieldValue.serverTimestamp()
            )
        }

        val hourHistMap = (0..23).associate { i -> i.toString() to analytics.hourHist[i] }
        val dowHistMap  = (0..6).associate { i -> i.toString() to analytics.dowHist[i] }

        val profileDoc = hashMapOf(
            "baselines" to mapOf(
                "by_category" to byCatMap,
                "overall"     to mapOf(
                    "median" to analytics.overall.median,
                    "MAD"    to analytics.overall.mad,
                    "n"      to analytics.overall.n,
                    "updated_at" to FieldValue.serverTimestamp()
                ),
                "by_type"     to byTypeMap
            ),
            "hour_hist" to hourHistMap,
            "dow_hist"  to dowHistMap,
            "profileUpdatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("users").document(uid)
            .collection("analytics").document("profile")
            .set(profileDoc, SetOptions.merge())
    }

    // -------------------- ANOMALY SCORING --------------------

    private fun computeAnomaly(
        current: TxSample,
        currency: String?,
        history: List<TxSample>,
        analytics: AnalyticsData
    ): AnomalyResult? {
        val Z_HI = 5.0
        val Z_MED = 3.0
        val VEL_5M = 3
        val VEL_1M = 2
        val RARE_BUCKET_MIN = 1
        val BIG_MULT_CAT = 1.5
        val DUP_WINDOW_MS = 120_000L

        val weights = mapOf(
            "cat_spike_hi"   to 40,
            "cat_spike_med"  to 20,
            "overall_spike"  to 15,
            "type_spike"     to 10,
            "velocity"       to 30,
            "odd_hour"       to 15,
            "odd_dow"        to 10,
            "duplicate"      to 25,
            "first_time_cat" to 20,
            "fx"             to 10
        )

        var score = 0
        val reasons = mutableListOf<String>()

        val catStats = analytics.byCategory[current.category]
            ?: Stats(analytics.overall.median, analytics.overall.mad, 0)
        val overallStats = analytics.overall
        val typeStats = analytics.byType[current.type]
            ?: Stats(analytics.overall.median, analytics.overall.mad, 0)

        // ---- Amount spikes ----
        val zCat = madZ(current.amount, catStats.median, catStats.mad)
        if (zCat >= Z_HI) {
            score += weights.getValue("cat_spike_hi")
            reasons += "amount_spike_category"
        } else if (zCat >= Z_MED) {
            score += weights.getValue("cat_spike_med")
            reasons += "amount_high_category"
        }

        val zOv = madZ(current.amount, overallStats.median, overallStats.mad)
        if (zOv >= Z_MED) {
            score += weights.getValue("overall_spike")
            reasons += "amount_high_overall"
        }

        val zType = madZ(current.amount, typeStats.median, typeStats.mad)
        if (zType >= Z_MED) {
            score += weights.getValue("type_spike")
            reasons += "amount_high_type"
        }

        // ---- Velocity & duplicates (5min/1min windows) ----
        val nowMs = current.time.time
        var count5m = 0
        var count1m = 0
        var isDuplicate = false

        for (h in history) {
            val diffMs = nowMs - h.time.time
            if (diffMs <= 0) continue
            if (diffMs <= 5 * 60 * 1000L) count5m++
            if (diffMs <= 60 * 1000L)     count1m++

            if (diffMs <= DUP_WINDOW_MS &&
                h.amount == current.amount &&
                h.category == current.category &&
                h.type == current.type) {
                isDuplicate = true
            }
        }

        if (count5m >= VEL_5M || count1m >= VEL_1M) {
            score += weights.getValue("velocity")
            reasons += "velocity"
        }
        if (isDuplicate) {
            score += weights.getValue("duplicate")
            reasons += "near_duplicate"
        }

        // ---- Odd hour / odd day with big amount ----
        val hr = current.time.hours
        val dow = current.time.day
        val catBaselineAmt = if (catStats.median > 0) catStats.median * BIG_MULT_CAT else 1500.0

        if (analytics.hourHist[hr] <= RARE_BUCKET_MIN && current.amount >= catBaselineAmt) {
            score += weights.getValue("odd_hour")
            reasons += "odd_hour_big_amount"
        }
        if (analytics.dowHist[dow] <= RARE_BUCKET_MIN && current.amount >= catBaselineAmt) {
            score += weights.getValue("odd_dow")
            reasons += "odd_dow_big_amount"
        }

        // ---- Foreign currency (if any non-INR) ----
        if (!currency.isNullOrBlank() && currency != "INR") {
            score += weights.getValue("fx")
            reasons += "foreign_currency"
        }

        if (reasons.isEmpty()) return null

        val severity = when {
            score >= 60 -> "high"
            score >= 30 -> "med"
            else        -> "low"
        }

        return AnomalyResult(score.coerceAtMost(100), severity, reasons)
    }

    // -------------------- WRITE RISK + NOTIFICATION --------------------

    private fun writeRiskAndNotification(
        db: FirebaseFirestore,
        uid: String,
        txId: String,
        amount: Double,
        category: String,
        result: AnomalyResult
    ) {
        val userRef = db.collection("users").document(uid)
        val now = FieldValue.serverTimestamp()

        // riskEvents
        val riskRef = userRef.collection("riskEvents").document()
        val riskDoc = hashMapOf(
            "txId" to txId,
            "severity" to result.severity,
            "risk_score_client" to result.score,
            "reasons_client" to result.reasons,
            "status" to "open",
            "created_at" to now,
            "model_ver" to "client_rules_v1"
        )
        riskRef.set(riskDoc)

        // notifications (reuse your notifications system)
        val notifDoc = hashMapOf(
            "type" to "anomaly",
            "title" to "Unusual payment detected",
            "body" to "₹$amount • $category • tap to review",
            "timestamp" to now,
            "txId" to txId,
            "severity" to result.severity,
            "risk_score" to result.score,
            "reasons" to result.reasons,
            "status" to "open"
        )
        userRef.collection("notifications").add(notifDoc)
    }

    // -------------------- UTILS --------------------

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }

    private fun mad(values: List<Double>, med: Double): Double {
        if (values.isEmpty()) return 1.0
        val dev = values.map { abs(it - med) }.sorted()
        val mid = dev.size / 2
        val m = if (dev.size % 2 == 0) {
            (dev[mid - 1] + dev[mid]) / 2.0
        } else {
            dev[mid]
        }
        return if (m <= 0.0) 1.0 else m
    }

    private fun madZ(x: Double, med: Double, mad: Double): Double {
        if (mad <= 0.0) return 0.0
        return abs(x - med) / mad
    }
}