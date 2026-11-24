package com.example.texthighlighter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * A small draggable, resizable box the user positions by hand over any text
 * on screen. No screen-reading of any kind — it is just a shape drawn in a
 * window, moved and resized by touch.
 *
 * Top-left dot   = toggle dark-green fill on/off
 * Top-right dot  = remove the marker
 * Bottom-right dot = drag to resize
 * Anywhere else  = drag to move
 */
class MarkerBoxView(
    context: Context,
    private val wm: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val onClose: () -> Unit
) : View(context) {

    var fill = false
        private set

    private val density = resources.displayMetrics.density
    private val handle = 26f * density
    private val minSize = (60f * density).toInt()

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF00C853.toInt()
        strokeWidth = 3f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x70006400 }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E7D32.toInt() }
    private val iconStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeWidth = 2.5f * density; style = Paint.Style.STROKE
    }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

    private enum class Mode { NONE, MOVE, RESIZE, CLOSE_TAP, FILL_TAP }
    private var mode = Mode.NONE
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var startW = 0
    private var startH = 0
    private var moved = false

    override fun onDraw(c: Canvas) {
        val sw = borderPaint.strokeWidth
        val r = RectF(sw, sw, width - sw, height - sw)
        if (fill) c.drawRoundRect(r, 10f, 10f, fillPaint)
        c.drawRoundRect(r, 10f, 10f, borderPaint)

        // close chip (top-right): an X
        c.drawCircle(width - handle / 2, handle / 2, handle / 2, chipPaint)
        c.drawLine(width - handle * 0.75f, handle * 0.25f, width - handle * 0.25f, handle * 0.75f, iconStroke)
        c.drawLine(width - handle * 0.25f, handle * 0.25f, width - handle * 0.75f, handle * 0.75f, iconStroke)

        // fill-toggle chip (top-left): filled or outline dot
        c.drawCircle(handle / 2, handle / 2, handle / 2, chipPaint)
        if (fill) c.drawCircle(handle / 2, handle / 2, handle * 0.22f, iconFill)
        else c.drawCircle(handle / 2, handle / 2, handle * 0.22f, iconStroke)

        // resize chip (bottom-right): diagonal arrow
        c.drawCircle(width - handle / 2, height - handle / 2, handle / 2, chipPaint)
        c.drawLine(width - handle * 0.75f, height - handle * 0.3f, width - handle * 0.3f, height - handle * 0.75f, iconStroke)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = e.rawX; downRawY = e.rawY
                moved = false
                mode = when {
                    e.x >= width - handle && e.y <= handle -> Mode.CLOSE_TAP
                    e.x <= handle && e.y <= handle -> Mode.FILL_TAP
                    e.x >= width - handle && e.y >= height - handle -> Mode.RESIZE
                    else -> Mode.MOVE
                }
                startX = params.x; startY = params.y
                startW = width; startH = height
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downRawX
                val dy = e.rawY - downRawY
                if (abs(dx) > 8 || abs(dy) > 8) moved = true
                when (mode) {
                    Mode.MOVE -> {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        wm.updateViewLayout(this, params)
                    }
                    Mode.RESIZE -> {
                        params.width = (startW + dx.toInt()).coerceAtLeast(minSize)
                        params.height = (startH + dy.toInt()).coerceAtLeast(minSize)
                        wm.updateViewLayout(this, params)
                    }
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    if (mode == Mode.CLOSE_TAP) onClose()
                    if (mode == Mode.FILL_TAP) { fill = !fill; invalidate() }
                }
                mode = Mode.NONE
            }
        }
        return true
    }
}
