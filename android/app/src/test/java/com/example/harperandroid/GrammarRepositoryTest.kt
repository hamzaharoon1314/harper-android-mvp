package com.example.harperandroid

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.harper_android.HarperEngine

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GrammarRepositoryTest {

    private fun createSnapshot(text: String, generation: Long): TextSnapshot {
        return TextSnapshot(
            packageName = "com.example.test",
            nodeIdentity = NodeIdentity(1, "android.widget.EditText"),
            text = text,
            selectionStart = text.length,
            selectionEnd = text.length,
            generation = generation,
            capturedAtElapsedMs = 0
        )
    }

    @Test
    fun testRapidTypingCancelsStaleAnalysis() = runTest {
        // We use the real HarperEngine over FFI
        val engine = HarperEngine.create()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = GrammarRepository(scope = backgroundScope, engine = engine, backgroundDispatcher = testDispatcher)

        val results = mutableListOf<AnalysisResult>()
        val job = backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            repository.analysisResults.toList(results)
        }

        // Simulate rapid typing
        repository.submitSnapshot(createSnapshot("H", 1))
        advanceTimeBy(100) // less than 300ms debounce
        repository.submitSnapshot(createSnapshot("He", 2))
        advanceTimeBy(100)
        repository.submitSnapshot(createSnapshot("He ", 3))
        advanceTimeBy(100)
        repository.submitSnapshot(createSnapshot("He go", 4))
        advanceTimeBy(100)
        repository.submitSnapshot(createSnapshot("He go to", 5))
        advanceTimeBy(100)
        repository.submitSnapshot(createSnapshot("He go to school", 6))
        
        // Wait for debounce and processing
        advanceTimeBy(1000)

        // Only the final snapshot should produce a result, preventing stale analysis
        assertEquals("Should only emit 1 result due to debounce/cancellation", 1, results.size)
        
        val finalResult = results.first()
        assertEquals("He go to school", finalResult.snapshot.text)
        assertEquals(6L, finalResult.snapshot.generation)
        
        // Verify grammar rule fired
        assertTrue(finalResult.lints.isNotEmpty())
        assertTrue(finalResult.lints[0].suggestions.any { it.displayText.contains("goes") })
    }

    @Test
    fun testExplicitStaleResultRejection() = runTest {
        val engine = HarperEngine.create()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = GrammarRepository(scope = backgroundScope, engine = engine, backgroundDispatcher = testDispatcher)

        val results = mutableListOf<AnalysisResult>()
        val job = backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            repository.analysisResults.toList(results)
        }

        // Submit generation 10
        repository.submitSnapshot(createSnapshot("Current text", 10))
        advanceTimeBy(1000)
        
        assertEquals(1, results.size)
        
        // Now submit an older generation (e.g. 5) that arrived late
        repository.submitSnapshot(createSnapshot("Old text", 5))
        advanceTimeBy(1000)

        // Should be rejected explicitly, so no new result emitted
        assertEquals(1, results.size)
        assertEquals(10L, results.first().snapshot.generation)
    }
}
