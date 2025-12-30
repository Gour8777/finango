package com.gourav.finango.ui.notifications

import com.google.logging.type.LogSeverity

data class AppNotification(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "",           // "recurring" | "anomaly"
    val severity: String = "",       // "low" | "med" | "high" | "info"
    val transactionId: String = "",  // for anomaly
    val riskScore: Int? = null,
    val reasons: List<String> = emptyList(),
    val status: String = "",         // "open" | "confirmed" | "disputed"
    var isExpanded: Boolean = false  // UI-only flag, not from Firestore
)

