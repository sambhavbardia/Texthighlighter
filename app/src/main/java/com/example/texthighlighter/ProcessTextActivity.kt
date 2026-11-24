package com.example.texthighlighter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/** Receives text from the "Highlight" option in any app's selection menu. */
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        val service = HighlightService.instance
        when {
            text.isNullOrBlank() -> {}
            service == null -> {
                Toast.makeText(this, "Turn on Text Highlighter in Accessibility settings", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            else -> service.highlight(text)
        }
        finish()
    }
}
