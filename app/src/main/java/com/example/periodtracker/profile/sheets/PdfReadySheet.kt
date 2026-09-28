package com.example.periodtracker.profile.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.periodtracker.R

class PdfReadySheet(
    private val fileName: String,
    private val onOpen: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_pdf_ready, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvPdfPath).text =
            "Report saved to Downloads/\n$fileName"

        view.findViewById<View>(R.id.btnOpenPdf).setOnClickListener {
            dismiss()
            onOpen()
        }

        view.findViewById<View>(R.id.btnClosePdf).setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "PdfReadySheet"
    }
}