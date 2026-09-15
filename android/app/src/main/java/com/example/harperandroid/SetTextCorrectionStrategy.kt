package com.example.harperandroid

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import uniffi.harper_android.EditOperation
import uniffi.harper_android.HarperLint
import uniffi.harper_android.HarperSuggestion

class SetTextCorrectionStrategy : CorrectionStrategy {
    override fun applyCorrection(
        node: AccessibilityNodeInfo,
        currentText: String,
        newText: String,
        snapshot: TextSnapshot,
        lint: HarperLint,
        suggestion: HarperSuggestion,
    ): Boolean {
        val args =
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }

        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!success) {
            Log.e("Harper", "SetTextCorrectionStrategy failed to perform ACTION_SET_TEXT")
            return false
        }

        // Restore selection/cursor if possible
        val cursorStart = snapshot.selectionStart ?: -1
        val cursorEnd = snapshot.selectionEnd ?: -1

        if (cursorStart >= 0) {
            val diff = newText.length - currentText.length
            val start = lint.startUtf16.toInt()
            val end = lint.endUtf16.toInt()

            var newCursorStart = cursorStart
            var newCursorEnd = cursorEnd

            val insertionLength =
                when (val op = suggestion.operation) {
                    is EditOperation.ReplaceWith -> op.replacement.length
                    is EditOperation.InsertAfter -> op.insertion.length
                    is EditOperation.Remove -> 0
                }

            if (cursorStart >= end) {
                newCursorStart += diff
            } else if (cursorStart > start) {
                newCursorStart = start + insertionLength
            }

            if (cursorEnd >= end) {
                newCursorEnd += diff
            } else if (cursorEnd > start) {
                newCursorEnd = newCursorStart
            }

            newCursorStart = newCursorStart.coerceIn(0, newText.length)
            newCursorEnd = newCursorEnd.coerceIn(0, newText.length)

            val selArgs =
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursorStart)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursorEnd)
                }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        }

        return true
    }
}
