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

    // ── Semantic medical colors — kept hardcoded (correct in both modes) ──
    private val colorPeriod    = Color.parseColor("#C2185B")
    private val colorOvul      = Color.parseColor("#E66A28")
    // Pale fills + their text flip for dark mode (values-night/colors.xml)
    private val colorFertile   get() = context.getColor(R.color.cycle_fertile_bg)
    private val colorFertileTx get() = context.getColor(R.color.cycle_fertile)
    private val colorPred      get() = context.getColor(R.color.cycle_predicted)
    private val colorPredTx    get() = context.getColor(R.color.cycle_predicted_text)
    private val colorToday     = Color.parseColor("#EC4899")

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

                when {
                    date in periodDays -> {
                        circlePaint.color = colorPeriod
                        canvas.drawCircle(cx, cy, r, circlePaint)
                        textPaint.color = Color.WHITE
                    }
                    date == ovulationDay -> {
                        circlePaint.color = colorOvul
                        canvas.drawCircle(cx, cy, r, circlePaint)
                        textPaint.color = Color.WHITE
                    }
                    date in fertileWindowDays -> {
                        circlePaint.color = colorFertile
                        canvas.drawCircle(cx, cy, r, circlePaint)
                        textPaint.color = colorFertileTx
                    }
                    date in predictedPeriodDays -> {
                        circlePaint.color = colorPred
                        canvas.drawCircle(cx, cy, r * 0.88f, circlePaint)
                        textPaint.color = colorPredTx
                    }
                    date == LocalDate.now() -> {
                        strokePaint.color = colorToday
                        canvas.drawCircle(cx, cy, r, strokePaint)
                        textPaint.color = colorToday
                    }
                    date == selectedDate -> {
                        strokePaint.color = colorToday
                        canvas.drawCircle(cx, cy, r, strokePaint)
                        textPaint.color = colorToday
                    }
                    else -> {
                        textPaint.color    = if (inMonth) colorNormal else colorOther
                        textPaint.typeface = Typeface.DEFAULT
                    }
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

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            val col = (e.x / cellW).toInt().coerceIn(0, 6)
            val ri  = ((e.y - hdrH) / cellH).toInt()
            if (ri < 0) return true
            val row = ri.coerceIn(0, 5)
            selectedDate = cells[row][col]
            onDateSelected?.invoke(cells[row][col])
            return true
        }
        return super.onTouchEvent(e)
    }
}