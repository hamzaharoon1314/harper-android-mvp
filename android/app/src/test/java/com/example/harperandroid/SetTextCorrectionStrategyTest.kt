package com.example.harperandroid

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uniffi.harper_android.EditOperation
import uniffi.harper_android.HarperLint
import uniffi.harper_android.HarperSuggestion

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetTextCorrectionStrategyTest {

    private val strategy = SetTextCorrectionStrategy()

    private fun snapshot(text: String, cursor: Int) = TextSnapshot(
        packageName = "com.test",
        nodeIdentity = NodeIdentity(1, "EditText"),
        text = text,
        selectionStart = cursor,
        selectionEnd = cursor,
        generation = 1,
        capturedAtElapsedMs = 0
    )

    private fun lint(start: Int, end: Int) = HarperLint(
        issueId = "id",
        startUtf16 = start.toUInt(),
        endUtf16 = end.toUInt(),
        message = "test",
        ruleId = null,
        suggestions = emptyList()
    )

    private fun suggestion(replacement: String) = HarperSuggestion(
        suggestionId = "s",
        displayText = "Fix",
        operation = EditOperation.ReplaceWith(replacement)
    )

    private fun mockNode(): AccessibilityNodeInfo = mock {
        on { performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), any()) } doReturn true
        on { performAction(eq(AccessibilityNodeInfo.ACTION_SET_SELECTION), any()) } doReturn true
    }

    private fun captureNewCursor(node: AccessibilityNodeInfo): Int {
        val captor = argumentCaptor<Bundle>()
        verify(node, atLeastOnce()).performAction(eq(AccessibilityNodeInfo.ACTION_SET_SELECTION), captor.capture())
        return captor.lastValue.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT)
    }

    @Test
    fun testCursorBeforeLintIsUnchanged() {
        val text = "Hello world"
        val newText = "Hello earth"
        val snap = snapshot(text, 3)
        val node = mockNode()
        strategy.applyCorrection(node, text, newText, snap, lint(6, 11), suggestion("earth"))
        assertEquals(3, captureNewCursor(node))
    }

    @Test
    fun testCursorAfterLintShiftsByDelta() {
        val text = "He go to school"
        val newText = "He goes to school"
        val snap = snapshot(text, 10)
        val node = mockNode()
        strategy.applyCorrection(node, text, newText, snap, lint(3, 5), suggestion("goes"))
        assertEquals(12, captureNewCursor(node))
    }

    @Test
    fun testCursorInsideLintSnapsToEndOfReplacement() {
        val text = "He go to school"
        val newText = "He goes to school"
        val snap = snapshot(text, 4)
        val node = mockNode()
        strategy.applyCorrection(node, text, newText, snap, lint(3, 5), suggestion("goes"))
        assertEquals(7, captureNewCursor(node))
    }

    @Test
    fun testCursorAtExactEndOfLintShiftsByDelta() {
        val text = "He go to school"
        val newText = "He goes to school"
        val snap = snapshot(text, 5)
        val node = mockNode()
        strategy.applyCorrection(node, text, newText, snap, lint(3, 5), suggestion("goes"))
        assertEquals(7, captureNewCursor(node))
    }
}
