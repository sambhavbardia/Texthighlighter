package com.example.texthighlighter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class HighlightService : AccessibilityService() {

    companion object {
        var instance: HighlightService? = null
        const val AUTO_CLEAR_MS = 30_000L   // highlights disappear after this long
    }

    private val handler = Handler(Looper.getMainLooper())
    private var query: String? = null
    private var overlay: OverlayView? = null
    private val redraw = Runnable { draw() }
    private val autoClear = Runnable { clear() }

    override fun onServiceConnected() { instance = this }
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        clear()
        return super.onUnbind(intent)
    }

    /** Highlight every occurrence of [text] currently visible on screen. */
    fun highlight(text: String) {
        handler.removeCallbacks(autoClear)
        query = text.trim().takeIf { it.isNotEmpty() }
        if (query == null) { clear(); return }
        handler.postDelayed(redraw, 400)
        handler.postDelayed(autoClear, AUTO_CLEAR_MS)
    }

    fun clear() {
        query = null
        handler.removeCallbacks(redraw)
        handler.removeCallbacks(autoClear)
        overlay?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        overlay = null
    }

    // Re-draw when the screen scrolls or changes so boxes follow the text.
    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (query == null || e.packageName == packageName) return
        handler.removeCallbacks(redraw)
        handler.postDelayed(redraw, 120)
    }

    private fun draw() {
        val q = query ?: return
        val found = mutableListOf<RectF>()
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = w.root ?: continue
            if (root.packageName != packageName) collect(root, q, found)
        }
        val view = overlay ?: OverlayView(this).also {
            val lp = LP(
                LP.MATCH_PARENT, LP.MATCH_PARENT, LP.TYPE_ACCESSIBILITY_OVERLAY,
                LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCHABLE or
                    LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(it, lp)
            overlay = it
        }
        view.fill = getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("fill", false)
        view.rects = found
        view.invalidate()
    }

    private fun collect(node: AccessibilityNodeInfo, q: String, out: MutableList<RectF>) {
        if (!node.isVisibleToUser) return
        val text = node.text?.toString()
        if (text != null) {
            var i = text.indexOf(q, ignoreCase = true)
            while (i >= 0) {
                out += boundsFor(node, i, q.length)
                i = text.indexOf(q, i + q.length, ignoreCase = true)
            }
        }
        for (c in 0 until node.childCount) node.getChild(c)?.let { collect(it, q, out) }
    }

    /** Exact word bounds when the view supports it, otherwise the whole text view. */
    @Suppress("DEPRECATION")
    private fun boundsFor(node: AccessibilityNodeInfo, start: Int, len: Int): List<RectF> {
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        try {
            if (Build.VERSION.SDK_INT >= 26 && node.availableExtraData.contains(key)) {
                val args = Bundle().apply {
                    putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, start)
                    putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, len)
                }
                if (node.refreshWithExtraData(key, args)) {
                    val chars = node.extras.getParcelableArray(key)
                    if (chars != null) {
                        val lines = mutableListOf<RectF>()   // merge characters into one box per line
                        for (c in chars) {
                            val r = c as? RectF ?: continue
                            val last = lines.lastOrNull()
                            if (last != null && r.top < last.bottom && r.bottom > last.top) last.union(r)
                            else lines += RectF(r)
                        }
                        if (lines.isNotEmpty()) return lines
                    }
                }
            }
        } catch (_: Exception) {}
        val r = Rect()
        node.getBoundsInScreen(r)
        return listOf(RectF(r))
    }
}

class OverlayView(ctx: android.content.Context) : View(ctx) {
    var rects: List<RectF> = emptyList()
    var fill = false

    private val loc = IntArray(2)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF00C853.toInt()                       // bright green border
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x70006400                               // dark green, semi-transparent
    }

    override fun onDraw(c: Canvas) {
        getLocationOnScreen(loc)
        c.translate(-loc[0].toFloat(), -loc[1].toFloat())
        val pad = 3f * resources.displayMetrics.density
        for (r in rects) {
            val b = RectF(r).apply { inset(-pad, -pad) }
            if (fill) c.drawRoundRect(b, 8f, 8f, dark)
            c.drawRoundRect(b, 8f, 8f, border)
        }
    }
}
