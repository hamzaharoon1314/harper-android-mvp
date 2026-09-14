package com.example.harperandroid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uniffi.harper_android.HarperEngine
import uniffi.harper_android.HarperLint

data class AnalysisResult(
    val snapshot: TextSnapshot,
    val lints: List<HarperLint>
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GrammarRepository(
    private val scope: CoroutineScope,
    private val settingsRepository: HarperSettingsRepository? = null,
    private val engine: HarperEngine = HarperEngine.create(),
    private val backgroundDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
) {
    private val _configVersion = MutableStateFlow(0)

    init {
        settingsRepository?.let { repo ->
            scope.launch(backgroundDispatcher) {
                repo.config.collect { config ->
                    engine.updateConfig(config)
                    _configVersion.value = engine.getConfigVersion().toInt()
                }
            }
        }
    }

    private val _snapshots = MutableSharedFlow<TextSnapshot>(replay = 1)

    val analysisResults: Flow<AnalysisResult> = combine(_snapshots, _configVersion) { snapshot, configVersion -> 
            Pair(snapshot, configVersion)
        }
        .distinctUntilChangedBy { Pair(it.first.text, it.second) } // Re-analyze if text or config changes
        .debounce(300L)
        .mapLatest { (snapshot, _) ->
            if (snapshot.text.isEmpty()) {
                return@mapLatest AnalysisResult(snapshot, emptyList())
            }
            
            val startMs = android.os.SystemClock.elapsedRealtime()
            val lints = kotlinx.coroutines.withContext(backgroundDispatcher) {
                val isRobolectric = android.os.Build.FINGERPRINT == "robolectric"
                check(isRobolectric || android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) { "Linting must not run on the main thread" }
                engine.lint(snapshot.text, "en-US")
            }
            val endMs = android.os.SystemClock.elapsedRealtime()
            
            android.util.Log.d("HarperPerformance", "analysis_ms=${endMs - startMs} chars=${snapshot.text.length} lint_count=${lints.size}")
            
            AnalysisResult(snapshot, lints)
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    suspend fun submitSnapshot(snapshot: TextSnapshot) {
        _snapshots.emit(snapshot)
    }
}
