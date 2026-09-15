package com.example.harperandroid

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPolicyTest {
    @Test
    fun testUnknownPackageIsFull() {
        val policy = AppPolicy()
        assertEquals(AppPolicy.SupportLevel.FULL, policy.getSupportLevel("com.unknown.app"))
    }

    @Test
    fun testChromeIsDenied() {
        val policy = AppPolicy()
        assertEquals(AppPolicy.SupportLevel.DENIED, policy.getSupportLevel("com.android.chrome"))
    }

    @Test
    fun testFirefoxIsDenied() {
        val policy = AppPolicy()
        assertEquals(AppPolicy.SupportLevel.DENIED, policy.getSupportLevel("org.mozilla.firefox"))
    }

    @Test
    fun testGboardIsDenied() {
        val policy = AppPolicy()
        assertEquals(AppPolicy.SupportLevel.DENIED, policy.getSupportLevel("com.google.android.inputmethod.latin"))
    }

    @Test
    fun testGoogleDocsIsDenied() {
        val policy = AppPolicy()
        assertEquals(AppPolicy.SupportLevel.DENIED, policy.getSupportLevel("com.google.android.apps.docs"))
    }

    @Test
    fun testTermuxIsDenied() {
        val policy = AppPolicy()
        assertEquals(AppPolicy.SupportLevel.DENIED, policy.getSupportLevel("com.termux"))
    }

    @Test
    fun testSetPolicyOverridesDefault() {
        val policy = AppPolicy()
        // Override chrome to LIMITED
        policy.setPolicy("com.android.chrome", AppPolicy.SupportLevel.LIMITED)
        assertEquals(AppPolicy.SupportLevel.LIMITED, policy.getSupportLevel("com.android.chrome"))
    }

    @Test
    fun testIsAllowedReturnsTrueOnlyForFull() {
        val policy = AppPolicy()
        assert(policy.isAllowed("com.example.messaging"))
        assert(!policy.isAllowed("com.android.chrome"))
    }
}
