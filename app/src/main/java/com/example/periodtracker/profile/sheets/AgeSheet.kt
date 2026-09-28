package com.example.periodtracker.profile.sheets

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.periodtracker.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider

class AgeSheet(
    private val currentAge: Int,
    private val onSave: (Int) -> Unit
) : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "AgeSheet"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_age, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvAgeValue = view.findViewById<TextView>(R.id.tvAgeValue)
        val ageSlider  = view.findViewById<Slider>(R.id.ageSlider)

        // Style age card background
        view.findViewById<View>(R.id.ageCard).background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 22f * resources.displayMetrics.density
                setColor(Color.parseColor("#FDF0F5"))
            }

        // Set initial value (clamped to valid range)
        val initial = currentAge.coerceIn(12, 55)
        ageSlider.value  = initial.toFloat()
        tvAgeValue.text  = initial.toString()

        // Live update number as slider moves
        ageSlider.addOnChangeListener { _, value, _ ->
            tvAgeValue.text = value.toInt().toString()
        }

        view.findViewById<View>(R.id.btnSheetSave).setOnClickListener {
            onSave(ageSlider.value.toInt())
            dismiss()
        }

        view.findViewById<View>(R.id.btnSheetCancel).setOnClickListener {
            dismiss()
        }
    }
}