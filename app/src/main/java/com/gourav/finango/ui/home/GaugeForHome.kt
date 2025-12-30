package com.gourav.finango.ui.home

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.gourav.finango.R
import java.text.NumberFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale


class GaugeForHome : Fragment() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private lateinit var chart: PieChart

    // Animation properties
    private var centerTextAnimator: ValueAnimator? = null
    private val animationDuration = 1200 // ms

    private fun inr0() = object : ValueFormatter() {
        private val nf = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            currency = Currency.getInstance("INR")
            maximumFractionDigits = 0
        }
        override fun getFormattedValue(value: Float): String = nf.format(value.toDouble())
    }

    private val currencyFmt by lazy { inr0() }

    private fun userDoc() = db.collection("users")
        .document(requireNotNull(auth.currentUser?.uid) { "No user" })

    private fun txCol() = userDoc().collection("transactions")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gauge_for_home, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chart = view.findViewById(R.id.pieBudgetGauge)
        styleGauge()

        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val now = Date()

        // Show loading state
        showLoadingState()

        // 1) read budget from users/{uid}.budget
        userDoc().get().addOnSuccessListener { doc ->
            val budget = when (val v = doc.get("budget")) {
                is Number -> v.toDouble()
                is String -> v.replace(Regex("[^0-9.]"), "") // remove ₹ , etc.
                    .toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            // 2) sum expenses this month
            txCol()
                .whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                .whereLessThanOrEqualTo("timestamp", now)
                .get()
                .addOnSuccessListener { snap ->
                    var spent = 0.0
                    for (d in snap.documents) {
                        val type = (d.getString("type") ?: "")
                            .trim()
                            .lowercase(Locale.getDefault())
                        if (type != "expense") continue
                        val amt = when (val a = d.get("amount")) {
                            is Number -> a.toDouble()
                            is String -> a.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }
                        spent += amt
                    }
                    renderGaugeWithAnimation(budget, spent)
                }
                .addOnFailureListener { e ->
                    showNoData("Failed to read transactions")
                    Log.e("Gauge", "tx", e)
                }
        }.addOnFailureListener { e ->
            showNoData("Failed to read budget")
            Log.e("Gauge", "budget", e)
        }
    }

    private fun styleGauge() = chart.apply {
        description.isEnabled = false
        setUsePercentValues(false)
        setRotationEnabled(false) // disable finger rotation
        isRotationEnabled = false
        // Clean 2D half-donut
        isDrawHoleEnabled = true
        holeRadius = 65f
        transparentCircleRadius = 0f // no extra ring
        setHoleColor(Color.TRANSPARENT)
        setDrawEntryLabels(false)

        // Half circle only
        maxAngle = 180f
        rotationAngle = 180f // 180 = bottom semicircle

        legend.apply {
            isEnabled = true
            horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            orientation = Legend.LegendOrientation.HORIZONTAL
            textColor = Color.DKGRAY
            setDrawInside(true)
            textSize = 12f
            xEntrySpace = 12f
            yEntrySpace = 6f
            isWordWrapEnabled = true
        }

        setExtraOffsets(12f, 0f, 12f, 16f)

        // Enable touch and animation
        setTouchEnabled(true)
        setDragDecelerationFrictionCoef(0.9f)
    }

    private fun showLoadingState() {
        chart.centerText = "Loading..."
        chart.setCenterTextSize(14f)
        chart.setCenterTextColor(Color.parseColor("#9CA3AF"))

        // Animate loading text alpha
        val loadingAnimator = ValueAnimator.ofFloat(0.3f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator ->
                val alpha = animator.animatedValue as Float
                val color = Color.parseColor("#9CA3AF")
                val alphaColor = Color.argb(
                    (255 * alpha).toInt(),
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color)
                )
                chart.setCenterTextColor(alphaColor)
                chart.invalidate()
            }
        }
        loadingAnimator.start()

        // Stop loading animation after a timeout
        chart.postDelayed({
            loadingAnimator.cancel()
        }, 5000)
    }

    private fun renderGaugeWithAnimation(budget: Double, spent: Double) {
        // Cancel any existing animations
        centerTextAnimator?.cancel()

        if (budget <= 0.0) {
            animateCenterText("Set your monthly budget")
            chart.animateY(animationDuration, Easing.EaseOutCubic)
            chart.data = null
            chart.invalidate()
            return
        }

        val used = spent.coerceAtMost(budget)
        val remaining = (budget - spent).coerceAtLeast(0.0)

        // Enhanced colors with better contrast
        val spentColor = when {
            spent > budget -> Color.parseColor("#EF4444") // red if over budget
            spent > budget * 0.8 -> Color.parseColor("#F59E0B") // amber if close to budget
            else -> Color.parseColor("#3B82F6") // blue if within budget
        }
        val remainingColor = Color.parseColor("#E5E7EB") // light gray

        val entries = listOf(
            PieEntry(used.toFloat(), "Spent"),
            PieEntry(remaining.toFloat(), "Remaining")
        )

        val ds = PieDataSet(entries, "").apply {
            colors = listOf(spentColor, remainingColor)
            sliceSpace = 3f
            valueTextSize = 12f
            valueTextColor = Color.DKGRAY
            selectionShift = 8f
            setHighlightEnabled(true)
        }

        chart.data = PieData(ds).apply {
            setValueFormatter(currencyFmt)
            setDrawValues(false) // Initially hide values, will be shown by animation
        }

        // Animate filling
        animateGaugeFilling(used.toFloat(), remaining.toFloat(), budget, spent)

        // Animate center text
        val finalCenterText =
            "${currencyFmt.getFormattedValue(spent.toFloat())} / ${currencyFmt.getFormattedValue(budget.toFloat())}"
        animateCenterText(finalCenterText)
    }

    private fun animateGaugeFilling(
        targetSpent: Float,
        targetRemaining: Float,
        budget: Double,
        spent: Double
    ) {
        val fillAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration.toLong()
            interpolator = android.view.animation.DecelerateInterpolator()

            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float

                val currentSpent = targetSpent * progress
                val currentRemaining = targetRemaining + (targetSpent * (1f - progress))

                val entries = listOf(
                    PieEntry(currentSpent, "Spent"),
                    PieEntry(currentRemaining, "Remaining")
                )

                val spentColor = when {
                    spent > budget -> Color.parseColor("#EF4444")
                    spent > budget * 0.8 -> Color.parseColor("#F59E0B")
                    else -> Color.parseColor("#3B82F6")
                }
                val remainingColor = Color.parseColor("#E5E7EB")

                val ds = PieDataSet(entries, "").apply {
                    colors = listOf(spentColor, remainingColor)
                    sliceSpace = 3f
                    valueTextSize = 12f
                    valueTextColor = Color.DKGRAY
                    selectionShift = 8f
                    setHighlightEnabled(true)
                    setDrawValues(progress > 0.8f) // show values near end
                }

                chart.data = PieData(ds).apply {
                    setValueFormatter(currencyFmt)
                }
                chart.invalidate()
            }
        }
        fillAnimator.start()
    }

    private fun animateCenterText(targetText: String) {
        chart.setCenterTextColor(Color.parseColor("#374151"))
        chart.setCenterTextSize(14f)

        // Fade out current text
        centerTextAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 300
            addUpdateListener { animator ->
                val alpha = animator.animatedValue as Float
                val base = Color.parseColor("#374151")
                val alphaColor = Color.argb(
                    (255 * alpha).toInt(),
                    Color.red(base),
                    Color.green(base),
                    Color.blue(base)
                )
                chart.setCenterTextColor(alphaColor)
                chart.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Change text and fade in
                    chart.centerText = targetText
                    val fadeInAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 400
                        addUpdateListener { animator ->
                            val alpha = animator.animatedValue as Float
                            val base = Color.parseColor("#374151")
                            val alphaColor = Color.argb(
                                (255 * alpha).toInt(),
                                Color.red(base),
                                Color.green(base),
                                Color.blue(base)
                            )
                            chart.setCenterTextColor(alphaColor)
                            chart.invalidate()
                        }
                    }
                    fadeInAnimator.start()
                }
            })
        }
        centerTextAnimator?.start()
    }

    private fun showNoData(msg: String) {
        centerTextAnimator?.cancel()
        chart.clear()

        // Animate error message
        animateCenterText(msg)

        // Subtle shake animation for errors
        val shakeAnimator = ValueAnimator.ofFloat(0f, 10f, -10f, 5f, -5f, 0f).apply {
            duration = 500
            addUpdateListener { animator ->
                val translationX = animator.animatedValue as Float
                chart.translationX = translationX
            }
        }
        shakeAnimator.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        centerTextAnimator?.cancel()
    }


}