package com.example.harperandroid

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uniffi.harper_android.HarperEngine
import uniffi.harper_android.HarperLint

data class AnalysisResult(
    val snapshot: TextSnapshot,
    val lints: List<HarperLint>,
    val requestId: Long,
    val configVersion: Int
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

    private val requestCounter = AtomicLong(0)
    private val latestGenerations = ConcurrentHashMap<NodeIdentity, Long>()

    // Bounded queueing to protect against synthetic event spam
    private val _snapshots = MutableSharedFlow<Pair<TextSnapshot, Long>>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    val analysisResults: Flow<AnalysisResult> = combine(_snapshots, _configVersion) { (snapshot, reqId), configVersion -> 
            Triple(snapshot, reqId, configVersion)
        }
        .distinctUntilChangedBy { Triple(it.first.text, it.second, it.third) } // Re-analyze if text, requestId, or config changes
        .debounce(300L)
        .mapLatest { (snapshot, reqId, configVersion) ->
            if (snapshot.text.isEmpty()) {
                return@mapLatest AnalysisResult(snapshot, emptyList(), reqId, configVersion)
            }
            
            val startMs = android.os.SystemClock.elapsedRealtime()
            val lints = kotlinx.coroutines.withContext(backgroundDispatcher) {
                val isRobolectric = android.os.Build.FINGERPRINT == "robolectric"
                check(isRobolectric || android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) { "Linting must not run on the main thread" }
                engine.lint(snapshot.text, "en-US")
            }
            val endMs = android.os.SystemClock.elapsedRealtime()
            
            // Phase 16 Performance Logging
            android.util.Log.d("HarperPerformance", "Linted ${snapshot.text.length} chars in ${endMs - startMs}ms (Thread: ${Thread.currentThread().name})")
            
            val currentGen = latestGenerations[snapshot.nodeIdentity] ?: 0L
            val isGenerationStale = snapshot.generation < currentGen
            val isConfigStale = configVersion < _configVersion.value
            
            if (isGenerationStale || isConfigStale) {
                android.util.Log.d("HarperPerformance", "Stale result rejected for reqId=$reqId generation=${snapshot.generation} (currentGen=$currentGen) configVersion=$configVersion")
                return@mapLatest null
            }
            
            android.util.Log.d("HarperPerformance", "analysis_ms=${endMs - startMs} chars=${snapshot.text.length} lint_count=${lints.size}")
            
            AnalysisResult(snapshot, lints, reqId, configVersion)
        }
        .filterNotNull()
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    suspend fun submitSnapshot(snapshot: TextSnapshot) {
        val currentGen = latestGenerations[snapshot.nodeIdentity] ?: 0L
        if (snapshot.generation > currentGen) {
            latestGenerations[snapshot.nodeIdentity] = snapshot.generation
        }
        _snapshots.emit(Pair(snapshot, requestCounter.incrementAndGet()))
    }
}
