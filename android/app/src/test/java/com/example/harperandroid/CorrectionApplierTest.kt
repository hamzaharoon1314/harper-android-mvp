package com.example.harperandroid

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uniffi.harper_android.LintResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CorrectionApplierTest {

    private fun createSnapshot(text: String): TextSnapshot {
        return TextSnapshot(
            packageName = "com.test",
            nodeIdentity = NodeIdentity(1, "EditText"),
            text = text,
            selectionStart = null,
            selectionEnd = null,
            generation = 1,
            capturedAtElapsedMs = 0
        )
    }

    @Test
    fun testCorrectionAppliesWhenTextIsIdentical() {
        val applier = CorrectionApplier()
        
        val node = mock<AccessibilityNodeInfo> {
            on { isEditable } doReturn true
            on { text } doReturn "This are a test"
            on { performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), any()) } doReturn true
        }

        val snapshot = createSnapshot("This are a test")
        val lint = LintResult(
            startUtf16 = 5u,
            endUtf16 = 8u,
            message = "grammar",
            suggestions = listOf("is")
        )

        val result = applier.applyCorrection(node, snapshot, lint, "is")

        assertTrue("Correction should be applied", result)

        // Capture the bundle args to verify the new text
        argumentCaptor<Bundle>().apply {
            verify(node).performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), capture())
            val newText = firstValue.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE)
            assert(newText == "This is a test")
        }
    }

    @Test
    fun testCorrectionRejectedWhenTextIsStale() {
        val applier = CorrectionApplier()
        
        // Node text has been changed by the user (rapid typing)
        val node = mock<AccessibilityNodeInfo> {
            on { isEditable } doReturn true
            on { text } doReturn "This are really good" 
        }

        // The snapshot was from older text
        val snapshot = createSnapshot("This are good")
        val lint = LintResult(
            startUtf16 = 5u,
            endUtf16 = 8u,
            message = "grammar",
            suggestions = listOf("is")
        )

        val result = applier.applyCorrection(node, snapshot, lint, "is")

        assertFalse("Stale correction should be rejected", result)
        verify(node, never()).performAction(any(), any())
    }
}
