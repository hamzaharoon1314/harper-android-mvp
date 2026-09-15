package com.example.harperandroid

class AppPolicy {
    enum class SupportLevel {
        FULL,
        LIMITED,
        DENIED,
    }

    private val customPolicies = mutableMapOf<String, SupportLevel>()

    private val defaultBlocklist =
        setOf(
            // Browsers / WebViews
            "com.android.chrome",
            "org.mozilla.firefox",
            // IMEs
            "com.google.android.inputmethod.latin",
            "com.touchtype.swiftkey",
            // Complex Editors
            "com.google.android.apps.docs",
            "com.microsoft.office.word",
            "notion.id",
            // Terminals
            "com.termux",
        )

    fun getSupportLevel(packageName: String): SupportLevel {
        customPolicies[packageName]?.let { return it }
        if (defaultBlocklist.contains(packageName)) return SupportLevel.DENIED
        return SupportLevel.FULL
    }

    fun isAllowed(packageName: String): Boolean {
        return getSupportLevel(packageName) == SupportLevel.FULL
    }

    fun setPolicy(
        packageName: String,
        level: SupportLevel,
    ) {
        customPolicies[packageName] = level
    }
}
