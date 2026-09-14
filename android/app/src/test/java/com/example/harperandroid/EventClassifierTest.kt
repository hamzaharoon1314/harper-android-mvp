package com.example.harperandroid

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventClassifierTest {



    @Test
    fun testClassification() {
        val classifier = EventClassifier()
        
        val node1Identity = NodeIdentity(1, "EditText")
        
        // 1. Focus event -> FOCUS_CHANGED
        var result = classifier.classify(AccessibilityEvent.TYPE_VIEW_FOCUSED, node1Identity, "Hello")
        assertEquals(EventClassification.FOCUS_CHANGED, result)
        
        // 2. Selection changed with identical text -> SELECTION_MOVED
        result = classifier.classify(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED, node1Identity, "Hello")
        assertEquals(EventClassification.SELECTION_MOVED, result)
        
        // 3. Text changed with identical text (IME span update) -> COMPOSITION_CHANGED
        result = classifier.classify(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED, node1Identity, "Hello")
        assertEquals(EventClassification.COMPOSITION_CHANGED, result)
        
        // 4. Text actually changed via TYPE_VIEW_TEXT_CHANGED -> TEXT_EDITED
        result = classifier.classify(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED, node1Identity, "Hello, world")
        assertEquals(EventClassification.TEXT_EDITED, result)
        
        // 5. Node recycling (different window ID) -> NODE_RECYCLED
        val node3Identity = NodeIdentity(2, "EditText")
        result = classifier.classify(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED, node3Identity, "Hello, world")
        assertEquals(EventClassification.NODE_RECYCLED, result)
    }
}
