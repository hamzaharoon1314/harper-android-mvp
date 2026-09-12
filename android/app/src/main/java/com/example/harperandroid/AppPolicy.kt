package com.example.harperandroid

class AppPolicy {
    private val blocklist = mutableSetOf(
        "com.android.chrome", // Example blocked apps for MVP
        "com.google.android.inputmethod.latin"
    )

    fun isAllowed(packageName: String): Boolean {
        // MVP: Blocklist mode
        return !blocklist.contains(packageName)
    }

    fun blockApp(packageName: String) {
        blocklist.add(packageName)
    }

    fun allowApp(packageName: String) {
        blocklist.remove(packageName)
    }
}
