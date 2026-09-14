package com.example.harperandroid

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uniffi.harper_android.HarperLint

class OverlayManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val applier: CorrectionApplier = CorrectionApplier()
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun updateOverlay(
        node: AccessibilityNodeInfo,
        snapshot: TextSnapshot,
        lints: List<HarperLint>
    ) {
        scope.launch(Dispatchers.Main) {
            removeOverlay()

            if (lints.isEmpty()) return@launch

            val lint = lints.first() // MVP: Just show the first suggestion
            val suggestion = lint.suggestions.firstOrNull() ?: return@launch

            val view = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(0xFFE0E0E0.toInt())
                setPadding(16, 16, 16, 16)
                
                // Text
                val textView = TextView(context).apply {
                    text = "${lint.message}\nSuggestion: ${suggestion.displayText}"
                    setTextColor(0xFF000000.toInt())
                    setPadding(0, 0, 16, 0)
                }
                addView(textView)

                // Apply button
                val applyButton = Button(context).apply {
                    text = "Apply"
                    setOnClickListener {
                        applier.applyCorrection(node, snapshot, lint, suggestion)
                        removeOverlay()
                    }
                }
                addView(applyButton)

                // Ignore button
                val ignoreButton = Button(context).apply {
                    text = "Ignore"
                    setOnClickListener {
                        removeOverlay()
                    }
                }
                addView(ignoreButton)
            }

            // Figure out node bounds
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = bounds.left
                y = bounds.bottom + 10 // popup appears near target
            }

            try {
                val startMs = android.os.SystemClock.elapsedRealtime()
                windowManager.addView(view, params)
                overlayView = view
                val endMs = android.os.SystemClock.elapsedRealtime()
                Log.d("HarperPerformance", "overlay_ms=${endMs - startMs} target_latency_ms=${endMs - snapshot.capturedAtElapsedMs}")
            } catch (e: Exception) {
                Log.e("Harper", "Failed to add overlay", e)
            }
        }
    }

    fun removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
                overlayView = null
                Log.d("Harper", "Overlay removed")
            } catch (e: Exception) {
                Log.e("Harper", "Failed to remove overlay", e)
            }
        }
    }
}
