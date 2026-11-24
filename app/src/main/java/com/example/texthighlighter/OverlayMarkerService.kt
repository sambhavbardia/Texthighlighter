package com.example.texthighlighter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager

/**
 * Shows one draggable marker box using only the "draw over other apps"
 * permission. Unlike HighlightService, this never reads what is on screen —
 * it just displays a shape the user positions manually.
 */
class OverlayMarkerService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.texthighlighter.STOP"
        const val CHANNEL_ID = "overlay_marker"
        const val NOTIF_ID = 42
    }

    private var wm: WindowManager? = null
    private var box: MarkerBoxView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopOverlay(); return START_NOT_STICKY }
        startForeground(NOTIF_ID, buildNotification())
        if (box == null) addBox()
        return START_STICKY
    }

    private fun addBox() {
        val manager = (getSystemService(WINDOW_SERVICE) as WindowManager).also { wm = it }
        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            (220 * density).toInt(), (90 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (40 * density).toInt()
            y = (300 * density).toInt()
        }
        val view = MarkerBoxView(this, manager, params) { stopOverlay() }
        manager.addView(view, params)
        box = view
    }

    private fun stopOverlay() {
        box?.let { v -> wm?.let { m -> try { m.removeView(v) } catch (_: Exception) {} } }
        box = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopOverlay()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Overlay marker", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        val stopIntent = Intent(this, OverlayMarkerService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating marker is on screen")
            .setContentText("Drag the green box onto any text. Top-left dot = fill, top-right = remove.")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .addAction(0, "Remove", stopPending)
            .setOngoing(true)
            .build()
    }
}
