package com.gourav.finango.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.gourav.finango.MainActivity
import com.gourav.finango.R
import com.gourav.finango.databinding.ActivityProfileSetupBinding

class ProfileSetup : AppCompatActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var binding: ActivityProfileSetupBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.etEmail.text = auth.currentUser?.email.orEmpty()

        ArrayAdapter.createFromResource(
            this,
            R.array.income_ranges,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerBudget.adapter = adapter
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.nationalities,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerNationality.adapter = adapter
        }
        binding.spinnerBudget.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                val selectedIncome = parent.getItemAtPosition(position).toString()
                Toast.makeText(this@ProfileSetup, "Income: $selectedIncome", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.spinnerNationality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                val selectedNationality = parent.getItemAtPosition(position).toString()
                Toast.makeText(this@ProfileSetup, "Nationality: $selectedNationality", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        //handling submit button
        binding.btnSubmit.setOnClickListener {handlingSubmission()}
    }


    private fun handlingSubmission() = with(binding) {
        btnSubmit.isEnabled = false
        progressSubmit.visibility = View.VISIBLE

        fun show(msg: String) = Toast.makeText(this@ProfileSetup, msg, Toast.LENGTH_SHORT).show()
        fun enableBtn() {
            progressSubmit.visibility = View.GONE
            btnSubmit.isEnabled = true
        }

        val email = etEmail.text.toString().trim()
        val name = etName.text.toString().trim()
        val goals = etGoals.text.toString().trim()

        val dayStr = etDobDay.text.toString().trim()
        val monStr = etDobMonth.text.toString().trim()
        val yrStr  = etDobYear.text.toString().trim()

        // Length checks for strict formats
        if (dayStr.length != 2) { show("Enter day as DD"); enableBtn(); return@with }
        if (monStr.length != 2) { show("Enter month as MM"); enableBtn(); return@with }
        if (yrStr.length  != 4) { show("Enter year as YYYY"); enableBtn(); return@with }

        val budget = spinnerBudget.selectedItem?.toString()?.trim().orEmpty()
        val nationality = spinnerNationality.selectedItem?.toString()?.trim().orEmpty()

        if (email.isEmpty()) { show("Email not found. Please re-login."); enableBtn(); return@with }
        if (name.isEmpty()) { show("Please enter your name"); enableBtn(); return@with }
        if (goals.isEmpty()) { show("Please enter your goals"); enableBtn(); return@with }
        if (budget.isEmpty() || budget.equals("Select income", true)) {
            show("Please select your income"); enableBtn(); return@with
        }
        if (nationality.isEmpty() || nationality.equals("Select nationality", true)) {
            show("Please select your nationality"); enableBtn(); return@with
        }

        val day  = dayStr.toIntOrNull()
        val month = monStr.toIntOrNull()
        val year = yrStr.toIntOrNull()

        // Strict YYYY validation and full date validation
        validateDob(day, month, year)?.let { msg ->
            show(msg); enableBtn(); return@with
        }

        val dob = String.format("%04d-%02d-%02d", year, month, day)

        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) { show("User not authenticated"); enableBtn(); return@with }

        val profileData = hashMapOf(
            "email" to email,
            "name" to name,
            "goals" to goals,
            "budget" to budget,
            "dob" to dob,
            "nationality" to nationality,
            "profileCompleted" to true,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("users").document(uid)
            .set(profileData, SetOptions.merge())
            .addOnSuccessListener {
                show("Profile saved")
                val intent = Intent(this@ProfileSetup, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
                // finish() // optional
            }
            .addOnFailureListener { e ->
                show("Failed to save: ${e.localizedMessage}")
                enableBtn()
            }
    }


    private fun enableBtn() { binding.btnSubmit.isEnabled = true
    binding.progressSubmit.visibility=View.GONE}

    private fun isLeapYear(y: Int): Boolean =
        (y % 4 == 0) && (y % 100 != 0 || y % 400 == 0)




    private fun validateDob(day: Int?, month: Int?, year: Int?): String? {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

        if (day == null || month == null || year == null) return "Please enter DOB (DD / MM / YYYY)."
        if (year !in 1900..currentYear) return "Enter a valid year (1900–$currentYear)."
        if (month !in 1..12) return "Enter a valid month (01–12)."
        if (day !in 1..31) return "Enter a valid day (01–31)."

        val maxDay = when (month) {
            1,3,5,7,8,10,12 -> 31
            4,6,9,11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 31
        }
        if (day > maxDay) return "Day $day is not valid for month $month."
        return null
    }


}
