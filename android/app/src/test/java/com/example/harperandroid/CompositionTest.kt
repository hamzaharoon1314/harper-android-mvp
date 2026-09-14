package com.example.harperandroid

import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompositionTest {

    @Test
    fun testSpans() {
        val node = AccessibilityNodeInfo.obtain()
        val text = SpannableString("Hello")
        text.setSpan(UnderlineSpan(), 0, 5, 0)
        node.text = text
        
        val retrieved = node.text
        println("Retrieved type: ")
        if (retrieved is android.text.Spanned) {
            println("Spans: ")
            for (span in retrieved.getSpans(0, retrieved.length, Any::class.java)) {
                println("Span: ")
            }
        }
    }
}
