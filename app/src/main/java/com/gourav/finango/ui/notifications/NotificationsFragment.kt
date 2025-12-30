package com.gourav.finango.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gourav.finango.R
import com.gourav.finango.databinding.FragmentNotificationsBinding
import java.util.Date

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: NotificationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = NotificationsAdapter(mutableListOf()) { notif, confirmed ->
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@NotificationsAdapter
            val db = FirebaseFirestore.getInstance()

            val newStatus = if (confirmed) "confirmed" else "disputed"

            // 1) Update notification status (and optional hidden flag if you want to hide it)
            db.collection("users").document(uid)
                .collection("notifications").document(notif.id)
                .update(
                    mapOf(
                        "status" to newStatus
                        // ,"hidden" to true   // uncomment if you want to remove from list after action
                    )
                )
            if (!notif.transactionId.isNullOrBlank()) {
                db.collection("users").document(uid)
                    .collection("riskEvents")
                    .whereEqualTo("txId", notif.transactionId)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { snap ->
                        val riskDoc = snap.documents.firstOrNull()
                        riskDoc?.reference?.update("status", newStatus)
                    }
            }


            // 2) Update UI list (e.g., remove it or just collapse)
            if (confirmed) {

                    notif.isExpanded = false
                    adapter.notifyDataSetChanged()

            } else {
                // Option B: keep but collapse
                notif.isExpanded = false
                adapter.notifyDataSetChanged()
            }

            val msg = if (confirmed) "Marked as safe" else "Marked as suspicious"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.adapter = adapter
        loadNotifications()
        val btnClose = view.findViewById<ImageView>(R.id.btnClose)
        btnClose.setOnClickListener {
            findNavController().navigateUp()
        }

    }
    private fun loadNotifications() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("notifications")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull{ doc ->
                    val ts = doc.getTimestamp("timestamp")?.toDate() ?: Date()
                    val reasonsList = (doc.get("reasons") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    val status = doc.getString("status") ?: ""
                    if (status == "confirmed") return@mapNotNull null

                    AppNotification(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        body = doc.getString("body") ?: "",
                        timestamp = ts.time,
                        type = doc.getString("type") ?: "",          // "recurring" | "anomaly"
                        severity = doc.getString("severity") ?: "",
                        transactionId = doc.getString("txId")?:"",
                        riskScore = (doc.get("riskScore") as? Number)?.toInt(),
                        reasons = reasonsList,
                        status = doc.getString("status") ?: ""
                    )
                }
                adapter.updateList(list.toMutableList())

            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }



    override fun     onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}