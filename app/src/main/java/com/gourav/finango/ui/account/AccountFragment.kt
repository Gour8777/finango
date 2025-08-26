package com.gourav.finango.ui.account

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.gourav.finango.databinding.FragmentAccountBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.text.method.TextKeyListener
import androidx.core.view.isVisible
import com.gourav.finango.ui.login.LoginScreen

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var isEditing = false

    private lateinit var incomeOptions: List<String>
    private lateinit var nationalityOptions: List<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        setLoading(true)
        // Build dropdown lists + set adapters
        incomeOptions = buildIncomeRanges()
        nationalityOptions = buildNationalityList()

        binding.ddIncome.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, incomeOptions)
        )
        binding.ddNationality.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, nationalityOptions)
        )

        // Load profile
        loadUserData()

        // Edit / Save toggle
        binding.personaldetailsedit.setOnClickListener {
            if (!isEditing) {
                isEditing = true
                binding.personaldetailsedit.text = "Save"
                switchToEditMode(true)
            } else {
                saveUserData()
            }
        }

        // Logout
        binding.logoutlayout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign out?")
                .setMessage("You will be signed out of your account.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sign out") { _, _ ->
                    auth.signOut()
                    val intent = Intent(requireContext(), LoginScreen::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .show()
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressProfile.isVisible = loading
        binding.accountContent.isVisible = !loading

        // optionally disable actions while loading
        binding.personaldetailsedit.isEnabled = !loading
        binding.logoutlayout.isEnabled = !loading
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: run {
            setLoading(false)
            return
        }

        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener

                if (doc.exists()) {
                    val name = doc.getString("name").orEmpty()
                    val email = doc.getString("email").orEmpty()
                    val goal = doc.getString("goals").orEmpty()   // keep your field name
                    val income = doc.getString("income").orEmpty()
                    val nationality = doc.getString("nationality").orEmpty()

                    binding.usernameheader.text = if (name.isNotBlank()) "Hi, $name" else "Hi"
                    binding.usermailheader.text = email
                    binding.username.setText(name)
                    binding.usergoal.setText(goal)

                    binding.tvIncome.text = income.ifBlank { "Income not set" }
                    binding.tvNationality.text = nationality.ifBlank { "Nationality not set" }

                    binding.ddIncome.setText(matchClosest(incomeOptions, income), false)
                    binding.ddNationality.setText(matchClosest(nationalityOptions, nationality), false)

                    switchToEditMode(false)
                    isEditing = false
                    binding.personaldetailsedit.text = "Edit"
                } else {
                    Toast.makeText(requireContext(), "Profile not found", Toast.LENGTH_SHORT).show()
                }

                // ✅ hide loader on success (exists or not)
                setLoading(false)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Failed to load data: ${e.message}", Toast.LENGTH_SHORT).show()
                // ✅ hide loader on failure
                setLoading(false)
            }
    }


    private fun saveUserData() {
        setLoading(true) // optional: show loading while saving

        val uid = auth.currentUser?.uid ?: run {
            setLoading(false)
            return
        }

        val updatedData = mapOf(
            "name" to binding.username.text.toString().trim(),
            "goals" to binding.usergoal.text.toString().trim(),
            "income" to binding.ddIncome.text.toString().trim().ifBlank { binding.tvIncome.text.toString() },
            "nationality" to binding.ddNationality.text.toString().trim().ifBlank { binding.tvNationality.text.toString() }
        )

        firestore.collection("users").document(uid)
            .update(updatedData)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                binding.tvIncome.text = updatedData["income"]
                binding.tvNationality.text = updatedData["nationality"]
                switchToEditMode(false)
                isEditing = false
                binding.personaldetailsedit.text = "Edit"
                Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
    }

    private fun switchToEditMode(enable: Boolean) {
        // Name + Goal: keep enabled look but lock editing when not in edit mode
        setEditable(binding.username, enable)
        setEditable(binding.usergoal, enable)

        // Income + Nationality: swap View <-> Dropdown
        binding.tvIncome.visibility = if (enable) View.GONE else View.VISIBLE
        binding.tilIncome.visibility = if (enable) View.VISIBLE else View.GONE

        binding.tvNationality.visibility = if (enable) View.GONE else View.VISIBLE
        binding.tilNationality.visibility = if (enable) View.VISIBLE else View.GONE
    }

    private fun setEditable(et: EditText, enable: Boolean) {
        et.isFocusable = enable
        et.isFocusableInTouchMode = enable
        et.isCursorVisible = enable
        et.keyListener = if (enable) TextKeyListener.getInstance() else null
    }

    private fun buildIncomeRanges(): List<String> {
        val list = mutableListOf<String>()
        val step = 5000
        val min = 10000   // first income value
        val max = 200000  // adjust as needed

        var amount = min
        while (amount <= max) {
            list.add("₹%,d".format(amount))
            amount += step
        }
        return list
    }


    private fun buildNationalityList(): List<String> {
        // Minimal demo list — replace with a full list if needed
        return listOf(
            "Indian", "American", "British", "Canadian", "Australian", "Bangladeshi",
            "Nepalese", "Sri Lankan", "Singaporean", "German", "French", "Other"
        )
    }

    private fun matchClosest(options: List<String>, currentValue: String): String {
        if (currentValue.isBlank()) return ""
        return options.firstOrNull { it.equals(currentValue, ignoreCase = true) }
            ?: options.firstOrNull { it.contains(currentValue, ignoreCase = true) }
            ?: ""
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
