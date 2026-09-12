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
            grammarRepository.analysisResults.collect { result ->
                val node = editableNodeTracker.currentNode
                if (node != null && result.lints.isNotEmpty() && result.snapshot.text.isNotEmpty()) {
                    overlayManager.updateOverlay(node, result.snapshot, result.lints)
                } else {
                    overlayManager.removeOverlay()
                }
            }
        }
        
        Log.d("Harper", "HarperAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (::editableNodeTracker.isInitialized) {
            editableNodeTracker.onAccessibilityEvent(event)
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
