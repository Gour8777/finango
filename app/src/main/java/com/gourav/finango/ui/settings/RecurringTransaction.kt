// RecurringTransaction.kt
package com.gourav.finango.ui.settings

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RecurringTransaction(
    val id: String = "",
    val title: String = "",
    val amount: Long = 0L,
    val currency: String = "INR",
    val category: String = "General",
    val type: String = "expense",
    val frequency: String = "DAILY",
    val endDate: String? = null,
    val nextDueDate: String? = null,
    val isActive: Boolean = true,
) : Parcelable
