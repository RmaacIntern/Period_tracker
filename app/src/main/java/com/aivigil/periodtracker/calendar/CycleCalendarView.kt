package com.aivigil.periodtracker.calendar

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.aivigil.periodtracker.R
import java.time.LocalDate
import java.time.YearMonth

class CycleCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var displayMonth: YearMonth = YearMonth.now()
        set(value) { field = value; invalidate() }
    var selectedDate: LocalDate = LocalDate.now()
        set(value) { field = value; invalidate() }
    var periodDays: Set<LocalDate> = emptySet()
        set(value) { field = value; invalidate() }
    var fertileWindowDays: Set<LocalDate> = emptySet()
        set(value) { field = value; invalidate() }
    var ovulationDay: LocalDate? = null
        set(value) { field = value; invalidate() }
    var predictedPeriodDays: Set<LocalDate> = emptySet()
        set(value) { field = value; invalidate() }
    var onDateSelected: ((LocalDate) -> Unit)? = null

    /**
     * Days the user has ACTUALLY recorded a period on, as opposed to days the app
     * filled in from the expected duration. Recorded days get a solid fill;
     * inferred days get a lighter one, so a prediction is never presented as a
     * fact the user entered herself.
     */
    var confirmedPeriodDays: Set<LocalDate> = emptySet()
        set(value) { field = value; invalidate() }

    init {
        // See onTouchEvent — without this, ACTION_DOWN is rejected and no tap
        // ever reaches this view.
        isClickable = true
        isFocusable = true
    }

    // ── Semantic medical colors — kept hardcoded (correct in both modes) ──
    private val colorPeriod    = Color.parseColor("#C2185B")
    private val colorOvul      = Color.parseColor("#E66A28")
    // Pale fills + their text flip for dark mode (values-night/colors.xml)
    private val colorFertile   get() = context.getColor(R.color.cycle_fertile_bg)
    private val colorFertileTx get() = context.getColor(R.color.cycle_fertile)
    private val colorPred      get() = context.getColor(R.color.cycle_predicted)
    private val colorPredTx    get() = context.getColor(R.color.cycle_predicted_text)
    private val colorToday     = Color.parseColor("#EC4899")
    private val colorSelected  get() = context.getColor(R.color.text_primary)

    /** Dash pattern marking a day as predicted rather than recorded. */
    private val predictedDash = DashPathEffect(floatArrayOf(6f, 5f), 0f)

    /**
     * Today's date, resolved once per onDraw pass.
     * FIX: LocalDate.now() was called inside the per-cell loop — up to 42
     * allocations and 42 clock reads per frame while scrolling months.
     */
    private var today: LocalDate = LocalDate.now()

    // ✅ FIX — text colors use color resources so they adapt to dark mode
    private val colorNormal get() = context.getColor(R.color.text_primary)
    private val colorOther  get() = context.getColor(R.color.text_secondary)

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val weekdays = listOf("M","T","W","T","F","S","S")
    private var cellW = 0f
    private var cellH = 0f
    private var hdrH  = 0f
    private val cells = Array(6) { Array(7) { LocalDate.now() } }

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (width * 1.08f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cellW = w / 7f
        hdrH  = cellW * 0.55f
        cellH = (h - hdrH) / 6f
    }

    override fun onDraw(canvas: Canvas) {
        today = LocalDate.now()   // resolved once per frame, not once per cell
        val daysInMonth = displayMonth.lengthOfMonth()
        val first       = displayMonth.atDay(1)
        val offset      = (first.dayOfWeek.value - 1) % 7
        val prevMonth   = displayMonth.minusMonths(1)
        val nextMonth   = displayMonth.plusMonths(1)

        textPaint.textSize = cellW * 0.27f
        textPaint.color    = colorOther
        textPaint.typeface = Typeface.DEFAULT
        weekdays.forEachIndexed { col, d ->
            canvas.drawText(d, col * cellW + cellW / 2f, hdrH * 0.72f, textPaint)
        }

        for (row in 0 until 6) {
            for (col in 0 until 7) {
                val n = row * 7 + col - offset + 1
                val date = when {
                    n < 1           -> prevMonth.atDay(prevMonth.lengthOfMonth() + n)
                    n > daysInMonth -> nextMonth.atDay(n - daysInMonth)
                    else            -> displayMonth.atDay(n)
                }
                cells[row][col] = date

                val inMonth = n in 1..daysInMonth
                val cx = col * cellW + cellW / 2f
                val cy = hdrH + row * cellH + cellH / 2f
                val r  = minOf(cellW, cellH) * 0.38f

                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = cellW * 0.29f

                // ── Day fill ──────────────────────────────────────
                //
                // FIX 1: a PREDICTED period day is now drawn as a dashed ring
                // rather than a filled circle, and a day the app merely inferred
                // from the expected duration is drawn lighter than one the user
                // actually recorded. Previously every period day — recorded,
                // inferred, or purely predicted — used the identical solid
                // #C2185B fill, so the calendar presented predictions as though
                // they were the user's own recorded history.
                //
                // FIX 2: prediction is now checked BEFORE the fertile window. A
                // predicted period day that fell inside a fertile window used to
                // render as fertile, hiding the period prediction entirely.
                when {
                    date in periodDays -> {
                        val recorded = confirmedPeriodDays.isEmpty() ||
                                date in confirmedPeriodDays
                        circlePaint.color = colorPeriod
                        circlePaint.alpha = if (recorded) 255 else 110
                        canvas.drawCircle(cx, cy, r, circlePaint)
                        circlePaint.alpha = 255
                        textPaint.color = if (recorded) Color.WHITE else colorPeriod
                    }
                    date == ovulationDay -> {
                        circlePaint.color = colorOvul
                        canvas.drawCircle(cx, cy, r, circlePaint)
                        textPaint.color = Color.WHITE
                    }
                    date in predictedPeriodDays -> {
                        // Dashed outline = "expected, not recorded".
                        strokePaint.color = colorPeriod
                        strokePaint.pathEffect = predictedDash
                        canvas.drawCircle(cx, cy, r, strokePaint)
                        strokePaint.pathEffect = null
                        textPaint.color = colorPredTx
                    }
                    date in fertileWindowDays -> {
                        circlePaint.color = colorFertile
                        canvas.drawCircle(cx, cy, r, circlePaint)
                        textPaint.color = colorFertileTx
                    }
                    else -> {
                        textPaint.color    = if (inMonth) colorNormal else colorOther
                        textPaint.typeface = Typeface.DEFAULT
                    }
                }

                // ── Today / selected rings, drawn ON TOP ──────────
                //
                // FIX 3: these used to be `when` branches after the colour
                // branches, so selecting a period / fertile / ovulation /
                // predicted day produced no visible change at all — the user had
                // no way to tell which day she had tapped. They also used the
                // same colour and radius as each other, making "today" and
                // "selected" indistinguishable. Now both draw over any fill, at
                // different radii, and selection is the stronger of the two.
                if (date == today) {
                    strokePaint.color = colorToday
                    strokePaint.strokeWidth = 2.2f
                    canvas.drawCircle(cx, cy, r, strokePaint)
                }
                if (date == selectedDate) {
                    strokePaint.color = colorSelected
                    strokePaint.strokeWidth = 3.5f
                    canvas.drawCircle(cx, cy, r * 1.16f, strokePaint)
                    strokePaint.strokeWidth = 2.2f
                }

                val label = when {
                    n < 1           -> (prevMonth.lengthOfMonth() + n).toString()
                    n > daysInMonth -> (n - daysInMonth).toString()
                    else            -> n.toString()
                }
                canvas.drawText(label, cx, cy + textPaint.textSize * 0.38f, textPaint)
            }
        }
    }

    /**
     * FIX (P0 — day taps did nothing at all):
     *
     * This view never set isClickable, and the layout declared no
     * android:clickable. View.onTouchEvent() returns FALSE for a non-clickable
     * view, so ACTION_DOWN was rejected; Android then never treats the view as
     * the touch target for that gesture, and ACTION_UP is never delivered here.
     * The result was that tapping any day in the calendar was a no-op —
     * onDateSelected never fired and the "Selected Day" card was permanently
     * stuck on today.
     *
     * Claiming ACTION_DOWN (and setting isClickable in init) makes the gesture
     * arrive. Bounds are also checked properly now instead of coercing an
     * out-of-range row into row 5, which used to select an unrelated date when
     * the user tapped below the grid.
     */
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> return true

            MotionEvent.ACTION_UP -> {
                if (cellW <= 0f || cellH <= 0f) return true
                val col = (e.x / cellW).toInt()
                val row = ((e.y - hdrH) / cellH).toInt()
                // Ignore taps on the weekday header or outside the grid rather
                // than snapping them onto the nearest cell.
                if (col !in 0..6 || row !in 0..5) return true

                val tapped = cells[row][col]
                selectedDate = tapped
                onDateSelected?.invoke(tapped)
                // Accessibility: announce the change for screen-reader users.
                contentDescription = tapped.toString()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}