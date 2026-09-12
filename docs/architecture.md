# Harper Android Architecture

The system architecture cleanly separates Android-specific lifecycle and UI components from the underlying grammar engine logic.

## The Pipeline

1. **AccessibilityNode Extraction**
   - The user types into an `EditText`.
   - Android emits an `AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED`.
   - `HarperAccessibilityService` receives the event and passes it to the `EditableNodeTracker`.

2. **Snapshot Generation**
   - The `EditableNodeTracker` reads the node and generates an immutable `TextSnapshot`.
   - Security filters (`ProtectedFieldDetector` and `AppPolicy`) are evaluated here. If the node is a password or the app is blocklisted, an empty snapshot is generated to halt the pipeline.

3. **Debounce & Asynchronous Analysis**
   - The snapshot is emitted to the `GrammarRepository`.
   - A Kotlin Coroutine Flow uses `distinctUntilChangedBy` and `debounce(300ms)` to ensure the engine only processes meaningful pauses in typing.
   - The snapshot text is passed into the native JNI boundary via `HarperEngine.lint()`.

4. **Rust Core (harper-core)**
   - The text enters the Rust FFI, powered by `UniFFI`.
   - The native `harper-core` engine tokenizes and applies grammatical rules to the text, generating a list of suggestions mapped to precise UTF-16 code unit offsets.
   - A serialized `AnalysisResult` is returned back to the JVM.

5. **UI Rendering**
   - The `HarperAccessibilityService` receives the result.
   - The `OverlayManager` calculates the screen bounds of the active `AccessibilityNode` and leverages the `WindowManager` to spawn an interactive UI pop-up (using `TYPE_ACCESSIBILITY_OVERLAY`) directly adjacent to the cursor.

6. **Correction Application**
   - If the user taps a grammar suggestion, the `CorrectionApplier` is invoked.
   - The applier verifies that the active field's `TextIdentity` has not mutated since the initial snapshot was taken (preventing stale text overwrites).
   - The suggestion is securely applied via Android's `ACTION_SET_TEXT` accessibility command.
