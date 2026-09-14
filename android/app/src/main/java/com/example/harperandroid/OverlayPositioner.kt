package com.example.harperandroid

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo

class OverlayPositioner(private val context: Context) {
    fun getLayoutParams(node: AccessibilityNodeInfo): WindowManager.LayoutParams {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = bounds.bottom + 10 // popup appears near target

            // Adjust if out of bounds (e.g. keyboard pushed it up)
            val displayMetrics = context.resources.displayMetrics
            if (y > displayMetrics.heightPixels * 0.8) {
                // If it's too low, try to put it above the text field
                y = bounds.top - 200 // approximate height
                if (y < 0) y = 0
            }
        }
    }
}
