package com.gourav.finango.ui.anomaly

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gourav.finango.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnomalyDetail : Fragment() {

    private lateinit var db: FirebaseFirestore
    private var notificationId: String? = null
    private var transactionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FirebaseFirestore.getInstance()

        arguments?.let { args ->
            notificationId = args.getString("notificationId")
            transactionId = args.getString("transactionId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_anomaly_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Top bar
        val btnClose = view.findViewById<ImageView>(R.id.btnClose)

        // Transaction card views
        val tvAmount = view.findViewById<TextView>(R.id.tvAmount)
        val tvCategory = view.findViewById<TextView>(R.id.tvCategory)
        val tvDateTime = view.findViewById<TextView>(R.id.tvDateTime)

        // Severity card views
        val tvSeverity = view.findViewById<TextView>(R.id.tvSeverity)
        val tvRiskScore = view.findViewById<TextView>(R.id.tvRiskScore)

        // Reasons card views
        val tvReasons = view.findViewById<TextView>(R.id.tvReasons)

        // Action buttons
        val btnYes = view.findViewById<Button>(R.id.btnYesThisWasMe)
        val btnNo = view.findViewById<Button>(R.id.btnNoNotMe)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // --- Close button ---
        btnClose.setOnClickListener {
            findNavController().navigateUp()
        }

        // --- 1) Load NOTIFICATION: severity, riskScore, reasons, body ----
        if (notificationId != null) {
            db.collection("users").document(uid)
                .collection("notifications").document(notificationId!!)
                .get()
                .addOnSuccessListener { doc ->
                    val severity = doc.getString("severity") ?: ""
                    val score = (doc.get("risk_score") as? Number)?.toInt()
                    val reasonsList = doc.get("reasons") as? List<*> ?: emptyList<Any>()
                    val reasonsText = reasonsList.joinToString("\n") { "• $it" }

                    // Fill severity card
                    tvSeverity.text = when (severity.lowercase(Locale.getDefault())) {
                        "high" -> "High severity alert"
                        "med"  -> "Medium severity alert"
                        "low"  -> "Low severity alert"
                        else   -> "Unusual activity"
                    }

                    tvRiskScore.text = if (score != null) {
                        "Risk score: $score"
                    } else {
                        "Risk score: N/A"
                    }

                    // Fill reasons card
                    tvReasons.text = if (reasonsText.isNotBlank()) {
                        reasonsText
                    } else {
                        "• This transaction is unusual compared to your normal pattern."
                    }
                }
        }

        // --- 2) Load TRANSACTION: amount, category, timestamp ---
        if (!transactionId.isNullOrBlank()) {
            db.collection("users").document(uid)
                .collection("transactions").document(transactionId!!)
                .get()
                .addOnSuccessListener { doc ->
                    val amount = doc.getDouble("amount") ?: 0.0
                    val category = doc.getString("category") ?: "Unknown"
                    val ts = doc.getTimestamp("timestamp")?.toDate() ?: Date()

                    val formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        .format(ts)

                    tvAmount.text = "₹${amount.toInt()}"      // or String.format("₹%.2f", amount)
                    tvCategory.text = category
                    tvDateTime.text = formattedDate
                }
        }

        // --- 3) Button actions – update notification.status only ---
        btnYes.setOnClickListener {
            notificationId?.let {
                db.collection("users").document(uid)
                    .collection("notifications").document(it)
                    .update("status", "confirmed")
            }
            if (!transactionId.isNullOrBlank()) {
                db.collection("users").document(uid)
                    .collection("riskEvents")
                    .whereEqualTo("txId", transactionId)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { snap ->
                        val riskDoc = snap.documents.firstOrNull()
                        riskDoc?.reference?.update("status", "confirmed")
                    }
            }
            Toast.makeText(requireContext(), "Marked as safe", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }

        btnNo.setOnClickListener {
            notificationId?.let {
                db.collection("users").document(uid)
                    .collection("notifications").document(it)
                    .update("status", "disputed")
            }
            if (!transactionId.isNullOrBlank()) {
                db.collection("users").document(uid)
                    .collection("riskEvents")
                    .whereEqualTo("txId", transactionId)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { snap ->
                        val riskDoc = snap.documents.firstOrNull()
                        riskDoc?.reference?.update("status", "disputed")
                    }
            }
            Toast.makeText(
                requireContext(),
                "Marked as suspicious – please review with your bank.",
                Toast.LENGTH_SHORT
            ).show()
            findNavController().navigateUp()
        }
    }
}
