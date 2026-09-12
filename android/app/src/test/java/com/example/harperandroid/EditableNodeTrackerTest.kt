package com.example.harperandroid

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import kotlinx.coroutines.test.runCurrent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class EditableNodeTrackerTest {

    @Test
    fun testPasswordFieldIsIgnored() = runTest {
        val grammarRepository = mock<GrammarRepository>()
        val tracker = EditableNodeTracker(this, grammarRepository)

        val node = mock<AccessibilityNodeInfo> {
            on { isEditable } doReturn true
            on { isFocused } doReturn true
            on { isPassword } doReturn true
            on { packageName } doReturn "com.example.safe"
            on { text } doReturn "secret123"
        }
        
        val event = mock<AccessibilityEvent> {
            on { eventType } doReturn AccessibilityEvent.TYPE_VIEW_FOCUSED
            on { source } doReturn node
        }

        tracker.onAccessibilityEvent(event)
        runCurrent()

        // It should submit an empty snapshot
        argumentCaptor<TextSnapshot>().apply {
            verify(grammarRepository).submitSnapshot(capture())
            assertEquals("", firstValue.text)
        }
    }

    @Test
    fun testBlockedAppIsIgnored() = runTest {
        val grammarRepository = mock<GrammarRepository>()
        val tracker = EditableNodeTracker(this, grammarRepository)

        val node = mock<AccessibilityNodeInfo> {
            on { isEditable } doReturn true
            on { isFocused } doReturn true
            on { isPassword } doReturn false
            on { packageName } doReturn "com.android.chrome" // Blocklisted in MVP
            on { text } doReturn "search query"
        }
        
        val event = mock<AccessibilityEvent> {
            on { eventType } doReturn AccessibilityEvent.TYPE_VIEW_FOCUSED
            on { source } doReturn node
        }

        tracker.onAccessibilityEvent(event)
        runCurrent()

        // It should submit an empty snapshot
        argumentCaptor<TextSnapshot>().apply {
            verify(grammarRepository).submitSnapshot(capture())
            assertEquals("", firstValue.text)
        }
    }
}
