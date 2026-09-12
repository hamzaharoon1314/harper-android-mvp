package com.example.harperandroid

data class NodeIdentity(
    val windowId: Int,
    val className: String
)

data class TextSnapshot(
    val packageName: String,
    val nodeIdentity: NodeIdentity,
    val text: String,
    val selectionStart: Int?,
    val selectionEnd: Int?,
    val generation: Long,
    val capturedAtElapsedMs: Long
)
