# Harper Android - Compatibility Matrix

This document tracks the compatibility status of Harper Android across various target applications. Since the application relies on the Android AccessibilityService API to read text and apply corrections, behavior may vary across different apps and UI frameworks.

## Status Definitions
- **Supported**: App uses standard `EditText` or proper AccessibilityNodeInfo APIs. The overlay appears correctly, and corrections apply safely.
- **Unsupported**: App uses heavily customized editors (e.g., custom rendering, web views without a11y support) that break text extraction or text replacement. Harper is blocked from analyzing these.
- **Pending/Untested**: App has not been verified yet.

## Default Blocklist (MVP)
The following apps are explicitly blocked by the `AppPolicy` rule for privacy reasons, regardless of their technical compatibility:
- `com.android.chrome` (Chrome Browser)
- `com.google.android.inputmethod.latin` (Gboard)

## Compatibility Matrix

| App Name | Package Name | Status | Notes |
|----------|--------------|--------|-------|
| **Internal Fixture App** | `com.example.harperandroid` | Supported | Used for integration tests and development. Uses Jetpack Compose `OutlinedTextField` which exposes standard accessibility nodes. |
| **Google Messages** | `com.google.android.apps.messaging` | Pending | Standard Android text fields, should work fine. |
| **WhatsApp** | `com.whatsapp` | Pending | Typically uses standard `EditText`, but might use custom spans. |
| **Telegram** | `org.telegram.messenger` | Unsupported (Expected) | Telegram uses heavily customized UI components and spans. Likely breaks `ACTION_SET_TEXT`. |
| **Twitter / X** | `com.twitter.android` | Pending | |
| **Notion** | `notion.id` | Unsupported (Expected) | Notion uses web views and custom spans that do not respond to `ACTION_SET_TEXT` well. |
| **Microsoft Word** | `com.microsoft.office.word` | Unsupported (Expected) | Canvas-based text rendering. |

## Strategy for Unsupported Apps
Do not attempt to build one-off workarounds (like screen-scraping or clipboard injection) for unsupported apps. If an app breaks `ACTION_SET_TEXT` or sends corrupt `AccessibilityNodeInfo` texts, add it to the `AppPolicy` blocklist.
# Harper Android App Compatibility

Harper applies corrections using the native Android Accessibility APIs (AccessibilityNodeInfo.ACTION_SET_TEXT). Due to this architectural choice, Harper guarantees safety only for applications that implement standard Android UI primitives (EditText, TextView).

## Compatibility Matrix

### 1. Standard Android Applications: **FULL SUPPORT**
- Examples: Most messaging apps (Messages, WhatsApp, Telegram, Signal), simple note-taking apps, standard social media.
- Reasoning: Rely on standard EditText widgets where ACTION_SET_TEXT is fully supported, preserves span data, and executes synchronously without destroying surrounding node context.

### 2. Chromium / WebView (Browsers): **DENIED**
- Examples: Google Chrome (com.android.chrome), Firefox, WebView-based apps.
- Reasoning: HTML contenteditable fields often report chaotic text boundaries over Accessibility IPC. Setting text via ACTION_SET_TEXT can corrupt DOM structure, duplicate text, or silently fail.

### 3. Complex Document Editors: **DENIED**
- Examples: Google Docs (com.google.android.apps.docs), Microsoft Word, Notion.
- Reasoning: These applications rely heavily on custom canvas rendering or proprietary text engines. The accessibility tree often trails behind the internal representation. Mutating text directly via Accessibility often causes desynchronization and data loss.

### 4. Input Method Editors (IMEs): **DENIED**
- Examples: Gboard (com.google.android.inputmethod.latin), SwiftKey.
- Reasoning: Mutating the keyboard's own accessibility nodes creates recursive feedback loops and breaks standard composition buffers.

### 5. Terminal Emulators: **DENIED**
- Examples: Termux (com.termux).
- Reasoning: Grid-based terminal screens report layout fragments as text, not logical sentences. Applying grammar corrections would corrupt terminal buffers.
