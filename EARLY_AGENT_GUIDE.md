# Harper Android Grammar Assistant — Agentic Build Specification

**Status:** Implementation-ready  
**Date:** 2026-09-09  
**Target Android:** API 36+  
**Primary engine:** Harper `harper-core`  
**Android:** Kotlin + Jetpack Compose  
**Native:** Rust  
**Rust/Kotlin bridge:** UniFFI  
**System integration:** `AccessibilityService` + accessibility overlays  
**Privacy model:** Local-first, no raw typed text sent to a cloud service by default

> This file is the source of truth for the coding agent. Follow the phases in order. Do not skip acceptance criteria. Do not introduce cloud processing, OCR, or an IME unless a future specification explicitly requires it.

---

# 1. Mission

Build a Grammarly-like Android writing assistant using Harper's Rust grammar engine.

The app must:

1. Detect a focused editable text field in another Android application through `AccessibilityService`.
2. Read exposed text from the accessibility node.
3. Analyze the text locally with Harper `harper-core`.
4. Return grammar/spelling lints and replacements.
5. Display a non-blocking suggestion UI using accessibility overlays.
6. Apply a selected correction to the target field safely.
7. Avoid stale corrections when the user continues typing.
8. Avoid sensitive fields such as password/PIN/OTP fields.
9. Remain responsive while the user types.
10. Never persist raw typed text by default.
11. Be structured so more Android targets and UI improvements can be added without changing the grammar engine.

The first end-to-end success case is:

```text
User opens a supported app
        ↓
User types:
"This are a test"
        ↓
AccessibilityService detects editable node
        ↓
Harper analyzes text
        ↓
Suggestion:
"are" → "is"
        ↓
User taps suggestion
        ↓
Field becomes:
"This is a test"
```

---

# 2. Product Constraints

## 2.1 Privacy

The default pipeline must be:

```text
Target app
  ↓
AccessibilityService
  ↓
Local Kotlin process
  ↓
Rust harper-core
  ↓
Result
  ↓
Overlay
```

No raw text may be sent to a server.

Do not log raw text.

Do not persist raw text.

Do not put raw text into crash reports, analytics events, debug telemetry, or exception messages.

When diagnostics are needed, log metadata only:

```text
event=ANALYSIS_STARTED
package=com.example.app
chars=47
generation=18
```

## 2.2 Sensitive Input

Do not process:

- password fields
- PIN fields
- OTP/authentication code fields
- fields explicitly marked sensitive by the target app
- fields excluded by the user's app settings

Fail closed if field sensitivity cannot be determined reliably.

## 2.3 User Control

The user must be able to:

- enable/disable the service
- choose enabled apps or excluded apps
- select language
- disable overlays
- inspect privacy information
- disable automatic analysis
- disable specific app packages

Do not automatically submit accessibility settings changes for the user.

---

# 3. Architecture

```text
                             OWN APP
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  Jetpack Compose                                            │
│        │                                                    │
│        ▼                                                    │
│  Screen ViewModels                                          │
│        │                                                    │
│        ▼                                                    │
│  Use Cases / Repositories                                   │
│        │                                                    │
│        ├──────────────► DataStore                           │
│        │                                                    │
│        ▼                                                    │
│  GrammarRepository                                          │
│        │                                                    │
│        ▼                                                    │
│  HarperEngine                                               │
│        │                                                    │
│        ▼                                                    │
│  UniFFI Kotlin Bindings                                     │
└────────┼────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│                         RUST                                │
│                                                             │
│                    harper-android                           │
│                           │                                 │
│                           ▼                                 │
│                      harper-core                            │
│                           │                                 │
│                           ▼                                 │
│                         Lint[]                              │
└─────────────────────────────────────────────────────────────┘


                     EXTERNAL APP PATH

External app
    │
    ▼
AccessibilityService
    │
    ▼
AccessibilityController
    │
    ▼
EditableNodeTracker
    │
    ▼
TextSnapshot
    │
    ▼
TextChangeDetector
    │
    ▼
AnalysisScheduler
    │
    ▼
GrammarRepository
    │
    ▼
HarperEngine
    │
    ▼
Lint results
    │
    ▼
SuggestionModel
    │
    ▼
OverlayManager
    │
    ├── indicator
    ├── popup
    └── optional inline error marker
    │
    ▼
User selects suggestion
    │
    ▼
CorrectionApplier
    │
    ▼
Validate snapshot
    │
    ▼
AccessibilityNodeInfo action
    │
    ▼
Target field updated
```

---

# 4. Repository Strategy

Prefer creating a dedicated Android project that consumes Harper rather than immediately maintaining a full fork.

Recommended top-level layout:

```text
harper-android/
├── android/
│   ├── app/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── rust/
│   └── harper-android/
│       ├── Cargo.toml
│       └── src/
│           ├── lib.rs
│           ├── engine.rs
│           ├── models.rs
│           └── error.rs
│
├── docs/
│   ├── architecture.md
│   ├── privacy.md
│   ├── accessibility.md
│   ├── compatibility.md
│   └── troubleshooting.md
│
├── tests/
│   ├── rust/
│   ├── android/
│   └── compatibility/
│
└── README.md
```

Preferred dependency direction:

```text
Android app
      ↓
harper-android adapter
      ↓
Harper harper-core
```

Avoid copying Harper internals into Kotlin.

If direct crates.io consumption is insufficient for the required API or version, use a pinned Git dependency or workspace integration. Keep the integration isolated so it can be upgraded later.

---

# 5. Technology Decisions

## 5.1 Android

Use:

- Kotlin
- Jetpack Compose
- AndroidX
- ViewModel
- Kotlin Coroutines
- Kotlin Flow
- DataStore
- Android Accessibility APIs
- Android NDK
- Gradle Kotlin DSL

Use dependency injection only if useful. Hilt is acceptable but is not mandatory for the MVP.

## 5.2 Grammar Engine

Use:

```text
Harper harper-core
```

Do not rewrite grammar logic in Kotlin.

Do not use `harper.js` or `harper-wasm` for the native Android MVP.

## 5.3 Rust/Kotlin Boundary

Use UniFFI.

Expose a deliberately small API rather than mirroring all Harper Rust types.

Example conceptual interface:

```text
HarperEngine.create()
HarperEngine.lint(text, language)
HarperEngine.version()
HarperEngine.capabilities()
```

Exact UniFFI syntax may be chosen by the agent based on the compatible UniFFI version.

## 5.4 Build

Use:

- Android NDK
- Cargo
- cargo-ndk or a current equivalent
- arm64-v8a for production devices
- x86_64 for emulator/testing where needed

Target API:

```text
compileSdk >= 36
targetSdk = 36
```

Resolve the latest compatible stable Android Gradle Plugin, Kotlin, Compose BOM, and NDK versions during project setup instead of hard-coding obsolete versions from this document.

## 5.5 UI

Jetpack Compose for the app's own screens.

Accessibility overlays for the external-app experience.

## 5.6 Persistence

DataStore for preferences.

No database is required for MVP.

Add Room only if a future feature requires structured historical data.

---

# 6. AccessibilityService Design

## 6.1 Service Responsibilities

`HarperAccessibilityService` should only coordinate Android accessibility events.

It should:

- receive accessibility events
- identify candidate editable nodes
- notify the controller
- expose required lifecycle state
- delegate all other work

It should NOT:

- call Harper directly
- contain long-running grammar algorithms
- store raw text permanently
- render large UI components
- manage DataStore directly
- contain app-specific grammar logic

## 6.2 Configuration

Configure the service to retrieve interactive window content as required.

Use the narrowest event types and flags that satisfy the product.

Do not request unrelated accessibility capabilities.

Start from events such as:

- `TYPE_VIEW_FOCUSED`
- `TYPE_VIEW_TEXT_CHANGED`
- `TYPE_WINDOW_STATE_CHANGED`
- `TYPE_WINDOW_CONTENT_CHANGED` only when needed

Avoid processing every accessibility event indiscriminately.

## 6.3 Editable Node Detection

Create:

```text
EditableNodeTracker
```

Responsibilities:

- identify current focused editable node
- identify package name
- identify class/node metadata
- determine whether text can be read
- determine whether text can be replaced
- determine whether field is sensitive
- keep a stable identity for the current editing target

Candidate detection order:

```text
isFocused
     ↓
isEditable
     ↓
text available
     ↓
not sensitive
     ↓
package allowed
```

Handle both standard Android views and Compose-backed apps as far as accessibility metadata permits.

Do not assume every app exposes the same node structure.

---

# 7. TextSnapshot Model

Never pass around "just a String" when an edit can later be applied.

Create an immutable model containing enough information to validate the result.

Example:

```kotlin
data class TextSnapshot(
    val packageName: String,
    val nodeIdentity: NodeIdentity,
    val text: String,
    val selectionStart: Int?,
    val selectionEnd: Int?,
    val generation: Long,
    val capturedAtElapsedMs: Long
)
```

`NodeIdentity` may combine stable information available from the target node and current window context.

Do not depend on `hashCode()` of an accessibility node as a permanent identity.

The snapshot must represent the exact text that was analyzed.

---

# 8. Stale Result Protection

This is mandatory.

Example:

```text
generation 10
"This are good"
       ↓
Harper analysis starts

User types:
"This are really good"
       ↓
generation 11

generation 10 result returns
       ↓
DISCARD
```

Implement one or both:

1. generation IDs
2. exact text comparison before applying

Prefer both.

Application of a correction is valid only if:

```text
current node == snapshot node
AND
current text == snapshot text
AND
snapshot generation == current generation
```

If any check fails:

```text
reject correction
```

Never apply a stale correction.

---

# 9. Analysis Pipeline

Use a coroutine/Flow pipeline.

Conceptual flow:

```text
AccessibilityEvent
      ↓
candidate node
      ↓
extract text
      ↓
snapshot
      ↓
distinctUntilChanged
      ↓
debounce ~250–400 ms
      ↓
mapLatest
      ↓
Harper
      ↓
results
```

Use `mapLatest` or an equivalent cancellation mechanism so newer text invalidates older analyses.

Do not block the main thread.

Do not create an unbounded queue.

Do not launch one independent coroutine per keystroke without cancellation.

---

# 10. Text Processing Rules

Before calling Harper:

```text
if text.length < minimumThreshold
    skip

if protected field
    skip

if app excluded
    skip

if text unchanged
    skip
```

Avoid aggressive minimum-length rules that prevent legitimate short sentences. Keep the threshold small and configurable internally.

Use Harper's language capabilities rather than attempting crude language detection in the MVP.

Where language selection is unclear, default to the user's selected language.

---

# 11. Rust `harper-android` Adapter

Create an Android-specific Rust crate:

```text
rust/harper-android/
```

It should depend on:

```text
harper-core
```

Keep all Android FFI-facing structures simple.

Conceptual models:

```rust
pub struct LintResult {
    pub start: u32,
    pub end: u32,
    pub message: String,
    pub suggestions: Vec<String>,
}
```

Add fields only when useful to Android.

Do not expose internal Harper parser objects to Kotlin.

Do not serialize unnecessary internal structures.

## 11.1 Error handling

Never panic across the FFI boundary for normal user input.

Return structured errors:

```text
EngineError
```

and map them to Kotlin exceptions/result types.

A malformed or unsupported text input should fail gracefully, not crash the accessibility service.

---

# 12. UniFFI Boundary

The Kotlin layer should see a stable API such as:

```text
HarperEngine
  lint(text, language)
  version()
  capabilities()
```

Potential Kotlin domain model:

```kotlin
data class GrammarSuggestion(
    val start: Int,
    val end: Int,
    val message: String,
    val replacements: List<String>
)
```

Do not leak Rust implementation details such as parser handles, references, or internal lifetimes.

Treat this boundary as an API contract.

---

# 13. Correction Strategy

## MVP

Prefer replacing the entire field when that is the most reliable cross-app operation.

But before doing so:

```text
1. capture exact text
2. verify target node
3. verify current text
4. construct corrected text
5. apply
```

## V2

Implement minimal-range replacement when the target app supports reliable cursor/selection operations.

Conceptual transformation:

```text
old:
"I has a car"

lint:
start = 2
end   = 5
replacement = "have"

new:
"I have a car"
```

The correction engine must preserve unaffected text.

---

# 14. Applying Text

For editable nodes that support it, use the accessibility action intended to set text.

Conceptual operation:

```text
AccessibilityNodeInfo.performAction(
    ACTION_SET_TEXT,
    arguments
)
```

For each correction:

```text
fetch current node
        ↓
read current text
        ↓
compare with analyzed snapshot
        ↓
if identical:
    apply
else:
    reject
```

Never blindly overwrite a field after the user has changed it.

Where supported, preserve selection/cursor position.

If a target application does not support the required action reliably, record it as a compatibility limitation rather than adding unsafe fallback automation.

---

# 15. Overlay Architecture

Create:

```text
OverlayManager
```

with components:

```text
SuggestionIndicator
SuggestionPopup
OptionalInlineMarker
```

MVP UI:

```text
             target app

      ┌─────────────────────┐
      │ This are a test     │
      └─────────────────────┘
               ▲
               │
         suggestion icon
               │
               ▼
      ┌─────────────────────┐
      │ "are" → "is"        │
      │ [ Apply ] [ Ignore ]│
      └─────────────────────┘
```

Do not attempt exact Grammarly-style rendering first.

Make the first overlay robust and accessible.

---

# 16. Character-Level Positioning

For advanced visual feedback, use Android accessibility text-character location APIs when supported.

Desired pipeline:

```text
Harper lint range
       ↓
character range
       ↓
Accessibility character bounds
       ↓
screen coordinates
       ↓
overlay marker
```

The implementation must gracefully fall back when character-location data is unavailable.

Possible fallback order:

```text
character range
    ↓ unavailable
node bounds + heuristic position
    ↓ unreliable
generic indicator near field
```

Never block grammar checking because precise underline positioning is unavailable.

---

# 17. Overlay UX Rules

The overlay must:

- not capture taps unrelated to the suggestion
- disappear when the focused field changes
- disappear when the app changes
- disappear when the analyzed text changes substantially
- be keyboard-aware
- avoid blocking the typing cursor
- respect display cutouts and safe areas
- be dismissed by the user
- provide clear apply/ignore actions
- remain usable with TalkBack where practical

Avoid notification-like spam.

If there are many lints, group them into a single compact state rather than opening a new overlay for every error.

---

# 18. App Filtering

Implement:

```text
AppPolicy
```

with:

```text
Mode:
    ALLOWLIST
    BLOCKLIST
```

MVP default should be privacy-conscious.

Store package names, not app labels alone.

Example:

```text
com.example.editor
com.google.android.gm
```

Add a settings screen to manage the policy.

Do not inspect app content when the package is excluded.

---

# 19. Sensitive Field Detection

At minimum, inspect accessibility metadata indicating password or similar protected input.

Also recognize likely credential-related input types where the API exposes them.

Do not attempt to "guess" that a normal free-text field contains sensitive text and then silently process it.

The safest logic is:

```text
explicitly protected
    → never process

uncertain and high-risk
    → skip

normal text field
    → analyze
```

Document the limitations in `docs/privacy.md`.

---

# 20. Main App Screens

Build the following screens in Compose.

## 20.1 Onboarding

Explain:

- what the service does
- what accessibility access means
- what text is inspected
- that grammar analysis is local
- how to enable/disable the service

The disclosure must be prominent and understandable.

## 20.2 Home

Display:

```text
Service status
Enabled/Disabled
Current language
Supported/blocked apps
```

## 20.3 Settings

Include:

- enable/disable
- language
- app policy
- overlay toggle
- analysis behavior
- privacy information
- diagnostics

## 20.4 Privacy

Clearly describe:

```text
Text is analyzed locally.
Raw typed text is not persisted by default.
The service may inspect accessible text fields in selected applications.
Password/protected fields are excluded.
```

Do not make claims the implementation does not actually enforce.

---

# 21. Android Permission / Accessibility Flow

The app must not silently assume accessibility is enabled.

Flow:

```text
App launch
    ↓
check service state
    ↓
if disabled:
    show explanation
    ↓
user chooses to enable
    ↓
open Android Accessibility Settings
    ↓
user enables service
    ↓
return to app
    ↓
verify service state
```

Do not pretend that the accessibility service is enabled until verified.

---

# 22. Google Play Policy Requirements

Treat Google Play compliance as an explicit engineering deliverable.

The app should:

- use AccessibilityService only for functionality that genuinely depends on it
- provide required in-app prominent disclosure
- obtain affirmative user consent where required
- complete the Accessibility API declaration in Play Console
- document the exact accessibility use case
- avoid misleading `isAccessibilityTool` declarations
- request only the accessibility capabilities actually needed
- prefer narrower APIs when a narrower API can perform the function

Do not attempt to bypass Play policy through manifest flags or misleading classifications.

The coding agent must create:

```text
docs/accessibility.md
```

containing:

- service purpose
- API usage
- capabilities requested
- data processed
- disclosure wording
- user controls
- known limitations

---

# 23. Service Lifecycle

Account for:

- service connected/disconnected
- target app changed
- active window changed
- node becomes stale
- node no longer editable
- screen turns off
- user leaves app
- service interrupted
- process restarts

When a target becomes invalid:

```text
cancel analysis
clear overlays
clear current snapshot
release node references
```

Do not retain stale `AccessibilityNodeInfo` objects longer than needed.

Always reacquire the current node before applying a correction.

---

# 24. Threading Model

Use:

```text
Main thread
    ↓
UI and service event coordination

Default/background dispatcher
    ↓
text extraction that is safe to perform off main
Harper analysis

Main thread
    ↓
overlay updates
accessibility actions
```

Do not block the main thread with Rust analysis.

Do not perform large recursive accessibility-tree scans continuously.

Cache only short-lived references needed for current processing.

---

# 25. Performance Requirements

Initial targets:

```text
No visible typing lag
No unbounded analysis queue
No duplicate analysis of identical text
No raw-text logging
No analysis on main thread
```

Aim for:

```text
typical short text analysis: <150 ms
UI response after result: <100 ms where practical
```

These are engineering targets, not guarantees.

Measure them with benchmarks and real devices.

Add:

- Macrobenchmark for startup and key interactions
- Baseline Profile
- Startup Profile where useful

Only optimize after measuring.

---

# 26. Memory Requirements

Do not retain:

- complete document history
- old text snapshots indefinitely
- all past lint results
- all accessibility nodes in memory

Keep:

```text
current target
current snapshot
current generation
current result
```

and a small bounded cache if profiling proves it useful.

---

# 27. Error Handling

Expected failures must be non-fatal.

Examples:

```text
Harper engine initialization failure
        ↓
disable analysis
        ↓
show service-level diagnostic only

Accessibility node unavailable
        ↓
clear current target

ACTION_SET_TEXT rejected
        ↓
show "Unable to apply" state
        ↓
do not retry aggressively

FFI error
        ↓
recover service
```

Never crash the AccessibilityService because one target app exposed an unexpected node.

---

# 28. Testing Strategy

Testing is a first-class requirement.

## 28.1 Rust Tests

Test:

- engine creation
- valid linting
- malformed/empty input
- Unicode text
- long text
- multiple lints
- replacement ranges
- errors across FFI boundary

## 28.2 Kotlin Unit Tests

Test:

- debounce logic
- distinct text filtering
- generation handling
- snapshot validation
- protected-field filtering
- app policy
- correction transformation
- overlay state machine

## 28.3 Android Instrumentation

Test:

- service lifecycle
- accessibility configuration
- overlay permission/configuration
- target node detection
- text replacement behavior
- Activity navigation

## 28.4 Test Harness — No Full Keyboard Required

Do **not** build a full keyboard/IME or a production-style in-app writing editor just to test Harper.

Test Harper directly with Rust unit/integration tests. Use a minimal Android fixture only when a real accessibility node is required to test `AccessibilityService`. The fixture is a deterministic test target, not a second product.

The fixture may expose only representative controls:

```text
standard editable field
multiline editable field
password field
OTP/PIN-style field
Compose text field
multiple editable fields
rapid text-change scenario
large-text scenario
```

Test levels:

```text
Rust tests            → Harper correctness
Kotlin tests          → state/scheduling/correction logic
Android fixture      → AccessibilityService behavior
Real apps             → compatibility and UX
```

---

# 29. Accessibility Compatibility Matrix

Maintain:

```text
docs/compatibility.md
```

and test actual applications manually.

Example:

| App | Detect | Read | Lint | Overlay | Apply |
|---|---|---|---|---|---|
| Internal fixture | ✅ | ✅ | ✅ | ✅ | ✅ |
| Chrome | TBD | TBD | TBD | TBD | TBD |
| Gmail | TBD | TBD | TBD | TBD | TBD |
| WhatsApp | TBD | TBD | TBD | TBD | TBD |
| Telegram | TBD | TBD | TBD | TBD | TBD |
| Discord | TBD | TBD | TBD | TBD | TBD |
| Instagram | TBD | TBD | TBD | TBD | TBD |
| Google Docs | TBD | TBD | TBD | TBD | TBD |

Do not mark an app "supported" based only on reading text. Test correction and overlay behavior too.

---

# 30. Accessibility Overlay Compatibility

Test:

- portrait
- landscape
- split screen
- keyboard visible
- keyboard hidden
- different font scales
- display density
- screen cutouts
- multiple windows
- dark/light theme
- TalkBack
- rotation/configuration change

---

# 31. Security Requirements

The app must:

- use no unnecessary network permission
- not introduce a cloud dependency for grammar
- avoid raw-text telemetry
- avoid storing raw text
- avoid logging passwords
- avoid requesting unrelated Android permissions
- validate all correction inputs
- guard against stale-node writes
- keep the FFI boundary small
- pin critical dependency versions in release builds
- run dependency/security checks during CI

If network connectivity is added in a future version, it must be explicitly documented and must never be used for grammar processing by default without a new product/security review.

---

# 32. Dependency Management

Use stable, maintained versions at implementation time.

The agent must verify compatible current versions for:

```text
Android Gradle Plugin
Kotlin
Compose BOM
AndroidX
Coroutines
DataStore
NDK
Rust toolchain
UniFFI
```

Do not blindly copy versions from an old tutorial.

Commit a reproducible lock state where the ecosystem supports it.

Document minimum supported Android version separately from target SDK.

---

# 33. CI Pipeline

Create CI that runs:

```text
1. Kotlin formatting/checks
2. Kotlin unit tests
3. Rust fmt
4. Rust clippy
5. Rust tests
6. UniFFI generation/build
7. Android debug build
8. Instrumented tests when emulator infrastructure is available
9. Release build validation
```

Recommended quality gates:

```text
ktlint or equivalent → pass
detekt or equivalent → pass if adopted
cargo fmt --check → pass
cargo clippy → pass
cargo test → pass
assembleDebug → pass
```

Do not fail CI on harmless warnings unless the project chooses to enforce them.

---

# 34. Build Automation

The agent must create a repeatable command sequence.

Example conceptual commands:

```bash
# Rust
cargo fmt --check
cargo clippy --all-targets --all-features
cargo test

# Android
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Add project-specific commands for building Android Rust artifacts.

The README must contain a clean "from zero to APK" path.

---

# 35. Agent Workflow

The coding agent must work in this order.

## Step 0 — Inspect Environment

Before modifying code:

1. inspect repository
2. detect existing Gradle/Rust toolchains
3. inspect Harper version/API available
4. inspect installed Android SDK/NDK
5. check whether a Java/Kotlin/Rust bridge already exists
6. identify current stable dependency versions
7. identify all build constraints

Do not assume paths or versions.

Output an internal implementation map before coding.

## Step 1 — Create Skeleton

Create:

```text
android/
rust/harper-android/
docs/
tests/
```

Make the Android project compile.

Acceptance:

```text
./gradlew assembleDebug
```

passes.

## Step 2 — Accessibility Service

Implement the minimum service.

Acceptance:

```text
Open fixture app
focus EditText
service identifies node
service reads text
```

Do not add Harper yet.

## Step 3 — Rust Engine

Integrate:

```text
harper-core
```

through the Rust adapter and UniFFI.

Acceptance:

```text
Kotlin test input:
"This are a test"

Rust result contains:
replacement related to "are" → "is"
```

The exact returned wording/rule IDs may vary with the installed Harper release.

## Step 4 — Analysis Pipeline

Implement:

```text
event
→ snapshot
→ debounce
→ cancellation
→ Harper
```

Acceptance:

```text
rapid typing
```

does not cause stale results to appear.

## Step 5 — Correction

Implement snapshot validation and text application.

Acceptance:

```text
fixture field:
"This are a test"

tap suggestion

result:
"This is a test"
```

Also test:

```text
type something else while analysis is running
```

and verify the old result is rejected.

## Step 6 — Overlay

Implement:

```text
indicator
popup
apply
ignore
dismiss
```

Acceptance:

- popup appears near the target
- does not prevent normal typing
- disappears when field/app changes
- apply updates text
- ignore removes suggestion

## Step 7 — Security/Privacy

Implement:

- protected field filtering
- app allow/block policy
- no raw text logs
- no raw text persistence
- privacy screen

Acceptance:

```text
password field → no Harper call
OTP field → no Harper call
blocked package → no Harper call
```

## Step 8 — Performance

Measure:

- startup
- text extraction
- analysis latency
- overlay latency
- memory
- typing responsiveness

Then add Baseline Profile/Startup Profile where useful.

## Step 9 — Compatibility

Run the test matrix against the selected target apps.

Do not label unsupported apps as compatible.

## Step 10 — Release Hardening

Before release:

```text
release build
shrinking/proguard validation if enabled
native library packaging
privacy review
accessibility disclosure review
Play Console declaration checklist
```

---

# 36. Definition of Done — MVP

MVP is complete only when ALL are true:

## Architecture

- [ ] Kotlin/Compose app builds
- [ ] AccessibilityService is isolated from business logic
- [ ] Harper engine is isolated behind `HarperEngine`
- [ ] Rust uses `harper-core`
- [ ] Kotlin/Rust boundary uses UniFFI
- [ ] DataStore stores only settings
- [ ] No raw text persistence

## Functional

- [ ] Detect focused editable node
- [ ] Read text
- [ ] Debounce changes
- [ ] Cancel stale analyses
- [ ] Run Harper
- [ ] Display suggestion
- [ ] Apply correction
- [ ] Ignore correction
- [ ] Clear state on field/app change

## Security

- [ ] Password fields skipped
- [ ] OTP/PIN fields skipped where detectable
- [ ] Excluded packages skipped
- [ ] Raw text not logged
- [ ] No unnecessary network dependency

## Reliability

- [ ] Service survives target app changes
- [ ] Service survives stale nodes
- [ ] Failed replacements do not corrupt text
- [ ] FFI failures do not crash the service
- [ ] Tests pass

## UX

- [ ] Onboarding explains accessibility access
- [ ] Privacy explanation is accessible
- [ ] Service state is visible
- [ ] Settings are usable
- [ ] Overlay is dismissible

---

# 37. Definition of Done — V2

V2 may be considered complete when:

- [ ] minimal-range replacement works reliably
- [ ] character-level positioning is used where supported
- [ ] inline indicators are implemented
- [ ] multi-lint navigation works
- [ ] app compatibility coverage improves
- [ ] performance targets are measured and documented
- [ ] Baseline/Startup Profiles are included
- [ ] TalkBack behavior is reviewed
- [ ] configuration is resilient across device rotations/windows

---

# 38. Future Architecture

Keep the architecture ready for these without implementing them in MVP:

```text
Future:
    rewriting
    tone suggestions
    paraphrasing
    custom dictionaries
    writing statistics
    additional languages
    user correction memory
    optional cloud AI
    per-app settings
```

Future AI services must remain behind a separate interface:

```text
GrammarEngine
      │
      ├── HarperLocalEngine
      │
      └── OptionalRemoteEngine
```

Do not contaminate the core AccessibilityService with AI provider logic.

---

# 39. Important Design Principle

The Android application is another adapter around Harper, not a fork of the grammar engine.

Think:

```text
                    Harper Core
                        │
      ┌─────────────────┼─────────────────┐
      │                 │                 │
     WASM               LSP             Native
      │                 │                 │
    Web/JS             Editors         Desktop/Android
```

For this Android project:

```text
AccessibilityService
        ↓
Android adapter
        ↓
Harper native Rust
        ↓
harper-core
```

The target platform changes. The grammar intelligence does not.

---

# 40. What NOT to Build

The agent must not introduce these shortcuts:

```text
❌ OCR
❌ screenshot parsing
❌ WebView for Harper
❌ harper.js as the native Android engine
❌ a second grammar engine written in Kotlin
❌ large handwritten JNI layer
❌ automatic correction without confirmation
❌ cloud grammar processing
❌ raw text analytics
❌ raw text database
❌ unrestricted accessibility event processing
❌ storing every AccessibilityNodeInfo
❌ blindly applying stale corrections
❌ fake AccessibilityService tool classification
❌ requesting accessibility capabilities unrelated to the feature
❌ foreground service merely to keep the app alive
```

---

# 41. Agent Coding Rules

The autonomous agent must:

1. Make small, compilable changes.
2. Run relevant tests after each phase.
3. Never proceed past a failed architectural acceptance test without resolving it.
4. Keep Rust and Kotlin responsibilities separate.
5. Keep AccessibilityService thin.
6. Preserve privacy constraints even during debugging.
7. Never log raw text to "make debugging easier."
8. Prefer robust fallbacks over assumptions about target apps.
9. Document compatibility failures instead of hiding them.
10. Keep external dependencies minimal.
11. Verify current API/dependency behavior against official documentation when implementation details may have changed.
12. Do not silently change the product requirements in this document.

---

# 42. Recommended First Implementation Slice

Do not start with the complete UI.

Build exactly this:

```text
Internal fixture app
        │
        ▼
EditText
        │
        ▼
Harper Android AccessibilityService
        │
        ▼
TextSnapshot
        │
        ▼
300 ms debounce
        │
        ▼
UniFFI
        │
        ▼
Rust
        │
        ▼
harper-core
        │
        ▼
Lint
        │
        ▼
Console/debug result
```

Once that works:

```text
Lint
 ↓
Overlay
 ↓
Apply
```

Then:

```text
Compatibility
 ↓
Privacy hardening
 ↓
Performance
 ↓
Release
```

This minimizes debugging complexity and gives the agent a deterministic sequence of milestones.

---

# 43. Final Acceptance Scenario

The final MVP demonstration must show:

### Scenario A — Normal sentence

```text
Target: internal fixture EditText

Input:
"This are a test"

Expected:
Harper detects a grammar issue.

Expected UI:
Suggestion is visible.

User action:
Tap Apply.

Expected text:
"This is a test"
```

### Scenario B — Stale result

```text
Input:
"This are a test"

Analysis begins.

User changes field to:
"This are a completely different sentence"

Old analysis returns.

Expected:
Old correction is discarded.
No incorrect replacement occurs.
```

### Scenario C — Password

```text
Password field focused.

Expected:
No text is sent to Harper.
No overlay is shown.
```

### Scenario D — Blocked app

```text
Blocked package focused.

Expected:
No grammar analysis.
No overlay.
```

### Scenario E — App switch

```text
Field in App A active.
Suggestion visible.

User opens App B.

Expected:
App A overlay disappears.
App A snapshot becomes invalid.
No App A correction may be applied.
```

---

# 44. Official References to Consult During Implementation

The agent should verify current details against official sources before finalizing implementation, especially because Android and Google Play behavior changes over time.

Android:

- AccessibilityService:
  https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- AccessibilityNodeInfo:
  https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo
- Accessibility development:
  https://developer.android.com/guide/topics/ui/accessibility/service
- Android app architecture:
  https://developer.android.com/topic/architecture
- DataStore:
  https://developer.android.com/topic/libraries/architecture/datastore
- NDK:
  https://developer.android.com/ndk
- App startup / Baseline Profiles:
  https://developer.android.com/topic/performance/baselineprofiles/overview

Google Play:

- AccessibilityService policy:
  https://support.google.com/googleplay/android-developer/answer/10964491
- Restricted permissions / Accessibility policy:
  https://support.google.com/googleplay/android-developer/

Harper:

- Repository:
  https://github.com/Automattic/harper
- Architecture:
  https://writewithharper.com/docs/contributors/architecture

Rust/UniFFI:

- UniFFI:
  https://mozilla.github.io/uniffi-rs/

---

# 45. Final Architecture Summary

```text
┌──────────────────────────────────────────────────────────┐
│                    Android / Kotlin                      │
│                                                          │
│ Compose UI                                               │
│ ViewModels                                               │
│ DataStore                                                │
│                                                          │
│ AccessibilityService                                    │
│     ↓                                                    │
│ AccessibilityController                                 │
│     ↓                                                    │
│ EditableNodeTracker                                     │
│     ↓                                                    │
│ TextSnapshot + Generation                               │
│     ↓                                                    │
│ Debounce + mapLatest                                    │
│     ↓                                                    │
│ GrammarRepository                                       │
│     ↓                                                    │
│ HarperEngine                                             │
│     ↓                                                    │
│ UniFFI                                                   │
└───────────────────────┬──────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────┐
│                       Rust                               │
│                                                          │
│ harper-android                                           │
│     ↓                                                    │
│ harper-core                                              │
│     ↓                                                    │
│ Lint / Suggestions                                      │
└───────────────────────┬──────────────────────────────────┘
                        │
                        ▼
                OverlayManager
                        │
             ┌──────────┴──────────┐
             ▼                     ▼
        Indicator              Popup
                                   │
                              user action
                                   │
                                   ▼
                         Snapshot validation
                                   │
                                   ▼
                    Accessibility ACTION_SET_TEXT
```

**Core rule:** Keep the Android layer responsible for Android, the Rust layer responsible for grammar analysis, and the UI layer responsible for presentation. The interface between them should stay small, explicit, testable, and privacy-preserving.


# 53. Additional Mandatory Architecture

These requirements refine the earlier architecture and are part of the final implementation contract.

## 53.1 InputProvider Abstraction

Do not couple the grammar pipeline directly to AccessibilityService.

```text
InputProvider
├── AccessibilityTextInputProvider
└── FutureKeyboardTextInputProvider
```

Common pipeline:

```text
InputProvider → TextSnapshot → AnalysisScheduler → GrammarEngine → Suggestions → Presentation
```

The keyboard provider is only an architectural extension point. **Do not build a full keyboard/IME for MVP.**

## 53.2 InputSessionManager

Introduce a dedicated session owner between the accessibility layer and scheduler.

Responsibilities:

- establish the current editing target
- invalidate it when package/window/node changes
- own the generation counter
- publish immutable snapshots
- clear state on lifecycle changes

## 53.3 CorrectionPlanner

Separate deciding what to change from actually editing the target.

```text
HarperResult → CorrectionPlanner → ValidatedCorrection → CorrectionApplier
```

`CorrectionPlanner` validates ranges and replacement semantics. `CorrectionApplier` performs only a validated Android edit.

## 53.4 Explicit Editing State Machine

Prefer a typed state model over many independent mutable flags.

```kotlin
sealed interface EditingState {
    data object Idle : EditingState
    data class Tracking(val snapshot: TextSnapshot) : EditingState
    data class Analyzing(val snapshot: TextSnapshot) : EditingState
    data class Suggestions(
        val snapshot: TextSnapshot,
        val suggestions: List<GrammarSuggestion>
    ) : EditingState
}
```

The exact model may differ, but impossible states should be difficult to represent.

## 53.5 TargetIdentity + TextIdentity

Do not rely on generation alone.

Track:

```text
TargetIdentity
- packageName
- windowId
- node identity information

TextIdentity
- exact text
- text hash
- selection
- generation
```

Before applying a correction, re-resolve the target and verify the current text again.

## 53.6 UTF-16 Offset Contract

Android/Kotlin string positions use UTF-16 code units; Rust string offsets are commonly byte-based UTF-8 offsets. Never pass Rust byte offsets directly to Android.

The FFI contract should expose Android-compatible offsets such as:

```text
startUtf16
endUtf16
```

The Rust adapter owns the conversion.

Mandatory tests:

```text
ASCII
accented characters
emoji
emoji sequences
CJK
Arabic
combining characters
mixed scripts
```

## 53.7 App Capability Model

Do not classify an application simply as supported/unsupported.

```kotlin
data class AppCapabilities(
    val canReadText: Boolean,
    val canDetectChanges: Boolean,
    val canSetText: Boolean,
    val canSetSelection: Boolean,
    val canGetCharacterBounds: Boolean,
    val overlayWorks: Boolean
)
```

Record observed capability separately from assumptions. Partial support is valid.

## 53.8 CharacterLocationProvider

Keep visual positioning behind an interface:

```text
CharacterLocationProvider
├── Api36CharacterLocationProvider
└── FallbackLocationProvider
```

Re-resolve the node before requesting character coordinates. If exact locations are unavailable, fall back to a field-level indicator instead of disabling grammar analysis.

## 53.9 Read-Only Developer Mode

Provide a mode that runs acquisition, analysis, and overlays but disables editing:

```text
Accessibility → Harper → Suggestions → Overlay
                                      ↘ no edit
```

Use this first when evaluating a new target app.

## 53.10 Emergency Kill Switch

Provide a global pause/disable mechanism that immediately stops:

- text acquisition
- analysis
- overlays
- correction

A temporary current-app pause is useful as well.

---

# 54. Accessibility Event and Back-Pressure Policy

## 54.1 Narrow Event Selection

Do not use broad coverage such as `typeAllMask` unless a measured compatibility issue proves it necessary.

Start with the narrowest useful set, typically:

```text
TYPE_VIEW_FOCUSED
TYPE_VIEW_TEXT_CHANGED
TYPE_WINDOW_STATE_CHANGED
```

Add other events only with a documented reason.

## 54.2 Runtime Service Configuration

Where appropriate, update `AccessibilityServiceInfo` at runtime when settings change, including:

- package filters
- event types
- flags
- notification timeout

This allows unnecessary processing to be reduced when the user excludes apps or disables functionality.

## 54.3 Notification Timeout

Use service-level notification throttling as coarse back-pressure, then apply application-level filtering and cancellation.

```text
Android event throttling
    ↓
cheap filtering
    ↓
snapshot/text comparison
    ↓
debounce
    ↓
mapLatest / cancellation
    ↓
Harper
```

---

# 55. Privacy Invariant Test Suite

Privacy must be enforced by automated tests, not only documentation.

Required assertions:

```text
password field   → Harper calls = 0
OTP/PIN field    → Harper calls = 0
blocked app      → Harper calls = 0
service disabled → Harper calls = 0
read-only mode   → correction calls = 0
normal field     → Harper may be called
```

Use a fake `GrammarEngine` in Kotlin tests. Verify logs and diagnostics contain metadata only and never raw input.

---

# 56. Large-Text Processing Policy

Define bounded behavior for normal, large, and extremely large text fields.

Do not choose arbitrary thresholds from a tutorial. Benchmark Harper on actual Android hardware and select practical limits.

Oversized text must degrade gracefully instead of causing typing stalls or unbounded memory use.

---

# 57. No Clipboard Correction Fallback

Do **not** implement copy/modify/paste as a general editing fallback.

Clipboard automation creates privacy exposure, clipboard interference, race conditions, and unpredictable target-app behavior.

Prefer supported accessibility editing actions. If reliable editing is unavailable, expose the target as read-only/limited.

---

# 58. Native API / Schema Versioning

Expose stable metadata from the Rust bridge:

```text
engineVersion()
schemaVersion()
capabilities()
```

At startup, verify that Kotlin and the native library agree on the expected schema. Fail gracefully on mismatch.

---

# 59. Real-Pipeline Performance Measurement

Measure the actual assistant path, not only app startup:

```text
Accessibility event
    → snapshot
    → Harper start
    → Harper finish
    → overlay visible
```

Store only timing/metadata in debug telemetry, for example:

```text
analysis_ms=73
chars=128
lint_count=2
```

Never record the sentence itself.

Use Macrobenchmark, Baseline Profiles, and Startup Profiles where they provide measurable benefit.

---

# 60. Play Policy Release Gate

Accessibility policy review is a release blocker.

Before production release, verify:

- the product purpose and accessibility use case are documented
- `isAccessibilityTool` is truthful and appropriate
- prominent disclosure exists where required
- affirmative consent exists where required
- Play Console accessibility declaration requirements are satisfied
- only necessary accessibility capabilities are requested
- the implementation does not perform prohibited autonomous actions
- privacy documentation exactly matches implementation behavior

Never add misleading classifications or manifest flags to improve approval chances.

---

# 61. Testing Strategy — No Full Keyboard

The project does **not** need a full keyboard, custom IME, or polished in-app editor to validate Harper.

Use four levels:

```text
Level 1 — Rust
Direct Harper inputs and expected lints

Level 2 — Kotlin
Snapshots, UTF-16 mapping, scheduling, state machine, correction planning

Level 3 — Minimal Android fixture
Real accessibility nodes for service/integration tests

Level 4 — Real apps
Compatibility and UX testing
```

The fixture is intentionally small and exists only to exercise Android accessibility behavior.

---

# 62. Revised First Implementation Slice

Do not begin with the complete settings UI, keyboard, or production editor.

Start here:

```text
Rust test input
    ↓
harper-core
    ↓
expected lint
```

Then:

```text
Minimal Android fixture
    ↓
AccessibilityService
    ↓
TextSnapshot
    ↓
300 ms debounce
    ↓
UniFFI
    ↓
Rust harper-core
    ↓
Lint result
```

Then:

```text
Lint
 ↓
Overlay
 ↓
User action
 ↓
Target re-resolution
 ↓
Snapshot validation
 ↓
ACTION_SET_TEXT
```

Only after this path is reliable should the agent expand UI polish or application coverage.

---

# 63. Revised Agent Task Queue

Execute in order:

```text
A0  Policy + architecture gate
A1  Android skeleton
A2  Minimal AccessibilityService
A3  Rust harper-core adapter
A4  UniFFI bridge
A5  InputProvider + InputSessionManager
A6  TextSnapshot + TargetIdentity + UTF-16 contract
A7  Event filtering + notification timeout + scheduler
A8  Harper analysis pipeline
A9  Editing state machine
A10 CorrectionPlanner
A11 CorrectionApplier + stale-result protection
A12 OverlayManager
A13 Protected-field/app policy
A14 Privacy invariant tests
A15 Read-only developer mode + kill switch
A16 Capability model + compatibility matrix
A17 CharacterLocationProvider
A18 Large-text policy + performance instrumentation
A19 Baseline/Startup Profiles + Macrobenchmark
A20 Release/privacy/Play policy gate
```

Each task must end with a measurable acceptance test. Do not jump ahead while A3–A11 are unstable.

---

# 64. Additional MVP Acceptance Criteria

- [ ] InputProvider abstraction exists
- [ ] Accessibility is isolated from the grammar engine
- [ ] InputSessionManager owns the editing session
- [ ] TargetIdentity and TextIdentity are checked before edits
- [ ] UTF-16 offsets are explicit and tested
- [ ] CorrectionPlanner is separate from CorrectionApplier
- [ ] editing state is explicit
- [ ] event types are narrowly filtered
- [ ] notification timeout is configured
- [ ] app capabilities are represented
- [ ] read-only mode exists
- [ ] no clipboard fallback exists
- [ ] privacy invariants are automated
- [ ] large-text behavior is bounded
- [ ] engine/schema compatibility is checked
- [ ] no full keyboard or production editor was created solely for testing

---

# 65. Final Engineering Priority

When this document leaves a decision open, prioritize in this order:

```text
1. User privacy and safety
2. No incorrect/stale edits
3. Android and Google Play compliance
4. Real-app compatibility
5. Typing responsiveness
6. Kotlin/Rust separation
7. Maintainability
8. Feature breadth
```

A visually impressive feature must not be prioritized above correctness, privacy, lifecycle safety, or policy compliance.

Keep Harper independently reusable. Android should remain an adapter around the grammar engine, not a fork of its language intelligence.
