package com.example.harperandroid

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

enum class EventClassification {
    FOCUS_CHANGED,
    TEXT_EDITED,
    SELECTION_MOVED,
    COMPOSITION_CHANGED,
    NODE_RECYCLED,
    IGNORED,
    UNSUPPORTED
}

class EventClassifier {
    private var lastText: String? = null
    private var lastNodeIdentity: NodeIdentity? = null

    fun classify(eventType: Int, identity: NodeIdentity, text: String): EventClassification {
        
        if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            lastNodeIdentity = identity
            lastText = text
            return EventClassification.FOCUS_CHANGED
        }

        if (identity != lastNodeIdentity) {
            // We got an event for a node that doesn't match what we thought was focused
            // Could be recycling in a RecyclerView
            lastNodeIdentity = identity
            lastText = text
            return EventClassification.NODE_RECYCLED
        }

        if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (text != lastText) {
                lastText = text
                return EventClassification.TEXT_EDITED
            } else {
                // Text is identical, but we got a TYPE_VIEW_TEXT_CHANGED event.
                // This happens often with IME composition spans changing without text changing.
                return EventClassification.COMPOSITION_CHANGED
            }
        }

        if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            if (text == lastText) {
                return EventClassification.SELECTION_MOVED
            }
            lastText = text
            return EventClassification.TEXT_EDITED
        }

        return EventClassification.IGNORED
    }
}
