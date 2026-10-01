package com.aivigil.periodtracker.insights

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
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

    private companion object { const val TAG = "InsightsFragment" }

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

    /**
     * Renders the cycle stat cards and phase guide, or an explicit empty state.
     *
     * Order-independent: safe to call from any observer, before or after the
     * others have emitted. Never leaves the layout's placeholder text on screen.
     */
    private fun bindStats() {
        if (_binding == null) return
        val pred = vm.prediction.value
        val s    = vm.settings.value

        if (pred == null || s == null) {
            // Empty state — em dashes, never fabricated numbers.
            binding.tvAvgCycleValue.text     = "—"
            binding.tvAvgPeriodValue.text    = "—"
            binding.tvLutealValue.text       = "—"
            binding.tvSymptomPeakValue.text  = "—"
            binding.tvPhaseGuideTitle.text   = "Log your first period"
            binding.tvPhaseDaysBadge.text    = "—"
            binding.tvPhaseGuideBody.text    =
                "Once you log a period, your cycle phases and insights will appear here."
            return
        }

        val day = CycleEngine.cycleDay(
            pred.lastPeriodStart, pred.cycleLength, vm.today.value ?: LocalDate.now()
        )

        binding.tvAvgCycleValue.text  = "${pred.cycleLength} Days"
        binding.tvAvgPeriodValue.text = "${s.periodDuration} Days"

        // FIX 2 — this formula was written out by hand in three separate places in
        // this file (here, bindPhaseGuide(), and the PDF export), and two of them
        // disagreed about their input: one used pred.cycleLength (adaptive) and the
        // PDF used settings.cycleLength (user-entered). For any user whose observed
        // cycle differed from her setting, the luteal figure on screen contradicted
        // the one in her exported report. All three now call CycleEngine.
        binding.tvLutealValue.text =
            "${CycleEngine.lutealPhaseLength(pred.cycleLength, s.periodDuration)} Days"

        bindPhaseGuide(
            day            = day,
            cycleLength    = pred.cycleLength,
            periodDuration = s.periodDuration
        )
    }

    private fun observeData() {

        // FIX 1 — the two observers below used to each return early if the OTHER
        // one's value had not arrived yet, and neither re-set the stat cards on
        // recovery. If `prediction` emitted before `settings`, tvAvgCycleValue /
        // tvAvgPeriodValue / tvLutealValue were never written at all and kept the
        // placeholder text hardcoded in fragment_insights.xml ("14 Days",
        // "Day 27") — showing a brand-new user invented numbers as her own cycle
        // data. Both observers now call one bind function that handles any arrival
        // order and renders an explicit empty state.
        vm.prediction.observe(viewLifecycleOwner) { bindStats() }
        vm.settings.observe(viewLifecycleOwner)   { bindStats() }
        vm.today.observe(viewLifecycleOwner)      { bindStats() }

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
            } else {
                binding.tvSymptomPeakValue.text = "—"
            }
        }
    }

    /**
     * FIX: tvSymptomPeakValue used to be reset to "Calculating…" by the
     * `prediction` observer on every emission, while the real value was written
     * only by the `allLogs` observer. Because `prediction` is derived from
     * `periodEntries`, logging any period re-fired it and overwrote the computed
     * value — and `allLogs` did not re-emit, so the label stayed on "Calculating…"
     * permanently. bindStats() no longer touches this field; only the allLogs
     * observer above owns it.
     */

    // ─────────────────────────────────────────────
    // PHASE GUIDE
    // ─────────────────────────────────────────────

    private fun bindPhaseGuide(day: Int, cycleLength: Int, periodDuration: Int) {
        val phase = CycleEngine.phase(day, cycleLength, periodDuration)

        // ✅ Derive day range labels from CycleEngine boundaries
        // so they are correct for all cycle lengths, not just 28-day cycles
        // Single source of truth — see CycleEngine.ovulationDay()
        val ovDay        = CycleEngine.ovulationDay(cycleLength, periodDuration)
        val fertileStart = CycleEngine.fertileStartDay(cycleLength, periodDuration)

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
            // FIX (P0 crash): the button used to be re-enabled from a
            // postDelayed(…, 2000) lambda that dereferenced `binding`. Detaching a
            // view does not drain messages already queued on it, so navigating
            // back or rotating within 2 seconds of tapping Download ran the lambda
            // after onDestroyView and threw NPE on the `binding` getter.
            // The button is now re-enabled in exportPdf()'s finally block, guarded
            // by `_binding?.`, which is also correct timing — it tracks the actual
            // export rather than an arbitrary 2-second delay.
            exportPdf()
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

        // FIX: was lifecycleScope (the FRAGMENT's scope, which outlives the view).
        // viewLifecycleOwner.lifecycleScope is cancelled with the view, so the
        // completion handler can never touch a destroyed binding or commit a
        // FragmentTransaction into a dead view hierarchy.
        // `pdf` is hoisted out of the try so the finally block can always close it.
        val pdf = android.graphics.pdf.PdfDocument()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
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
                // FIX: this used settings.cycleLength while the on-screen card used
                // pred.cycleLength, so the exported report could contradict the app.
                val predCycleLength = vm.prediction.value?.cycleLength ?: settings.cycleLength
                val lutealDays = CycleEngine.lutealPhaseLength(
                    predCycleLength, settings.periodDuration
                )

                canvas.drawText("Cycle length:    $predCycleLength days", 24f, y, bodyPaint); y += 14f
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
                    // FIX: this line claimed the data never leaves the device while
                    // the PDF was being written to shared Downloads, readable by any
                    // app with storage access. The file now stays in app-private
                    // storage, but this footer describes the REPORT the user is
                    // holding — which she may well email or print — so it must not
                    // promise confidentiality the app cannot enforce.
                    "Period Tracker • Contains personal health information — share with care",
                    24f, y + 24f, subPaint
                )
                pdf.finishPage(page)

                val fileName = "PeriodTracker_Report_${LocalDate.now()}.pdf"

                // ✅ openSheet defined once — used by both API branches
                suspend fun openSheet(uri: android.net.Uri) = withContext(Dispatchers.Main) {
                    // FIX: `isAdded` stays true for a BACKGROUNDED fragment, so it
                    // did not protect the show() below — backgrounding the app while
                    // the PDF was being written committed a FragmentTransaction after
                    // onSaveInstanceState and threw
                    // "Can not perform this action after onSaveInstanceState".
                    // Checking the view lifecycle state is the real guard.
                    if (!viewLifecycleOwner.lifecycle.currentState
                            .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                    ) return@withContext
                    if (parentFragmentManager.isStateSaved) return@withContext

                    val openPdfAction: () -> Unit = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        // FIX: opening the report sends the user to another app.
                        // On return, AppOpenAdManager fired a full-screen ad on top
                        // of her medical report. skipNextShow() existed for exactly
                        // this and was never called anywhere in the codebase.
                        (requireActivity().application as? com.aivigil.periodtracker.MainApplication)
                            ?.appOpenAdManager?.skipNextShow()
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

                // ── WHERE THE HEALTH REPORT IS WRITTEN ────────────
                //
                // FIX (P0 — privacy): this used to write to shared storage —
                // MediaStore.Downloads on API 29+, and
                // getExternalStoragePublicDirectory() below that. The PDF contains
                // the user's name, age, weight, listed health conditions and her
                // full flow / mood / symptom history. In shared Downloads it is
                // readable by any app holding media or storage permission, is
                // swept up by cloud-backup and file-manager apps, and persists
                // forever with no way for the user to find or delete it from
                // inside this app. The export screen itself printed
                // "Private & local • Data never leaves your device", which was
                // simply untrue.
                //
                // It now goes to the app's private cache directory and is shared
                // only through the existing FileProvider, which grants read access
                // to one URI, to one app, for as long as the user is viewing it.
                // Nothing is left in shared storage, and the legacy branch no
                // longer needs WRITE_EXTERNAL_STORAGE (which was declared with
                // maxSdkVersion=28 but never requested at runtime, so the old
                // API 26–28 path failed with EACCES on every attempt).
                val exportDir = File(requireContext().cacheDir, "reports").apply { mkdirs() }
                // Keep only the newest report so old copies of the user's health
                // data do not accumulate on disk.
                exportDir.listFiles()?.forEach { runCatching { it.delete() } }

                val file = File(exportDir, fileName)
                FileOutputStream(file).use { pdf.writeTo(it) }

                val uri = FileProvider.getUriForFile(
                    requireContext(), "${requireContext().packageName}.provider", file
                )
                openSheet(uri)

            } catch (e: Exception) {
                Log.e(TAG, "PDF export failed", e)
                withContext(Dispatchers.Main) {
                    val root = _binding?.root ?: return@withContext
                    com.google.android.material.snackbar.Snackbar.make(
                        root,
                        "Couldn't create the report. Please try again.",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show()
                }
            } finally {
                // FIX: pdf.close() was called on the success paths only, so any
                // failure mid-write leaked the PdfDocument's native pages.
                runCatching { pdf.close() }
                withContext(Dispatchers.Main) {
                    _binding?.btnDownloadReport?.let {
                        it.isEnabled = true
                        it.alpha = 1.0f
                    }
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