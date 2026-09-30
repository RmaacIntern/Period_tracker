package com.aivigil.periodtracker.dialog

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
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

    var onResult: ((PeriodStartResult) -> Unit)? = null

    // ✅ NEW — called when user dismisses without tapping Yes or No
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
            if (isHandled) return@setOnClickListener
            isHandled = true
            val callback = onResult
            dismiss()
            callback?.invoke(PeriodStartResult.PERIOD_STARTED)
        }

        // No — just bleeding
        binding.btnNoJustBleeding.setOnClickListener {
            if (isHandled) return@setOnClickListener
            isHandled = true
            val callback = onResult
            dismiss()
            callback?.invoke(PeriodStartResult.NOT_PERIOD_STARTED)
        }
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
            onDismiss?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PeriodStartConfirmationDialog"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            onResult: (PeriodStartResult) -> Unit,
            // ✅ NEW — optional, defaults to no-op so existing callers don't break
            onDismiss: () -> Unit = {}
        ) {
            // Prevent showing duplicate dialogs
            if (fragmentManager.findFragmentByTag(TAG) != null) return

            PeriodStartConfirmationDialog().apply {
                this.onResult  = onResult
                this.onDismiss = onDismiss  // ✅ wire it in
            }.show(fragmentManager, TAG)
        }
    }
}