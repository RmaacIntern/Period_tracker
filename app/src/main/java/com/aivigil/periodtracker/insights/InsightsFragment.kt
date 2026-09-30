package com.aivigil.periodtracker.insights

import android.content.ContentValues
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aivigil.periodtracker.R
import com.aivigil.periodtracker.history.PastLogHistoryFragment
import com.aivigil.periodtracker.databinding.FragmentInsightsBinding
import com.aivigil.periodtracker.domain.CycleEngine
import com.aivigil.periodtracker.data.entity.DailyLog
import com.aivigil.periodtracker.util.ThemeHelper
import com.aivigil.periodtracker.viewmodel.CycleViewModel
import com.aivigil.periodtracker.viewmodel.CycleViewModelFactory
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class InsightsFragment : Fragment() {

    private var _binding: FragmentInsightsBinding? = null
    private val binding get() = _binding!!

    private val vm: CycleViewModel by activityViewModels {
        CycleViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyBackgrounds()
        observeData()
        bindClickListeners()
    }

    // ─────────────────────────────────────────────
    // OBSERVE DATA
    // ─────────────────────────────────────────────

    private fun observeData() {

        // ✅ Use vm.prediction as single source of truth — not raw settings
        vm.prediction.observe(viewLifecycleOwner) { pred ->
            pred ?: return@observe
            val s = vm.settings.value ?: return@observe

            val day = CycleEngine.cycleDay(pred.lastPeriodStart, pred.cycleLength)

            binding.tvAvgCycleValue.text  = "${pred.cycleLength} Days"
            binding.tvAvgPeriodValue.text = "${s.periodDuration} Days"

            // ✅ Correct luteal formula matching CycleEngine.bestPrediction()
            val ovulationDay = (pred.cycleLength - 14).coerceAtLeast(s.periodDuration + 2)
            val lutealDays   = pred.cycleLength - ovulationDay
            binding.tvLutealValue.text = "$lutealDays Days"

            // Symptom peak is set by allLogs observer below
            binding.tvSymptomPeakValue.text = "Calculating…"

            bindPhaseGuide(
                day            = day,
                cycleLength    = pred.cycleLength,
                periodDuration = s.periodDuration
            )
        }

        // Re-trigger phase guide when settings arrive after prediction
        vm.settings.observe(viewLifecycleOwner) { s ->
            s ?: return@observe
            val pred = vm.prediction.value ?: return@observe
            val day  = CycleEngine.cycleDay(pred.lastPeriodStart, pred.cycleLength)
            bindPhaseGuide(day, pred.cycleLength, s.periodDuration)
        }

        vm.allLogs.observe(viewLifecycleOwner) { logs ->

            if (logs.isEmpty()) {
                binding.barCramping.progress = 0
                binding.barFatigue.progress  = 0
                binding.barHeadache.progress = 0
                binding.tvCrampingPct.text   = "0%"
                binding.tvFatiguePct.text    = "0%"
                binding.tvHeadachePct.text   = "0%"
                binding.tvDailyTrackingLabel.text = "• Daily tracking"
                binding.tvLastUpdatedLabel.text   = "No entries yet"
                binding.tvSymptomPeakValue.text   = "Not enough data"
                return@observe
            }

            // Deduplicate — keep latest entry per date
            val uniqueDateLogs = logs
                .groupBy { it.date }
                .mapValues { (_, entries) -> entries.maxByOrNull { it.entryNumber }!! }
                .values.toList()

            val total = uniqueDateLogs.size.toFloat()

            val crampingPercent = ((uniqueDateLogs.count { it.symptoms.contains("Cramps") }   / total) * 100).toInt()
            val fatiguePercent  = ((uniqueDateLogs.count { it.symptoms.contains("Fatigue") }  / total) * 100).toInt()
            val headachePercent = ((uniqueDateLogs.count { it.symptoms.contains("Headache") } / total) * 100).toInt()

            binding.barCramping.progress = crampingPercent
            binding.barFatigue.progress  = fatiguePercent
            binding.barHeadache.progress = headachePercent
            binding.tvCrampingPct.text   = "$crampingPercent%"
            binding.tvFatiguePct.text    = "$fatiguePercent%"
            binding.tvHeadachePct.text   = "$headachePercent%"

            val todayStr = LocalDate.now().toString()
            val todayLog = logs
                .filter { it.date == todayStr }
                .maxByOrNull { it.entryNumber }

            binding.tvDailyTrackingLabel.text = "• Daily tracking"
            binding.tvLastUpdatedLabel.text   =
                if (todayLog != null) "Updated today" else "Not updated today"

            // ✅ Symptom peak from real log data via CycleEngine.detectPmsOnsetDay()
            val pred = vm.prediction.value
            if (pred != null) {
                val logTriples = logs.mapNotNull { log ->
                    val date = runCatching { LocalDate.parse(log.date) }.getOrNull()
                        ?: return@mapNotNull null
                    val cycleDay = CycleEngine.cycleDay(pred.lastPeriodStart, pred.cycleLength, date)
                    Triple(date, log.symptoms, cycleDay)
                }
                val pmsDay = CycleEngine.detectPmsOnsetDay(logTriples)
                binding.tvSymptomPeakValue.text =
                    if (pmsDay != null) "Day $pmsDay" else "Not enough data"
            }
        }
    }

    // ─────────────────────────────────────────────
    // PHASE GUIDE
    // ─────────────────────────────────────────────

    private fun bindPhaseGuide(day: Int, cycleLength: Int, periodDuration: Int) {
        val phase = CycleEngine.phase(day, cycleLength, periodDuration)

        // ✅ Derive day range labels from CycleEngine boundaries
        // so they are correct for all cycle lengths, not just 28-day cycles
        val ovDay        = (cycleLength - 14).coerceAtLeast(periodDuration + 2)
        val fertileStart = (ovDay - 5).coerceAtLeast(periodDuration + 1)

        val result = when (phase) {
            CycleEngine.Phase.MENSTRUAL -> Triple(
                "Menstrual Phase Guide",
                "Days 1–$periodDuration",
                "Rest and nourish your body. Iron-rich foods and gentle movement can support recovery. Prioritize comfortable movement and good sleep."
            )
            CycleEngine.Phase.FOLLICULAR -> Triple(
                "Follicular Phase Guide",
                "Days ${periodDuration + 1}–${fertileStart - 1}",
                "Estrogen levels rise steadily toward ovulation. You may notice changes in energy, focus, and mood. Strength training and balanced meals can support your routine."
            )
            CycleEngine.Phase.OVULATION -> Triple(
                "Ovulation Phase Guide",
                "Days $fertileStart–$ovDay",
                "This phase can bring changes in energy, mood, and physical sensations. Continue listening to your body and maintain your usual healthy routine."
            )
            CycleEngine.Phase.LUTEAL_EARLY -> Triple(
                "Luteal Phase Guide",
                "Days ${ovDay + 1}–${ovDay + 3}",
                "Progesterone rises and then falls. You may notice mood changes, cravings, or lower energy. Focus on balanced meals, hydration, moderate activity, and rest."
            )
            CycleEngine.Phase.LUTEAL_LATE -> Triple(
                "Pre-Menstrual Phase Guide",
                "Days ${ovDay + 4}–$cycleLength",
                "PMS symptoms may appear. Take it easier and prioritize self-care. Cravings and mood shifts are normal — be kind to yourself."
            )
        }

        binding.tvPhaseGuideTitle.text = result.first
        binding.tvPhaseDaysBadge.text  = result.second
        binding.tvPhaseGuideBody.text  = result.third
    }

    // ─────────────────────────────────────────────
    // CLICK LISTENERS
    // ─────────────────────────────────────────────

    private fun bindClickListeners() {
        binding.cycleHistoryCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, PastLogHistoryFragment.newInstance())
                .addToBackStack("pastLogHistory")
                .commit()
        }

        binding.btnDownloadReport.setOnClickListener {
            binding.btnDownloadReport.isEnabled = false
            binding.btnDownloadReport.alpha = 0.5f
            exportPdf()
            binding.btnDownloadReport.postDelayed({
                binding.btnDownloadReport.isEnabled = true
                binding.btnDownloadReport.alpha = 1.0f
            }, 2000)
        }
    }

    // ─────────────────────────────────────────────
    // PDF EXPORT
    // ─────────────────────────────────────────────

    private fun exportPdf() {
        val settings = vm.settings.value
        val logs     = vm.allLogs.value ?: emptyList()

        if (settings == null) {
            Toast.makeText(requireContext(), "No data to export yet.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(requireContext(), "Generating PDF report...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pdf = android.graphics.pdf.PdfDocument()
                var pageNum  = 1
                var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                var page     = pdf.startPage(pageInfo)
                var canvas   = page.canvas

                val pink      = Color.parseColor("#EC4899")
                val dark      = Color.parseColor("#2D1B33")
                val grey      = Color.parseColor("#8A7A8F")
                val linePaint = Paint().apply {
                    color = Color.parseColor("#F0E4F5"); strokeWidth = 1f
                }
                val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = pink; textSize = 13f; isFakeBoldText = true
                }
                val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dark; textSize = 10f
                }
                val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = grey; textSize = 9f
                }

                val dateStr = LocalDate.now().format(
                    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())
                )

                // Header
                canvas.drawRect(0f, 0f, 595f, 65f, Paint().apply { color = pink })
                canvas.drawText("Period Tracker — Cycle & Daily Logs Report", 24f, 36f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE; textSize = 17f; isFakeBoldText = true
                    })
                canvas.drawText("Generated $dateStr", 24f, 54f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#FFD6EA"); textSize = 10f
                    })

                var y = 88f

                // Profile
                canvas.drawText("PROFILE", 24f, y, headingPaint); y += 16f
                canvas.drawLine(24f, y, 571f, y, linePaint); y += 10f

                val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
                canvas.drawText("Name:  ${settings.userName}", 24f, y, bodyPaint); y += 14f
                canvas.drawText("Age:   ${settings.age} yrs     Height: ${settings.heightCm} cm     Weight: ${settings.weightKg.toInt()} kg",
                    24f, y, bodyPaint); y += 14f
                canvas.drawText("Member since:  ${settings.memberSince}", 24f, y, bodyPaint); y += 20f

                // Cycle Stats
                canvas.drawText("CYCLE STATISTICS", 24f, y, headingPaint); y += 16f
                canvas.drawLine(24f, y, 571f, y, linePaint); y += 10f

                // ✅ Correct luteal formula in PDF too
                val ovulationDay = (settings.cycleLength - 14).coerceAtLeast(settings.periodDuration + 2)
                val lutealDays   = settings.cycleLength - ovulationDay

                canvas.drawText("Cycle length:    ${settings.cycleLength} days", 24f, y, bodyPaint); y += 14f
                canvas.drawText("Period duration: ${settings.periodDuration} days", 24f, y, bodyPaint); y += 14f
                canvas.drawText("Est. luteal phase:       $lutealDays days", 24f, y, bodyPaint); y += 14f
                canvas.drawText("Last period start:       ${
                    try { LocalDate.parse(settings.lastPeriodStart).format(fmt) }
                    catch (_: Exception) { settings.lastPeriodStart }
                }", 24f, y, bodyPaint); y += 14f
                if (settings.conditions.isNotBlank()) {
                    canvas.drawText("Conditions noted:        ${settings.conditions}", 24f, y, bodyPaint); y += 14f
                }
                y += 8f

                // Symptom Summary
                val uniqueLogs = logs
                    .groupBy { it.date }
                    .mapValues { (_, e) -> e.maxByOrNull { it.entryNumber }!! }
                    .values.toList()

                if (uniqueLogs.isNotEmpty()) {
                    val total      = uniqueLogs.size.toFloat()
                    val crampPct   = ((uniqueLogs.count { it.symptoms.contains("Cramps") }   / total) * 100).toInt()
                    val fatiguePct = ((uniqueLogs.count { it.symptoms.contains("Fatigue") }  / total) * 100).toInt()
                    val headPct    = ((uniqueLogs.count { it.symptoms.contains("Headache") } / total) * 100).toInt()
                    val nausePct   = ((uniqueLogs.count { it.symptoms.contains("Nausea") }   / total) * 100).toInt()
                    val bloatPct   = ((uniqueLogs.count { it.symptoms.contains("Bloating") } / total) * 100).toInt()

                    canvas.drawText("SYMPTOM SUMMARY  (${uniqueLogs.size} days tracked)", 24f, y, headingPaint); y += 16f
                    canvas.drawLine(24f, y, 571f, y, linePaint); y += 10f
                    canvas.drawText("Cramps:   $crampPct%",   24f,  y, bodyPaint)
                    canvas.drawText("Fatigue:  $fatiguePct%", 180f, y, bodyPaint)
                    canvas.drawText("Headache: $headPct%",    330f, y, bodyPaint); y += 14f
                    canvas.drawText("Nausea:   $nausePct%",   24f,  y, bodyPaint)
                    canvas.drawText("Bloating: $bloatPct%",   180f, y, bodyPaint); y += 20f
                }

                // Daily Log Table
                val sortedLogs = logs.sortedWith(
                    compareByDescending<DailyLog> { it.date }.thenByDescending { it.loggedAt }
                )

                if (sortedLogs.isNotEmpty()) {
                    canvas.drawText("DAILY LOG HISTORY  (${sortedLogs.size} total entries)", 24f, y, headingPaint); y += 16f
                    canvas.drawLine(24f, y, 571f, y, linePaint); y += 10f

                    fun drawTableHeader(c: Canvas, currentY: Float) {
                        c.drawText("Date",     24f,  currentY, subPaint)
                        c.drawText("Time",     100f, currentY, subPaint)
                        c.drawText("Flow",     170f, currentY, subPaint)
                        c.drawText("Mood",     235f, currentY, subPaint)
                        c.drawText("Symptoms", 335f, currentY, subPaint)
                        c.drawText("BBT",      510f, currentY, subPaint)
                        c.drawLine(24f, currentY + 4f, 571f, currentY + 4f, linePaint)
                    }

                    drawTableHeader(canvas, y); y += 16f

                    val rowFmt  = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
                    val timeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

                    sortedLogs.forEach { log ->
                        if (y > 780f) {
                            pdf.finishPage(page)
                            pageNum++
                            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                            page   = pdf.startPage(pageInfo)
                            canvas = page.canvas
                            canvas.drawRect(0f, 0f, 595f, 36f, Paint().apply { color = pink })
                            canvas.drawText("Period Tracker Report — Page $pageNum", 24f, 24f,
                                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = Color.WHITE; textSize = 13f; isFakeBoldText = true
                                })
                            y = 52f
                            drawTableHeader(canvas, y); y += 16f
                        }

                        val dateLabel = try { LocalDate.parse(log.date).format(rowFmt) }
                        catch (_: Exception) { log.date }
                        val timeLabel = if (log.loggedAt > 0L) {
                            java.time.Instant.ofEpochMilli(log.loggedAt)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalTime().format(timeFmt)
                        } else "Log #${log.entryNumber}"

                        canvas.drawText(log.flow.takeIf { it.isNotEmpty() } ?: "—", 170f, y, bodyPaint)
                        canvas.drawText(dateLabel, 24f,  y, bodyPaint)
                        canvas.drawText(timeLabel, 100f, y, bodyPaint)
                        canvas.drawText(log.moods.split(",").firstOrNull { it.isNotBlank() } ?: "—", 235f, y, bodyPaint)
                        canvas.drawText(log.symptoms.split(",").firstOrNull { it.isNotBlank() } ?: "—", 335f, y, bodyPaint)
                        canvas.drawText(log.basalTemp?.let { "%.1f°".format(it) } ?: "—", 510f, y, bodyPaint)
                        y += 15f
                    }
                }

                // Footer
                if (y > 790f) {
                    pdf.finishPage(page)
                    pageNum++
                    pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                    page   = pdf.startPage(pageInfo)
                    canvas = page.canvas
                    y = 40f
                }
                canvas.drawLine(24f, y + 10f, 571f, y + 10f, linePaint)
                canvas.drawText(
                    "Period Tracker • Private & local • Data never leaves your device",
                    24f, y + 24f, subPaint
                )
                pdf.finishPage(page)

                val fileName = "PeriodTracker_Report_${LocalDate.now()}.pdf"

                // ✅ openSheet defined once — used by both API branches
                suspend fun openSheet(uri: android.net.Uri) = withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    val openPdfAction: () -> Unit = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { startActivity(intent) }
                        catch (_: Exception) {
                            try { startActivity(Intent.createChooser(intent, "Open PDF with...")) }
                            catch (_: Exception) {
                                Toast.makeText(requireContext(), "No PDF viewer app found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    com.aivigil.periodtracker.profile.sheets.PdfReadySheet(
                        fileName = fileName,
                        onOpen   = openPdfAction
                    ).show(parentFragmentManager, com.aivigil.periodtracker.profile.sheets.PdfReadySheet.TAG)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = requireContext().contentResolver
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                    requireContext().contentResolver.openOutputStream(uri)!!.use { pdf.writeTo(it) }
                    pdf.close()
                    openSheet(uri)
                } else {
                    val dir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { pdf.writeTo(it) }
                    pdf.close()
                    val uri = FileProvider.getUriForFile(
                        requireContext(), "${requireContext().packageName}.provider", file
                    )
                    openSheet(uri)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) Toast.makeText(requireContext(),
                        "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // BACKGROUNDS
    // ─────────────────────────────────────────────

    private fun applyBackgrounds() {
        val ctx = requireContext()
        listOf(binding.cardAvgCycle, binding.cardAvgPeriod,
            binding.cardLuteal,  binding.cardSymptomPeak)
            .forEach { it.background = ThemeHelper.cardBg(ctx, 22f) }

        binding.iconAvgCycle.background  = ThemeHelper.iconCirclePink(ctx)
        binding.iconAvgPeriod.background = ThemeHelper.iconCirclePurple(ctx)
        binding.iconLuteal.background    = ThemeHelper.iconCirclePurple(ctx)
        binding.iconSymptom.background   = ThemeHelper.iconCirclePink(ctx)

        binding.cycleHistoryCard.background  = ThemeHelper.cardBg(ctx, 22f)
        binding.cycleHistoryCard.isClickable = true
        binding.cycleHistoryCard.isFocusable = true

        binding.tvCycleHistoryIcon.background = ThemeHelper.iconCirclePink(ctx)
        binding.tvHistoryArrow.background     = ThemeHelper.iconCirclePink(ctx)

        binding.phaseGuideCard.background   = ThemeHelper.cardBg(ctx, 22f)
        binding.tvPhaseDaysBadge.background = ThemeHelper.innerCardBg(ctx, 50f)

        binding.cyclePatternsCard.background = ThemeHelper.cardBg(ctx, 22f)

        binding.exportCard.background = ThemeHelper.cardBg(ctx, 22f)
        binding.iconExport.background = ThemeHelper.iconCirclePink(ctx)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}