package com.example.harperandroid

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class EditableNodeTracker(
    private val scope: CoroutineScope,
    private val grammarRepository: GrammarRepository
) {
    private val generationCounter = AtomicLong(0)
    private val eventClassifier = EventClassifier()
    var currentNode: AccessibilityNodeInfo? = null
        private set
        
    private val _currentSnapshot = kotlinx.coroutines.flow.MutableStateFlow<TextSnapshot?>(null)
    val currentSnapshot: kotlinx.coroutines.flow.StateFlow<TextSnapshot?> = _currentSnapshot.asStateFlow()

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        val node = event.source ?: return
        
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED || 
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            checkNode(node, event)
        }
    }

    private val appPolicy = AppPolicy()

    private fun checkNode(node: AccessibilityNodeInfo, event: AccessibilityEvent) {
        val packageName = node.packageName?.toString() ?: ""
        val supportLevel = appPolicy.getSupportLevel(packageName)

        if (supportLevel == AppPolicy.SupportLevel.DENIED || supportLevel == AppPolicy.SupportLevel.LIMITED || ProtectedFieldDetector.isSensitive(node)) {
            currentNode = null
            scope.launch {
                grammarRepository.submitSnapshot(TextSnapshot("", NodeIdentity(-1, ""), "", null, null, generationCounter.incrementAndGet(), SystemClock.elapsedRealtime()))
            }
            return
        }

        if (node.isEditable && node.isFocused) {
            val text = node.text?.toString() ?: ""
            val identity = NodeIdentity(windowId = node.windowId, className = node.className?.toString() ?: "")
            val classification = eventClassifier.classify(event.eventType, identity, text)
            currentNode = node
            
            val snapshot = TextSnapshot(
                packageName = packageName,
                nodeIdentity = identity,
                text = text,
                selectionStart = node.textSelectionStart.takeIf { it >= 0 },
                selectionEnd = node.textSelectionEnd.takeIf { it >= 0 },
                generation = generationCounter.incrementAndGet(),
                capturedAtElapsedMs = SystemClock.elapsedRealtime()
            )
            
            _currentSnapshot.value = snapshot

            // If it's a movement/composition without actual text changes, don't trigger heavy analysis
            if (classification == EventClassification.SELECTION_MOVED || 
                classification == EventClassification.COMPOSITION_CHANGED || 
                classification == EventClassification.IGNORED) {
                return
            }
            
            // Raw text MUST NOT BE LOGGED (Phase 7 Privacy Rule)
            Log.d("Harper", "Identified editable node in $packageName. Classification: $classification. Starting analysis...")
            scope.launch {
                grammarRepository.submitSnapshot(snapshot)
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            // Focus changed to something not editable, or lost focus
            currentNode = null
            _currentSnapshot.value = null
            scope.launch {
                grammarRepository.submitSnapshot(TextSnapshot("", NodeIdentity(-1, ""), "", null, null, generationCounter.incrementAndGet(), SystemClock.elapsedRealtime()))
            }
        }
    }
}
