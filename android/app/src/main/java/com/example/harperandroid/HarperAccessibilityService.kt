package com.example.harperandroid

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HarperAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var grammarRepository: GrammarRepository
    private lateinit var editableNodeTracker: EditableNodeTracker
    private lateinit var overlayManager: OverlayManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        grammarRepository = GrammarRepository(serviceScope)
        editableNodeTracker = EditableNodeTracker(serviceScope, grammarRepository)
        overlayManager = OverlayManager(this, serviceScope)

        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                grammarRepository.analysisResults,
                editableNodeTracker.currentSnapshot
            ) { result, snapshot ->
                Pair(result, snapshot)
            }.collect { (result, snapshot) ->
                val node = editableNodeTracker.currentNode
                if (node != null && snapshot != null && result.snapshot.text.isNotEmpty()) {
                    // Filter lints that intersect the cursor (composition-aware state)
                    val cursorStart = snapshot.selectionStart ?: -1
                    val cursorEnd = snapshot.selectionEnd ?: -1
                    
                    val safeLints = result.lints.filter { lint ->
                        val intersects = cursorStart >= 0 && cursorEnd >= 0 &&
                                lint.startUtf16.toInt() <= cursorEnd && lint.endUtf16.toInt() >= cursorStart
                        !intersects
                    }

                    if (safeLints.isNotEmpty()) {
                        overlayManager.updateOverlay(node, result.snapshot, safeLints)
                    } else {
                        overlayManager.removeOverlay()
                    }
                } else {
                    overlayManager.removeOverlay()
                }
            }
        }
        
        Log.d("Harper", "HarperAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (::editableNodeTracker.isInitialized) {
                val node = editableNodeTracker.currentNode
                if (node != null && event.windowId == node.windowId) {
                    // Refresh node to get updated bounds
                    if (node.refresh()) {
                        overlayManager.repositionOverlay(node)
                    }
                }
            }
        }

        if (::editableNodeTracker.isInitialized) {
            editableNodeTracker.onAccessibilityEvent(event)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::editableNodeTracker.isInitialized) {
            val node = editableNodeTracker.currentNode
            if (node != null && node.refresh()) {
                overlayManager.repositionOverlay(node)
            }
        }
    }

    override fun onInterrupt() {
        Log.d("Harper", "HarperAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
