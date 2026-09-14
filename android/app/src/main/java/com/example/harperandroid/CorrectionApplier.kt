package com.example.harperandroid

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import uniffi.harper_android.HarperLint
import uniffi.harper_android.HarperSuggestion
import uniffi.harper_android.EditOperation

class CorrectionApplier(
    private val strategies: List<CorrectionStrategy> = listOf(SetTextCorrectionStrategy())
) {

    fun applyCorrection(
        node: AccessibilityNodeInfo,
        snapshot: TextSnapshot,
        lint: HarperLint,
        suggestion: HarperSuggestion
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

        val newText = when (val op = suggestion.operation) {
            is EditOperation.ReplaceWith -> {
                currentText.substring(0, start) + op.replacement + currentText.substring(end)
            }
            is EditOperation.InsertAfter -> {
                currentText.substring(0, end) + op.insertion + currentText.substring(end)
            }
            is EditOperation.Remove -> {
                currentText.substring(0, start) + currentText.substring(end)
            }
        }

        for (strategy in strategies) {
            val success = strategy.applyCorrection(node, currentText, newText, snapshot, lint, suggestion)
            if (success) {
                Log.d("Harper", "Successfully applied correction via ${strategy.javaClass.simpleName}")
                return true
            }
        }

        Log.e("Harper", "All correction strategies failed.")
        return false
    }
}
