package com.gourav.finango.ui.TransactionDetails

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gourav.finango.R

class TransactionDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_transaction_detail)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val documentId = intent.getStringExtra("documentId")
        val date         = intent.getStringExtra("date") ?: ""
        val amount       = intent.getDoubleExtra("amount", 0.0)
        val type         = intent.getStringExtra("type") ?: ""
        val category     = intent.getStringExtra("category") ?: ""
        val description  = intent.getStringExtra("description") ?: ""
        val tsSeconds    = intent.getLongExtra("timestampSeconds", 0L)
        val tsNanos      = intent.getIntExtra("timestampNanos", 0)
        Log.d("TransactionDetail", "Document ID: $documentId")
        Toast.makeText(this, "dfbd", Toast.LENGTH_SHORT).show()


    }
}