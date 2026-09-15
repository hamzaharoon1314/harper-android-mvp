package com.example.harperandroid

import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo

object ProtectedFieldDetector {
    fun isSensitive(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true

        val inputType = node.inputType
        val clazz = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        if (clazz == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) {
                return true
            }
        }
        if (clazz == InputType.TYPE_CLASS_NUMBER) {
            if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                return true
            }
        }

        return false
    }
}
