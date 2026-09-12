package com.example.harperandroid

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class EditableNodeTracker(
    private val scope: CoroutineScope,
    private val grammarRepository: GrammarRepository
) {
    private val generationCounter = AtomicLong(0)
    var currentNode: AccessibilityNodeInfo? = null
        private set

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        val node = event.source ?: return
        
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED || 
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            checkNode(node, event)
        }
    }

    private val appPolicy = AppPolicy()

    private fun checkNode(node: AccessibilityNodeInfo, event: AccessibilityEvent) {
        val packageName = node.packageName?.toString() ?: ""

        if (!appPolicy.isAllowed(packageName) || ProtectedFieldDetector.isSensitive(node)) {
            currentNode = null
            scope.launch {
                grammarRepository.submitSnapshot(TextSnapshot("", NodeIdentity(-1, ""), "", null, null, generationCounter.incrementAndGet(), SystemClock.elapsedRealtime()))
            }
            return
        }

        if (node.isEditable && node.isFocused) {
            currentNode = node
            val text = node.text?.toString() ?: ""
            
            val snapshot = TextSnapshot(
                packageName = packageName,
                nodeIdentity = NodeIdentity(
                    windowId = node.windowId,
                    className = node.className?.toString() ?: ""
                ),
                text = text,
                selectionStart = node.textSelectionStart.takeIf { it >= 0 },
                selectionEnd = node.textSelectionEnd.takeIf { it >= 0 },
                generation = generationCounter.incrementAndGet(),
                capturedAtElapsedMs = SystemClock.elapsedRealtime()
            )
            
            // Raw text MUST NOT BE LOGGED (Phase 7 Privacy Rule)
            Log.d("Harper", "Identified editable node in $packageName. Starting analysis...")
            scope.launch {
                grammarRepository.submitSnapshot(snapshot)
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            // Focus changed to something not editable, or lost focus
            currentNode = null
            scope.launch {
                grammarRepository.submitSnapshot(TextSnapshot("", NodeIdentity(-1, ""), "", null, null, generationCounter.incrementAndGet(), SystemClock.elapsedRealtime()))
            }
        }
    }
}
