package com.example.texthighlighter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        root.addView(TextView(this).apply {
            textSize = 16f
            text = "1. Tap the button below and turn on \"Text Highlighter\".\n\n" +
                "2. In any app, select some text and choose \"Highlight\" from the selection menu " +
                "(it may be under the ⋮ / More menu). Every match on screen gets marked.\n\n" +
                "Or type a word here, tap Highlight, then switch to the app you want to search."
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

        setContentView(root)
    }
}
