package com.aivigil.periodtracker.insights

import com.aivigil.periodtracker.R

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View


class CycleBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class CycleBar(
        val month: String,
        val days: Int,
        val isCurrent: Boolean = false,
        val currentDay: Int? = null
    )


    var bars: List<CycleBar> = emptyList()
        set(value) { field = value; invalidate() }

    private val barPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val labelPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT
    }
    private val avgLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        color = context.getColor(R.color.text_secondary)
    }
    private val avgTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT; typeface = Typeface.DEFAULT_BOLD
        color = context.getColor(R.color.text_primary)
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = context.getColor(R.color.text_secondary)
    }

    private val rectF = RectF()

    override fun onDraw(canvas: Canvas) {
        if (bars.isEmpty()) {
            emptyPaint.textSize = 13f * resources.displayMetrics.density * 0.7f
            canvas.drawText(
                "Log your first period to see cycle history",
                width / 2f, height / 2f, emptyPaint
            )
            return
        }

        val w         = width.toFloat()
        val h         = height.toFloat()
        val topPad    = 28f
        val bottomPad = 24f
        val chartH    = h - topPad - bottomPad
        val maxDays   = bars.maxOf { it.days }.coerceAtLeast(30).toFloat()
        val avgDays   = bars.map { it.days }.average().toInt()

        val totalBars = bars.size
        val barWidth  = (w * 0.75f) / totalBars
        val barGap    = (w * 0.25f) / (totalBars + 1)
        val cornerR   = barWidth * 0.3f

        textPaint.textSize    = 11f * resources.displayMetrics.density * 0.7f
        labelPaint.textSize   = 10f * resources.displayMetrics.density * 0.7f
        avgTextPaint.textSize = 10f * resources.displayMetrics.density * 0.7f

        // Draw avg line
        val avgY = topPad + chartH * (1f - avgDays / maxDays)
        canvas.drawLine(0f, avgY, w * 0.82f, avgY, avgLinePaint)
        canvas.drawText("Avg: ${avgDays}d", w * 0.83f, avgY + 4f, avgTextPaint)

        bars.forEachIndexed { i, bar ->
            val barH   = chartH * (bar.days / maxDays)
            val left   = barGap + i * (barWidth + barGap)
            val right  = left + barWidth
            val top    = topPad + chartH - barH
            val bottom = topPad + chartH

            rectF.set(left, top, right, bottom)

            if (bar.isCurrent) {
                barPaint.shader = null
                barPaint.color  = context.getColor(R.color.bar_current)
                canvas.drawRoundRect(rectF, cornerR, cornerR, barPaint)

                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#EC4899"); style = Paint.Style.FILL
                }
                val badgeW = barWidth * 1.1f
                val badgeH = 18f
                val badgeL = left + barWidth / 2f - badgeW / 2f
                val badgeT = top - badgeH - 4f
                val badgeR = RectF(badgeL, badgeT, badgeL + badgeW, badgeT + badgeH)
                canvas.drawRoundRect(badgeR, 6f, 6f, badgePaint)

                val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color     = Color.WHITE
                    textAlign = Paint.Align.CENTER
                    textSize  = 9f * resources.displayMetrics.density * 0.7f
                    typeface  = Typeface.DEFAULT_BOLD
                }
                canvas.drawText(
                    "D${bar.currentDay ?: "?"}",
                    left + barWidth / 2f, badgeT + 12f, bp
                )
            } else {
                // Gradient fill for completed bars
                val grad = LinearGradient(
                    left, top, left, bottom,
                    Color.parseColor("#EC4899"), Color.parseColor("#A855F7"),
                    Shader.TileMode.CLAMP
                )
                barPaint.shader = grad
                canvas.drawRoundRect(rectF, cornerR, cornerR, barPaint)
                barPaint.shader = null

                // Day label above bar
                textPaint.color = context.getColor(R.color.text_primary)
                canvas.drawText("${bar.days}d", left + barWidth / 2f, top - 6f, textPaint)
            }

            // Month label below chart
            labelPaint.color = context.getColor(R.color.text_secondary)
            canvas.drawText(bar.month, left + barWidth / 2f, topPad + chartH + bottomPad - 4f, labelPaint)
        }
    }
}