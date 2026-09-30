package com.aivigil.periodtracker.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class CycleProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var actualCurrentDay  = 1
    private var selectedDay       = 1
    private var animatedDayFloat  = 1f

    private var cycleLength      = 28
    private var periodDuration   = 5

    private var resetAnimator: ValueAnimator? = null
    var onDaySelectedListener: ((Int) -> Unit)? = null

    // ── Colors ────────────────────────────────────────────────────
    private val colorPeriod       = Color.parseColor("#C2185B")
    private val colorFertile      = Color.parseColor("#3B7A57")
    private val colorOvulation    = Color.parseColor("#E66A28")
    private val colorLowFertility = Color.parseColor("#64B5F6")
    private val colorLuteal       = Color.parseColor("#BA68C8")
    private val colorDarkText     = Color.parseColor("#2D1B33")
    private val colorMutedText    = Color.parseColor("#8A7A8F")

    // ── Paints ────────────────────────────────────────────────────
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#40FFFFFF")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val currentDayFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val ovalRect = RectF()

    // ── Public API ────────────────────────────────────────────────

    fun setProgress(day: Int, totalCycleDays: Int, periodDays: Int = 5) {
        cycleLength      = totalCycleDays.coerceAtLeast(1)
        periodDuration   = periodDays.coerceAtLeast(1)
        actualCurrentDay = day.coerceIn(1, cycleLength)
        selectedDay      = actualCurrentDay
        animatedDayFloat = actualCurrentDay.toFloat()
        invalidate()
    }

    // ── Phase boundary helpers ─────────────────────────────────────
    //
    // ✅ FIX 1 & 2 — clamp ovulation and fertileStart to after period ends,
    // matching CycleEngine.phase() and CycleEngine.bestPrediction() exactly.
    // Old code used coerceIn(1, cycleLength) which allowed ovulation to fall
    // inside the period on short cycles (e.g. 19-day cycle, 5-day period).

    private fun ovulationDay(): Int =
        (cycleLength - 14).coerceAtLeast(periodDuration + 2)

    private fun fertileStart(): Int =
        (ovulationDay() - 5).coerceAtLeast(periodDuration + 1)

    // ✅ FIX 3 — fertileEnd is now ovulationDay (inclusive), matching
    // CycleEngine where the fertile window runs from fertileStart through
    // ovulation day. The old code used ovulationDay() - 1 which excluded
    // the ovulation day itself from the green arc.
    private fun fertileEnd(): Int = ovulationDay()

    private fun dayToAngle(day: Float): Float =
        -90f + ((day - 1f) / cycleLength.toFloat()) * 360f

    // ── Draw ──────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w       = width.toFloat()
        val h       = height.toFloat()
        val size    = min(w, h)
        val cx      = w / 2f
        val cy      = h / 2f
        val ringWidth = size * 0.07f
        val radius  = size / 2f - ringWidth / 2f - size * 0.04f

        ovalRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        ringPaint.strokeWidth = ringWidth

        // 1. Ring segments
        drawPhaseArc(canvas, 1, periodDuration, colorPeriod)

        val fStart = fertileStart()
        val fEnd   = fertileEnd()
        val ovDay  = ovulationDay()

        if (periodDuration + 1 < fStart) {
            drawPhaseArc(canvas, periodDuration + 1, fStart - 1, colorLowFertility)
        }
        if (fStart <= fEnd) {
            drawPhaseArc(canvas, fStart, fEnd, colorFertile)
        }
        // Ovulation day gets its own distinct color on top of fertile arc
        drawPhaseArc(canvas, ovDay, ovDay, colorOvulation)

        // ✅ FIX 7 — renamed from "Safe Days" to "Luteal Phase" with colorLuteal
        if (ovDay + 1 <= cycleLength) {
            drawPhaseArc(canvas, ovDay + 1, cycleLength, colorLuteal)
        }

        // 2. Ticks & labels
        drawDayTicks(canvas, cx, cy, radius, ringWidth)
        drawDayLabels(canvas, cx, cy, radius, ringWidth)

        // 3. Indicator & center
        drawCurrentDayIndicator(canvas, cx, cy, radius, ringWidth)
        drawCenterContent(canvas, cx, cy, w)
    }

    private fun drawPhaseArc(canvas: Canvas, startDay: Int, endDay: Int, color: Int) {
        if (startDay > endDay) return
        val startAngle   = dayToAngle(startDay.toFloat())
        val numberOfDays = endDay - startDay + 1
        val sweep        = (numberOfDays.toFloat() / cycleLength.toFloat()) * 360f
        ringPaint.color  = color
        canvas.drawArc(ovalRect, startAngle, sweep, false, ringPaint)
    }

    private fun drawDayTicks(
        canvas: Canvas, cx: Float, cy: Float, radius: Float, ringWidth: Float
    ) {
        tickPaint.strokeWidth = width * 0.003f
        val innerR = radius - ringWidth * 0.3f
        val outerR = radius + ringWidth * 0.3f

        for (i in 1..cycleLength) {
            val angle  = Math.toRadians(dayToAngle(i.toFloat()).toDouble())
            val startX = cx + innerR * cos(angle).toFloat()
            val startY = cy + innerR * sin(angle).toFloat()
            val stopX  = cx + outerR * cos(angle).toFloat()
            val stopY  = cy + outerR * sin(angle).toFloat()
            canvas.drawLine(startX, startY, stopX, stopY, tickPaint)
        }
    }

    private fun drawDayLabels(
        canvas: Canvas, cx: Float, cy: Float, radius: Float, ringWidth: Float
    ) {
        textPaint.textSize   = width * 0.032f
        val labelDistance    = radius + ringWidth / 2f + width * 0.03f

        // ✅ FIX 9 — key day labels now derived from actual phase boundaries
        // instead of hardcoded 7/14/21 which are only meaningful for 28-day cycles.
        // Now shows: day 1, fertile start, ovulation day, cycle end, selected day.
        val keyDays = linkedSetOf(
            1,
            fertileStart(),
            ovulationDay(),
            cycleLength,
            selectedDay
        )

        keyDays.forEach { day ->
            val angle = Math.toRadians(dayToAngle(day.toFloat()).toDouble())
            val x     = cx + labelDistance * cos(angle).toFloat()
            val y     = cy + labelDistance * sin(angle).toFloat() -
                    (textPaint.ascent() + textPaint.descent()) / 2f

            textPaint.typeface = if (day == selectedDay) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textPaint.color    = if (day == selectedDay) colorDarkText else colorMutedText
            canvas.drawText(day.toString(), x, y, textPaint)
        }
    }

    private fun drawCurrentDayIndicator(
        canvas: Canvas, cx: Float, cy: Float, radius: Float, ringWidth: Float
    ) {
        val angle           = Math.toRadians(dayToAngle(animatedDayFloat).toDouble())
        val x               = cx + radius * cos(angle).toFloat()
        val y               = cy + radius * sin(angle).toFloat()
        val indicatorRadius = ringWidth * 0.52f
        currentDayFillPaint.color = Color.parseColor("#EC4899")
        canvas.drawCircle(x, y, indicatorRadius, currentDayFillPaint)
    }

    private fun drawCenterContent(canvas: Canvas, cx: Float, cy: Float, w: Float) {
        // "DAY" label
        textPaint.color         = colorMutedText
        textPaint.textSize      = w * 0.045f
        textPaint.typeface      = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.letterSpacing = 0.17f
        canvas.drawText("DAY", cx, cy - w * 0.19f, textPaint)
        textPaint.letterSpacing = 0f

        // Day number
        textPaint.color    = colorDarkText
        textPaint.textSize = w * 0.15f
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        canvas.drawText(selectedDay.toString(), cx, cy - w * 0.02f, textPaint)

        // ✅ FIX 3 applied here too — fertile window now includes ovulation day
        // ✅ FIX 7 — "Safe / Upcoming" renamed to "Luteal Phase" with medically
        // accurate label and color. The luteal phase is not "safe" — it's the
        // progesterone-dominant phase before the next period.
        // ✅ FIX 4 — "Next cycle in X days" off-by-one removed.
        //   Old: cycleLength - selectedDay + 1 (wrong)
        //   New: cycleLength - selectedDay     (correct)
        val phaseInfo = when {
            selectedDay <= periodDuration -> CyclePhaseInfo(
                "• Period Day $selectedDay", colorPeriod,
                "Conceiving Chance: Very Low",
                "Medium flow · Lining shedding"
            )
            selectedDay < fertileStart() -> CyclePhaseInfo(
                "• Low Fertility", colorLowFertility,
                "Conceiving Chance: Low",
                "Follicular phase · Before fertile window"
            )
            selectedDay in fertileStart()..fertileEnd() -> CyclePhaseInfo(
                "• Fertile Window", colorFertile,
                "Conceiving Chance: High",
                "High fertility window"
            )
            selectedDay == ovulationDay() -> CyclePhaseInfo(
                "• Ovulation Day", colorOvulation,
                "Conceiving Chance: Peak",
                "Egg release within 12–24 hrs"
            )
            else -> CyclePhaseInfo(
                "• Luteal Phase", colorLuteal,
                "Conceiving Chance: Low",
                "Next period in ${cycleLength - selectedDay} days"
            )
        }

        val badgeWidth  = w * 0.42f
        val badgeHeight = w * 0.072f
        val badgeTop    = cy + w * 0.02f
        val badgeRect   = RectF(
            cx - badgeWidth / 2f, badgeTop,
            cx + badgeWidth / 2f, badgeTop + badgeHeight
        )

        badgeBgPaint.color    = Color.parseColor("#F7F4FA")
        badgeBorderPaint.color = Color.parseColor("#E4DFEA")
        badgeBorderPaint.strokeWidth = w * 0.003f

        canvas.drawRoundRect(badgeRect, badgeHeight / 2f, badgeHeight / 2f, badgeBgPaint)
        canvas.drawRoundRect(badgeRect, badgeHeight / 2f, badgeHeight / 2f, badgeBorderPaint)

        textPaint.color    = phaseInfo.badgeColor
        textPaint.textSize = w * 0.028f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        val badgeTextY     = badgeRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(phaseInfo.badgeText, cx, badgeTextY, textPaint)

        textPaint.color    = colorDarkText
        textPaint.textSize = w * 0.040f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(phaseInfo.conceivingText, cx, badgeRect.bottom + w * 0.050f, textPaint)

        textPaint.color    = colorMutedText
        textPaint.textSize = w * 0.035f
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText(phaseInfo.detailText, cx, badgeRect.bottom + w * 0.083f, textPaint)
    }

    // ── Touch ─────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                resetAnimator?.cancel()
                handleTouchMove(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                handleTouchMove(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animateResetToCurrentDay()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouchMove(event: MotionEvent) {
        val cx = width / 2f
        val cy = height / 2f
        val dx = event.x - cx
        val dy = event.y - cy

        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        angle += 90f
        if (angle < 0) angle += 360f

        val calculatedDay  = ((angle / 360f) * cycleLength).roundToInt() + 1
        val newSelectedDay = calculatedDay.coerceIn(1, cycleLength)

        if (newSelectedDay != selectedDay) {
            selectedDay      = newSelectedDay
            animatedDayFloat = selectedDay.toFloat()
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onDaySelectedListener?.invoke(selectedDay)
            invalidate()
        }
    }

    private fun animateResetToCurrentDay() {
        if (selectedDay == actualCurrentDay &&
            animatedDayFloat == actualCurrentDay.toFloat()) return

        resetAnimator?.cancel()
        var startVal   = animatedDayFloat
        val targetVal  = actualCurrentDay.toFloat()

        if (startVal - targetVal > cycleLength / 2f)  startVal -= cycleLength
        else if (targetVal - startVal > cycleLength / 2f) startVal += cycleLength

        resetAnimator = ValueAnimator.ofFloat(startVal, targetVal).apply {
            duration     = 450L
            interpolator = OvershootInterpolator(1.2f)

            addUpdateListener { animator ->
                var currentFloat = animator.animatedValue as Float
                if (currentFloat < 1f)              currentFloat += cycleLength
                if (currentFloat > cycleLength)     currentFloat -= cycleLength

                animatedDayFloat = currentFloat
                val nearestDay   = currentFloat.roundToInt().coerceIn(1, cycleLength)

                if (nearestDay != selectedDay) {
                    selectedDay = nearestDay
                    onDaySelectedListener?.invoke(selectedDay)
                }
                invalidate()
            }
            start()
        }
    }

    // ── Data class ────────────────────────────────────────────────

    private data class CyclePhaseInfo(
        val badgeText:     String,
        val badgeColor:    Int,
        val conceivingText: String,
        val detailText:    String
    )
}