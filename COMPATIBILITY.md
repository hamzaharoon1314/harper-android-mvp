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
