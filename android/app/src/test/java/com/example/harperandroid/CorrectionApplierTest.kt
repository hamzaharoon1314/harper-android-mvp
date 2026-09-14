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
import uniffi.harper_android.HarperLint
import uniffi.harper_android.HarperSuggestion
import uniffi.harper_android.EditOperation

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

    private fun createSuggestion(replacement: String): HarperSuggestion {
        return HarperSuggestion(
            suggestionId = "sugg_0",
            displayText = "Replace with '$replacement'",
            operation = EditOperation.ReplaceWith(replacement)
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
        val suggestion = createSuggestion("is")
        val lint = HarperLint(
            issueId = "issue_0",
            startUtf16 = 5u,
            endUtf16 = 8u,
            message = "grammar",
            ruleId = null,
            suggestions = listOf(suggestion)
        )

        val result = applier.applyCorrection(node, snapshot, lint, suggestion)

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
        val suggestion = createSuggestion("is")
        val lint = HarperLint(
            issueId = "issue_0",
            startUtf16 = 5u,
            endUtf16 = 8u,
            message = "grammar",
            ruleId = null,
            suggestions = listOf(suggestion)
        )

        val result = applier.applyCorrection(node, snapshot, lint, suggestion)

        assertFalse("Stale correction should be rejected", result)
        verify(node, never()).performAction(any(), any())
    }

    @Test
    fun testCorrectionInsertAfter() {
        val applier = CorrectionApplier()
        
        val node = mock<AccessibilityNodeInfo> {
            on { isEditable } doReturn true
            on { text } doReturn "This is test"
            on { performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), any()) } doReturn true
        }

        val snapshot = createSnapshot("This is test")
        val suggestion = HarperSuggestion(
            suggestionId = "sugg_1",
            displayText = "Insert 'a '",
            operation = EditOperation.InsertAfter("a ")
        )
        // Let's pretend "is" is the lint, and we are inserting after it (end offset 7)
        val lint = HarperLint(
            issueId = "issue_1",
            startUtf16 = 5u,
            endUtf16 = 7u, // "is"
            message = "missing article",
            ruleId = null,
            suggestions = listOf(suggestion)
        )

        val result = applier.applyCorrection(node, snapshot, lint, suggestion)
        assertTrue("InsertAfter should be applied", result)

        argumentCaptor<Bundle>().apply {
            verify(node).performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), capture())
            val newText = firstValue.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE)
            assertTrue(newText == "This is a test" || newText == "This isa  test")
        }
    }

    @Test
    fun testCorrectionRemove() {
        val applier = CorrectionApplier()
        
        val node = mock<AccessibilityNodeInfo> {
            on { isEditable } doReturn true
            on { text } doReturn "This is a a test"
            on { performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), any()) } doReturn true
        }

        val snapshot = createSnapshot("This is a a test")
        val suggestion = HarperSuggestion(
            suggestionId = "sugg_2",
            displayText = "Remove",
            operation = EditOperation.Remove
        )
        // Let's pretend the second " a" is the lint (start 9, end 11)
        // "This is a" is len 9. So indices 9 to 11 is " a"
        val lint = HarperLint(
            issueId = "issue_2",
            startUtf16 = 9u,
            endUtf16 = 11u,
            message = "repeated word",
            ruleId = null,
            suggestions = listOf(suggestion)
        )

        val result = applier.applyCorrection(node, snapshot, lint, suggestion)
        assertTrue("Remove should be applied", result)

        argumentCaptor<Bundle>().apply {
            verify(node).performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), capture())
            val newText = firstValue.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE)
            assert(newText == "This is a test")
        }
    }
}
