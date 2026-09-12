package com.example.harperandroid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import uniffi.harper_android.HarperEngine
import uniffi.harper_android.LintResult

data class AnalysisResult(
    val snapshot: TextSnapshot,
    val lints: List<LintResult>
)

@OptIn(FlowPreview::class)
class GrammarRepository(
    private val scope: CoroutineScope,
    private val engine: HarperEngine = HarperEngine.create()
) {
    private val _snapshots = MutableSharedFlow<TextSnapshot>(replay = 1)

    val analysisResults: Flow<AnalysisResult> = _snapshots
        .distinctUntilChangedBy { it.text } // Only re-analyze if text changes
        .debounce(300L)
        .mapLatest { snapshot ->
            if (snapshot.text.isEmpty()) {
                return@mapLatest AnalysisResult(snapshot, emptyList())
            }
            
            val startMs = android.os.SystemClock.elapsedRealtime()
            val lints = engine.lint(snapshot.text, "en-US")
            val endMs = android.os.SystemClock.elapsedRealtime()
            
            android.util.Log.d("HarperPerformance", "analysis_ms=${endMs - startMs} chars=${snapshot.text.length} lint_count=${lints.size}")
            
            AnalysisResult(snapshot, lints)
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    suspend fun submitSnapshot(snapshot: TextSnapshot) {
        _snapshots.emit(snapshot)
    }
}
