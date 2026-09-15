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
            "com.android.chrome", // Browsers / WebViews
            "org.mozilla.firefox",
            "com.google.android.inputmethod.latin", // IMEs
            "com.touchtype.swiftkey",
            "com.google.android.apps.docs", // Complex Editors
            "com.microsoft.office.word",
            "notion.id",
            "com.termux", // Terminals
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
