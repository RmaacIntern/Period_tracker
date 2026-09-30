package com.aivigil.periodtracker.calendar

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.aivigil.periodtracker.R
import java.time.LocalDate
import java.time.YearMonth

/**
 * Lightweight calendar used inside the "Last period start" bottom sheet.
 * Sunday-first, no cycle coloring — just selected (pink filled) and today (pink stroke).
 * Past-only: future dates are greyed out and non-tappable.
 */
class SheetCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var displayMonth: YearMonth = YearMonth.now()
        set(value) { field = value; invalidate() }
    var selectedDate: LocalDate = LocalDate.now()
        set(value) { field = value; invalidate() }
    var maxDate: LocalDate = LocalDate.now()
        set(value) { field = value; invalidate() }
    var onDateSelected: ((LocalDate) -> Unit)? = null

    // Brand pink — correct in both modes
    private val colorSelected = Color.parseColor("#EC4899")
    private val colorToday    = Color.parseColor("#EC4899")

    // ✅ FIX — use color resources so text adapts to dark mode
    private val colorNormal   get() = context.getColor(R.color.text_primary)
    private val colorDisabled get() = context.getColor(R.color.text_secondary)
    private val colorOther    get() = context.getColor(R.color.text_secondary)

    private val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var cellW = 0f
    private var cellH = 0f
    private val cells = Array(6) { Array(7) { LocalDate.now() } }

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (width * 0.85f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cellW = w / 7f
        cellH = h / 6f
    }

    override fun onDraw(canvas: Canvas) {
        val daysInMonth = displayMonth.lengthOfMonth()
        val first       = displayMonth.atDay(1)
        val offset      = first.dayOfWeek.value % 7
        val prevMonth   = displayMonth.minusMonths(1)
        val nextMonth   = displayMonth.plusMonths(1)

        for (row in 0 until 6) {
            for (col in 0 until 7) {
                val n = row * 7 + col - offset + 1
                val date = when {
                    n < 1           -> prevMonth.atDay(prevMonth.lengthOfMonth() + n)
                    n > daysInMonth -> nextMonth.atDay(n - daysInMonth)
                    else            -> displayMonth.atDay(n)
                }
                cells[row][col] = date

                val inMonth    = n in 1..daysInMonth
                val isFuture   = date.isAfter(maxDate)
                val isSelected = date == selectedDate
                val isToday    = date == LocalDate.now()

                val cx = col * cellW + cellW / 2f
                val cy = row * cellH + cellH / 2f
                val r  = minOf(cellW, cellH) * 0.40f

                textPaint.textSize = cellW * 0.28f

                when {
                    isSelected -> {
                        fillPaint.color = colorSelected
                        canvas.drawCircle(cx, cy, r, fillPaint)
                        textPaint.color    = Color.WHITE
                        textPaint.typeface = Typeface.DEFAULT_BOLD
                    }
                    isToday && !isSelected -> {
                        strokePaint.color  = colorToday
                        canvas.drawCircle(cx, cy, r, strokePaint)
                        textPaint.color    = colorToday
                        textPaint.typeface = Typeface.DEFAULT_BOLD
                    }
                    isFuture || !inMonth -> {
                        textPaint.color    = colorOther
                        textPaint.typeface = Typeface.DEFAULT
                    }
                    else -> {
                        textPaint.color    = colorNormal
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
            val col  = (e.x / cellW).toInt().coerceIn(0, 6)
            val row  = (e.y / cellH).toInt().coerceIn(0, 5)
            val date = cells[row][col]
            if (!date.isAfter(maxDate)) {
                selectedDate = date
                onDateSelected?.invoke(date)
            }
            return true
        }
        return true
    }
}