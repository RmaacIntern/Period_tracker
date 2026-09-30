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

class HeightSheet(
    private val currentHeightCm: Int,
    private val onSave: (Int) -> Unit
) : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "HeightSheet"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_height, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvValue     = view.findViewById<TextView>(R.id.tvHeightValue)
        val slider      = view.findViewById<Slider>(R.id.heightSlider)

        // Card background
        view.findViewById<View>(R.id.heightCard).background =
            ThemeHelper.innerCardBg(requireContext(), 16f)

        val initial = currentHeightCm.coerceIn(130, 210)
        slider.value  = initial.toFloat()
        tvValue.text  = "$initial cm"

        slider.addOnChangeListener { _, value, _ ->
            tvValue.text = "${value.toInt()} cm"
        }

        view.findViewById<View>(R.id.btnSheetSave).setOnClickListener {
            onSave(slider.value.toInt())
            dismiss()
        }

        view.findViewById<View>(R.id.btnSheetCancel).setOnClickListener {
            dismiss()
        }
    }
}