package com.example.harperandroid

import android.view.accessibility.AccessibilityNodeInfo
import uniffi.harper_android.HarperLint
import uniffi.harper_android.HarperSuggestion

interface CorrectionStrategy {
    fun applyCorrection(
        node: AccessibilityNodeInfo,
        currentText: String,
        newText: String,
        snapshot: TextSnapshot,
        lint: HarperLint,
        suggestion: HarperSuggestion
    ): Boolean
}
