package com.aivigil.periodtracker.profile.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.aivigil.periodtracker.R
import com.aivigil.periodtracker.util.ThemeHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider

class WeightSheet(
    private val currentWeightKg: Float,
    private val onSave: (Float) -> Unit
) : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "WeightSheet"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_weight, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvValue = view.findViewById<TextView>(R.id.tvWeightValue)
        val slider  = view.findViewById<Slider>(R.id.weightSlider)

        // Card background
        view.findViewById<View>(R.id.weightCard).background =
            ThemeHelper.innerCardBg(requireContext(), 16f)

        val initial = currentWeightKg.coerceIn(35f, 140f)
        slider.value = initial
        tvValue.text = "%.1f kg".format(initial)

        slider.addOnChangeListener { _, value, _ ->
            tvValue.text = "%.1f kg".format(value)
        }

        view.findViewById<View>(R.id.btnSheetSave).setOnClickListener {
            onSave(slider.value)
            dismiss()
        }

        view.findViewById<View>(R.id.btnSheetCancel).setOnClickListener {
            dismiss()
        }
    }
}