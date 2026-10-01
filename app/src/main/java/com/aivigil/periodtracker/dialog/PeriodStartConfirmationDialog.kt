package com.aivigil.periodtracker.dialog

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.aivigil.periodtracker.databinding.DialogPeriodStartConfirmationBinding

enum class PeriodStartResult {
    PERIOD_STARTED,
    NOT_PERIOD_STARTED
}

/**
 * Custom styled period-start confirmation dialog.
 * Matches the design: drop icon + title + message + red CTA + grey secondary.
 * Does NOT touch Room, repositories, ViewModels or cycle calculations.
 * The caller decides what to do with the result via [onResult].
 *
 * [onDismiss] is called when the user dismisses without choosing
 * (back press, tap outside) so the caller can re-enable save buttons.
 */
class PeriodStartConfirmationDialog : DialogFragment() {

    /**
     * Kept for source compatibility with any caller still setting these directly,
     * but the results below are ALSO delivered through the Fragment Result API.
     *
     * FIX (P0 — silent data loss on rotation): these lambdas were the only delivery
     * channel. A lambda cannot survive Fragment recreation, so rotating the device
     * while this dialog was open produced a restored dialog with `onResult == null`
     * — tapping "Yes, period started" then dismissed the dialog and saved
     * absolutely nothing, with no error and no indication anything had gone wrong.
     * setFragmentResult survives recreation and process death.
     */
    var onResult: ((PeriodStartResult) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    // ✅ Guard against double-tap / rapid double-click
    private var isHandled = false

    private var _binding: DialogPeriodStartConfirmationBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPeriodStartConfirmationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Yes — period started
        binding.btnYesPeriodStarted.setOnClickListener {
            deliver(PeriodStartResult.PERIOD_STARTED)
        }

        // No — just bleeding
        binding.btnNoJustBleeding.setOnClickListener {
            deliver(PeriodStartResult.NOT_PERIOD_STARTED)
        }
    }

    private fun deliver(result: PeriodStartResult) {
        if (isHandled) return
        isHandled = true
        val callback = onResult
        // Survives rotation and process death, unlike the lambda.
        setFragmentResult(
            REQUEST_KEY,
            androidx.core.os.bundleOf(RESULT_KEY to result.name)
        )
        dismiss()
        callback?.invoke(result)
    }

    override fun onStart() {
        // Apply transparent background & layout bounds BEFORE super.onStart()
        // so WindowManager renders the dialog correctly on frame 1 without flickering
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        super.onStart()
    }

    // ✅ NEW — fires when the user dismisses without choosing (back press / tap outside)
    // isHandled is false in that case so onDismiss is only called for true dismissals,
    // never after Yes/No which already dismiss the dialog themselves.
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        if (!isHandled) {
            isHandled = true
            setFragmentResult(
                REQUEST_KEY,
                androidx.core.os.bundleOf(RESULT_KEY to RESULT_CANCELLED)
            )
            onDismiss?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PeriodStartConfirmationDialog"

        const val REQUEST_KEY     = "period_start_confirmation"
        const val RESULT_KEY      = "result"
        const val RESULT_CANCELLED = "CANCELLED"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            onResult: (PeriodStartResult) -> Unit,
            onDismiss: () -> Unit = {}
        ) {
            // Prevent showing duplicate dialogs
            if (fragmentManager.findFragmentByTag(TAG) != null) return

            PeriodStartConfirmationDialog().apply {
                this.onResult  = onResult
                this.onDismiss = onDismiss
            }.show(fragmentManager, TAG)
        }

        /**
         * Registers a rotation-safe listener. Call this from the host fragment's
         * onCreate/onViewCreated — it is re-delivered after recreation, so the
         * user's answer is never dropped.
         */
        fun listen(
            fragment: androidx.fragment.app.Fragment,
            onResult: (PeriodStartResult?) -> Unit
        ) {
            fragment.parentFragmentManager.setFragmentResultListener(
                REQUEST_KEY, fragment.viewLifecycleOwner
            ) { _, bundle ->
                val raw = bundle.getString(RESULT_KEY)
                onResult(
                    PeriodStartResult.entries.firstOrNull { it.name == raw }
                )
            }
        }
    }
}