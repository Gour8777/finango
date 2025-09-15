package com.gourav.finango.ui.wallet

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.android.parcel.Parcelize


@Parcelize
data class Transactionget(
    var documentId: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val currency: String = "",
    val date: String = "",
    val description: String = "",
    val timestamp: Timestamp? = null,
    val type: String = ""
) : Parcelable

