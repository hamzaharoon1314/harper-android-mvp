package com.example.harperandroid

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InputBoundsTest {
    private fun makeNode(text: String): AccessibilityNodeInfo =
        mock {
            on { isEditable } doReturn true
            on { isFocused } doReturn true
            on { isPassword } doReturn false
            on { inputType } doReturn 0
            on { packageName } doReturn "com.example.test"
            on { this.text } doReturn text
            on { windowId } doReturn 1
            on { className } doReturn "android.widget.EditText"
            on { textSelectionStart } doReturn -1
            on { textSelectionEnd } doReturn -1
        }

    private fun makeEvent(node: AccessibilityNodeInfo): AccessibilityEvent =
        mock {
            on { eventType } doReturn AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            on { source } doReturn node
        }

    @Test
    fun testTextExactly50kCharsIsAnalyzed() =
        runTest {
            val grammarRepository = mock<GrammarRepository>()
            val tracker = EditableNodeTracker(this, grammarRepository)
            val text = "a".repeat(50_000)
            tracker.onAccessibilityEvent(makeEvent(makeNode(text)))
            runCurrent()
            verify(grammarRepository, atLeastOnce()).submitSnapshot(
                argThat { snap -> snap.text.length == 50_000 },
            )
        }

    @Test
    fun testTextOver50kCharsIsDropped() =
        runTest {
            val grammarRepository = mock<GrammarRepository>()
            val tracker = EditableNodeTracker(this, grammarRepository)
            val text = "a".repeat(50_001)
            tracker.onAccessibilityEvent(makeEvent(makeNode(text)))
            runCurrent()
            verify(grammarRepository, never()).submitSnapshot(
                argThat { snap -> snap.text.length > 50_000 },
            )
        }
}
