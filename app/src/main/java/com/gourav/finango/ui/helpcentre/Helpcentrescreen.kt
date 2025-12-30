package com.gourav.finango.ui.helpcentre

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gourav.finango.MainActivity
import com.gourav.finango.R
import com.gourav.finango.databinding.ActivityHelpcentrescreenBinding
import com.gourav.finango.ui.faq.faqscreen


class Helpcentrescreen : AppCompatActivity() {
    private lateinit var binding: ActivityHelpcentrescreenBinding

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHelpcentrescreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge insets (optional)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }
        binding.rowChat.setOnClickListener {
            val i = Intent(this, MainActivity::class.java).apply {
                putExtra("navigate_to_fragment", R.id.navigation_chatbot)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(i)
            finish() // optional
        }


        binding.btnClose.setOnClickListener { finish() }

        // Row actions
        binding.rowFaq.setOnClickListener {
            startActivity(Intent(this, faqscreen::class.java))
        }

        binding.rowGuides.setOnClickListener {
            // Open your How-To screen or a static URL
            // startActivity(Intent(this, GuidesActivity::class.java))
            openUrl("https://your.site/help/guides")
        }



        binding.rowContact.setOnClickListener {
            composeEmail(
                address = "support@finango.com",
                subject = "Finango Support Request",
                body = "Describe your issue here:\n\nApp Version: ${appVersion()}",
            )
        }

        binding.rowReport.setOnClickListener {
            // startActivity(Intent(this, ReportIssueActivity::class.java))
            // Or a web form fallback
            openUrl("https://your.site/help/report")
        }

        binding.rowPrivacy.setOnClickListener {
            // startActivity(Intent(this, PrivacyPolicyActivity::class.java))
            openUrl("https://your.site/privacy")
        }

        // Footer version
        binding.tvVersion.text = "Version ${appVersion()}"
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun appVersion(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val name = pInfo.versionName ?: "—"
            val code = pInfo.longVersionCode
            "$name ($code)"
        } catch (e: Exception) {
            "—"
        }
    }

    private fun composeEmail(address: String, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$address")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Fallback: open default mail app
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:$address"))
            startActivity(fallback)
        }
    }

    private fun openUrl(url: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(i)
    }
}
