package com.example.harperandroid

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import uniffi.harper_android.LintResult

class CorrectionApplier {

    /**
     * Applies a correction to an AccessibilityNodeInfo.
     * Validates that the current text in the node perfectly matches the text from the analyzed snapshot.
     */
    fun applyCorrection(
        node: AccessibilityNodeInfo,
        snapshot: TextSnapshot,
        lint: LintResult,
        suggestion: String
    ): Boolean {
        if (!node.isEditable) {
            Log.w("Harper", "Cannot apply correction: Node is not editable.")
            return false
        }

        val currentText = node.text?.toString() ?: ""
        if (currentText != snapshot.text) {
            Log.w("Harper", "Stale Result Protection: Node text has changed since analysis. Rejecting correction.")
            return false
        }

        val start = lint.startUtf16.toInt()
        val end = lint.endUtf16.toInt()

        if (start < 0 || end > currentText.length || start > end) {
            Log.e("Harper", "Invalid utf-16 offset range: $start..$end in text of length ${currentText.length}")
            return false
        }

        val newText = currentText.substring(0, start) + suggestion + currentText.substring(end)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }

        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (success) {
            Log.d("Harper", "Successfully applied correction: '$suggestion'")
        } else {
            Log.e("Harper", "Failed to perform ACTION_SET_TEXT")
        }

        return success
    }
}
