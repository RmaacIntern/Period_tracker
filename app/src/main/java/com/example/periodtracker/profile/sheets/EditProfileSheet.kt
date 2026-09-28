package com.example.periodtracker.profile.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.periodtracker.databinding.BottomSheetEditProfileBinding

class EditProfileSheet(
    private val currentName: String,
    private val currentAge: Int,
    private val currentHeightCm: Int,
    private val currentWeightKg: Float,
    private val onSave: (name: String, age: Int, heightCm: Int, weightKg: Float) -> Unit
) : BottomSheetDialogFragment() {

    private var _b: BottomSheetEditProfileBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = BottomSheetEditProfileBinding.inflate(inflater, container, false)
        .also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Pre-fill with current values
        b.etEditName.setText(currentName)
        b.etEditAge.setText(currentAge.toString())
        b.etEditHeight.setText(currentHeightCm.toString())
        b.etEditWeight.setText(currentWeightKg.toInt().toString())

        // Focus name and show keyboard
        b.etEditName.requestFocus()
        dialog?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        b.btnSaveProfile.setOnClickListener {
            val name   = b.etEditName.text.toString().trim()
            val age    = b.etEditAge.text.toString().toIntOrNull() ?: currentAge
            val height = b.etEditHeight.text.toString().toIntOrNull() ?: currentHeightCm
            val weight = b.etEditWeight.text.toString().toFloatOrNull() ?: currentWeightKg

            if (name.isEmpty()) {
                b.etEditName.error = "Name required"
                return@setOnClickListener
            }
            onSave(name, age, height, weight)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        const val TAG = "EditProfileSheet"
    }

//    private fun showEditProfileSheet() {
//        val s = vm.settings.value ?: return
//        EditProfileSheet(
//            currentName     = s.userName,
//            currentAge      = s.age,
//            currentHeightCm = s.heightCm,
//            currentWeightKg = s.weightKg
//        ) { name, age, heightCm, weightKg ->
//            vm.updateProfile(name, age, heightCm, weightKg)
//        }.show(childFragmentManager, EditProfileSheet.TAG)
//    }
}