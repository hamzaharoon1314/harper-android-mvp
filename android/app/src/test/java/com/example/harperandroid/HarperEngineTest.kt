package com.example.harperandroid

import org.junit.Test
import org.junit.Assert.*
import uniffi.harper_android.HarperEngine

class HarperEngineTest {
    @Test
    fun testHarperEngineLinting() {
        val engine = HarperEngine.create()
        // harper-core 2.10.0 currently does not flag "This are a test", 
        // so we use "He go to school" which correctly triggers a subject-verb agreement lint.
        val results = engine.lint("He go to school", "en-US")
        
        assertTrue("Expected at least one lint result", results.isNotEmpty())
        
        val firstResult = results.first()
        assertTrue("Expected replacement to contain 'goes'", firstResult.suggestions.contains("goes"))
        
        // "He go to school" -> "go" is at index 3..5
        assertEquals(3u, firstResult.startUtf16)
        assertEquals(5u, firstResult.endUtf16)
    }
}
