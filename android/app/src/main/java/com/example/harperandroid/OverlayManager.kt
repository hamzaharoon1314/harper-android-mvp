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
    private val positioner = OverlayPositioner(context)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var selectedIssueId: String? = null

    fun updateOverlay(
        node: AccessibilityNodeInfo,
        snapshot: TextSnapshot,
        lints: List<HarperLint>
    ) {
        scope.launch(Dispatchers.Main) {
            if (lints.isEmpty()) {
                removeOverlay()
                return@launch
            }

            // Keep selected issue if it still exists in the new list, otherwise default to first
            var currentIndex = lints.indexOfFirst { it.issueId == selectedIssueId }
            if (currentIndex == -1) {
                currentIndex = 0
            }
            selectedIssueId = lints[currentIndex].issueId

            val lint = lints[currentIndex]

            val isNewView = overlayView == null
            val view = overlayView ?: LayoutInflater.from(context).inflate(R.layout.harper_overlay, null)

            val tvTitle = view.findViewById<TextView>(R.id.tv_lint_title)
            val tvMessage = view.findViewById<TextView>(R.id.tv_lint_message)
            val btnPrev = view.findViewById<Button>(R.id.btn_prev)
            val btnNext = view.findViewById<Button>(R.id.btn_next)
            val containerSuggestions = view.findViewById<LinearLayout>(R.id.container_suggestions)
            val btnDismiss = view.findViewById<Button>(R.id.btn_dismiss)

            tvTitle.text = "${currentIndex + 1} of ${lints.size}"
            tvMessage.text = lint.message

            btnPrev.isEnabled = lints.size > 1
            btnPrev.setOnClickListener {
                val prevIndex = if (currentIndex > 0) currentIndex - 1 else lints.size - 1
                selectedIssueId = lints[prevIndex].issueId
                updateOverlay(node, snapshot, lints)
            }

            btnNext.isEnabled = lints.size > 1
            btnNext.setOnClickListener {
                val nextIndex = if (currentIndex < lints.size - 1) currentIndex + 1 else 0
                selectedIssueId = lints[nextIndex].issueId
                updateOverlay(node, snapshot, lints)
            }

            btnDismiss.setOnClickListener {
                removeOverlay()
            }

            containerSuggestions.removeAllViews()
            if (lint.suggestions.isEmpty()) {
                val noSugg = TextView(context).apply {
                    text = "No suggestions"
                    setPadding(8, 8, 8, 8)
                }
                containerSuggestions.addView(noSugg)
            } else {
                for (suggestion in lint.suggestions) {
                    val btn = Button(context).apply {
                        text = suggestion.displayText
                        setOnClickListener {
                            applier.applyCorrection(node, snapshot, lint, suggestion)
                            removeOverlay()
                        }
                    }
                    containerSuggestions.addView(btn)
                }
            }

            val params = positioner.getLayoutParams(node)

            if (isNewView) {
                try {
                    windowManager.addView(view, params)
                    overlayView = view
                } catch (e: Exception) {
                    Log.e("Harper", "Failed to add overlay", e)
                }
            } else {
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    Log.e("Harper", "Failed to update overlay layout", e)
                }
            }
        }
    }

    fun repositionOverlay(node: AccessibilityNodeInfo) {
        val view = overlayView ?: return
        val params = positioner.getLayoutParams(node)
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e("Harper", "Failed to reposition overlay", e)
        }
    }

    fun removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
                overlayView = null
                selectedIssueId = null
                Log.d("Harper", "Overlay removed")
            } catch (e: Exception) {
                Log.e("Harper", "Failed to remove overlay", e)
            }
        }
    }
}
