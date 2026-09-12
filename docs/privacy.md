# Privacy Model

Harper Android is designed with absolute privacy boundaries. Since the application leverages powerful accessibility services capable of reading all typed user input, strict limitations are enforced.

## Zero Persistence
- **No Cloud Backend**: Harper operates 100% on-device. No text is ever uploaded to a server, cloud analytics engine, or external API.
- **No Logcat Leaks**: Raw text extracted from accessibility events is deliberately obfuscated or suppressed in debug logs. Performance metrics log only metadata (e.g., character length, analysis latency), never the sentence itself.
- **No Local Storage**: The grammar engine operates entirely in RAM. Snapshots are discarded immediately after analysis or correction application. Nothing is saved to disk or shared preferences.

## Application Blocking (`AppPolicy`)
- Certain applications are highly sensitive and are hardcoded into an MVP blocklist. The pipeline simply will not trigger inside these packages.
- **Examples**: Mobile Browsers (`com.android.chrome`), IME Keyboards.

## Password Protection (`ProtectedFieldDetector`)
- Any `AccessibilityNodeInfo` that identifies itself as `isPassword = true` is immediately rejected by the tracker before the text even reaches the grammar repository.

## Memory Safety
- Once the user taps away from an active field, the `EditableNodeTracker` drops the active node reference (`currentNode = null`), ensuring sensitive DOM elements aren't leaked or retained longer than necessary.
