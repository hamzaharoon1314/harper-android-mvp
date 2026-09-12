package com.example.harperandroid

import android.view.accessibility.AccessibilityNodeInfo

object ProtectedFieldDetector {
    fun isSensitive(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true

        // Additional heuristic checks based on input type could be added here
        // (e.g. TYPE_TEXT_VARIATION_PASSWORD, TYPE_NUMBER_VARIATION_PASSWORD)
        
        return false
    }
}
