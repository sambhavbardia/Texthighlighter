package com.example.texthighlighter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val overlayPermissionCode = 100
    private val notifPermissionCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        // ---- Mode 1: automatic, Accessibility-based highlighting ----
        root.addView(sectionTitle("Auto-find (Accessibility Service)"))

        root.addView(TextView(this).apply {
            textSize = 15f
            text = "Turns on below, then select text in any app and choose \"Highlight\" from the " +
                "selection menu. Every match on screen gets marked.\n\n" +
                "Some apps — banking apps, IRCTC's Rail Connect, UPI apps — detect and block this " +
                "while it's on, since it's the same permission screen-reading malware uses. " +
                "Turn it off before opening those apps."
        })

        root.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        val input = EditText(this).apply { hint = "Text to highlight"; setSingleLine() }
        root.addView(input)

        root.addView(Button(this).apply {
            text = "Highlight"
            setOnClickListener {
                val s = HighlightService.instance
                if (s == null) Toast.makeText(context, "Enable the accessibility service first", Toast.LENGTH_SHORT).show()
                else {
                    s.highlight(input.text.toString())
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
                }
            }
        })

        root.addView(Button(this).apply {
            text = "Clear highlights"
            setOnClickListener { HighlightService.instance?.clear() }
        })

        root.addView(Switch(this).apply {
            text = "Fill with dark green (instead of border only)"
            isChecked = prefs.getBoolean("fill", false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("fill", on).apply() }
        })

        // ---- Mode 2: manual floating marker, overlay permission only ----
        root.addView(divider())
        root.addView(sectionTitle("Manual marker (no Accessibility needed)"))

        root.addView(TextView(this).apply {
            textSize = 15f
            text = "A small green box you drag onto any text yourself. It doesn't read the screen, " +
                "so it uses only the \"draw over other apps\" permission — the same one used by " +
                "chat-head style widgets. Some payment apps flag overlay permissions too, but it's " +
                "less commonly checked than Accessibility.\n\n" +
                "Once it's showing: drag the box to move it, the bottom-right dot to resize it, the " +
                "top-left dot to toggle fill, and the top-right dot (or the notification) to remove it."
        })

        root.addView(Button(this).apply {
            text = "Show floating marker"
            setOnClickListener { requestOverlayThenStart() }
        })

        root.addView(Button(this).apply {
            text = "Remove floating marker"
            setOnClickListener {
                startService(Intent(this@MainActivity, OverlayMarkerService::class.java).setAction(OverlayMarkerService.ACTION_STOP))
            }
        })

        setContentView(root)
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun divider() = View(this).apply {
        val h = (1 * resources.displayMetrics.density).toInt()
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h).apply {
            topMargin = (16 * resources.displayMetrics.density).toInt()
            bottomMargin = topMargin
        }
        setBackgroundColor(0xFFDDDDDD.toInt())
    }

    private fun requestOverlayThenStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow \"display over other apps\", then come back and tap this again", Toast.LENGTH_LONG).show()
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                overlayPermissionCode
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notifPermissionCode)
        }
        startForegroundService(Intent(this, OverlayMarkerService::class.java))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == overlayPermissionCode && Settings.canDrawOverlays(this)) {
            requestOverlayThenStart()
        }
    }
}
