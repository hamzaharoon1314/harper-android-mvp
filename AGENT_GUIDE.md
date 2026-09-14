# Harper Android — Full harper-core Integration Agent Guide

## Purpose

This document is the implementation contract and phased roadmap for turning the current Harper Android MVP into a production-quality Android host for the real `harper-core` engine.

The goal is **not** to reimplement Harper on Android. The Android application should provide a strong, privacy-preserving host/integration layer while using the real Harper engine for language analysis.

The end state should support, as far as the selected compatible `harper-core` version allows:

- Real Harper curated grammar and spelling linting
- Correct use of Harper dictionaries
- Dialect selection
- Configurable/ignored lints
- User dictionary support
- All supported suggestion/edit semantics, not only replacement strings
- Stable Android-facing result models
- Multiple simultaneous lints
- A UI that lets the user inspect and select among suggestions
- Robust accessibility-service event filtering
- Correct UTF-16 offsets and Unicode behavior
- Correct handling of IME composition and rapidly changing text
- Background/native analysis without blocking the Android main thread
- Cancellation/stale-result protection
- Persistent/reusable Rust engine state
- A clean compatibility boundary between Android and `harper-core`
- Privacy/security protections for password and sensitive fields
- Compatibility policies for apps/editors where Android accessibility behavior is unreliable
- Strong automated tests, benchmarks, and release gates

## Non-Negotiable Operating Rules for the Agent

1. **Use the real `harper-core`.** Do not rewrite grammar/spelling logic in Kotlin.
2. **Do not expose Harper internals directly through UniFFI unless necessary.** Create a stable Android-facing Rust adapter/domain model.
3. **Verify the actual dependency version at implementation time.** Do not blindly upgrade to an assumed “latest” version. Check `Cargo.lock`, the configured registry/source, upstream release/source, and Android build compatibility.
4. **Use current upstream documentation/source when a Harper API is uncertain or has changed.** The agent must verify APIs before changing integration code.
5. **Do not block Android's main/UI thread with synchronous native linting.** CPU/native analysis belongs on a dedicated background execution path.
6. **Do not discard supported suggestion semantics.** Preserve replacement, insertion, removal, and any additional supported operations present in the selected `harper-core` version.
7. **Do not use array indexes as stable issue IDs.** IDs must remain stable enough for UI interaction and stale-result validation.
8. **Do not assume `ACTION_SET_TEXT` is universally reliable.** Put text correction behind a correction strategy abstraction and test per app/editor.
9. **Do not use clipboard scraping, screenshots, OCR, or screen-reading hacks as the default correction path.** Only introduce exceptional fallbacks with explicit compatibility justification.
10. **Do not log user-entered text.** Logs should be metadata-oriented and privacy-safe.
11. **Keep architecture incremental-ready but do not prematurely build complex incremental parsing.** First build correct debounced snapshot analysis; later optimize using measurements.
12. **Every phase has a gate.** Do not start the next phase until the current phase's tests and acceptance criteria pass.
13. **Prefer small commits and small implementation steps.** Avoid giant refactors that combine unrelated behavior changes.
14. **Measure before optimizing.** Record latency, allocations/memory where practical, cancellation behavior, and event rates before changing architecture for performance.
15. **Preserve upstream compatibility.** When `harper-core` changes, isolate adaptation changes in the Rust compatibility layer whenever practical.

---

# 1. Current MVP: What Exists

The current project has a sound foundation:

```text
Android AccessibilityService
        |
        v
Editable node tracking / snapshot
        |
        v
Kotlin debounce + repository
        |
        v
UniFFI
        |
        v
Rust harper-android bridge
        |
        v
harper-core
        |
        v
LintResult -> Kotlin -> overlay/correction
```

The current Rust bridge already uses the real Harper engine and curated linting, conceptually along these lines:

```rust
let dict = Arc::new(harper_core::spell::FstDictionary::curated());
let doc = Document::new_plain_english(&text, &*dict);
let mut linter = harper_core::linting::LintGroup::new_curated(
    dict,
    harper_core::Dialect::American,
);
let lints = linter.lint(&doc);
```

That means the project is **not** a fake/reimplemented grammar engine. It already uses real `harper-core` functionality.

However, the integration surface is still narrow.

## Current limitations to eliminate

| Area | Current state | Target |
|---|---|---|
| Real Harper core | Yes | Keep |
| Curated linting | Yes | Keep/reuse efficiently |
| Grammar | Yes | Keep |
| Spelling | Yes | Keep |
| Dictionary reuse | Rebuilt on lint call | Persistent/reusable |
| Engine state | Stateless | Stateful session/engine |
| Dialect | Hard-coded American | Configurable |
| Language parameter | Passed from Kotlin but ignored | Explicitly modeled/configured |
| Lint configuration | Minimal | Full supported configuration surface |
| Ignored rules | Missing | Supported |
| User dictionary | Missing | Supported |
| Suggestion semantics | Only non-empty `ReplaceWith` strings are exposed | Preserve all supported suggestion operations |
| Rich lint metadata | Minimal | Stable Android-facing issue model |
| Document/parser abstraction | Plain English only | Extensible, correct mode selection |
| Analysis thread | Current flow can execute native linting on Main | Dedicated background execution |
| Cancellation | Kotlin `mapLatest` alone | End-to-end cancellation/stale-result strategy |
| Multi-lint overlay | Basic | Rich list/anchor UI |
| Correction | `ACTION_SET_TEXT` | Strategy abstraction + selection-safe behavior |
| Event filtering | Basic | IME/composition-aware filtering |
| Result identity | Position based | Stable issue IDs |
| Config persistence | Limited | Centralized persistent settings |
| Benchmarking | Limited | Regression benchmark suite |

---

# 2. Definition of “Full harper-core” for This Project

“Use all of Harper” does **not** mean exposing every internal Rust type through UniFFI.

It means the app should use the user-relevant functionality offered by the selected Harper core version and avoid unnecessarily throwing away information.

## Required capabilities

### Analysis

- Real `harper-core` linter pipeline
- Curated lint group
- Correct dictionary shared between document/parser and linter
- Configurable dialect
- Configurable lint enable/disable state where supported
- Ignored lint/rule support where supported
- User dictionary support where supported
- Correct document/parser mode for the text being analyzed

### Suggestions

The Rust adapter must preserve the semantics of supported `harper-core::linting::Suggestion` variants.

At minimum, for versions that expose them, handle:

- `ReplaceWith`
- `InsertAfter`
- `Remove`

If the selected version adds more variants, the compatibility layer must explicitly account for them rather than silently dropping them.

### Integration contract

The Android side should see stable domain types such as:

```text
HarperEngine
AnalysisRequest
AnalysisResult
HarperLint
HarperSuggestion
EditOperation
HarperConfig
DictionaryEntry
Dialect
```

The Android app should not become coupled to every changing internal Harper Rust type.

---

# 3. Target Architecture

```text
┌──────────────────────────────────────────────────────┐
│                    Android UI                        │
│ Settings / Overlay / Suggestion picker / Status     │
└──────────────────────┬───────────────────────────────┘
                       │
                       v
┌──────────────────────────────────────────────────────┐
│           Accessibility Integration Layer            │
│ Event filtering / node tracking / IME handling      │
└──────────────────────┬───────────────────────────────┘
                       │ TextSnapshot / AnalysisRequest
                       v
┌──────────────────────────────────────────────────────┐
│              Analysis Coordinator                    │
│ debounce / scheduling / cancellation / generations │
└──────────────────────┬───────────────────────────────┘
                       │
                       v
┌──────────────────────────────────────────────────────┐
│         Harper Android Rust Compatibility API        │
│ stable UniFFI types / edit operations / config      │
└──────────────────────┬───────────────────────────────┘
                       │
                       v
┌──────────────────────────────────────────────────────┐
│                  Harper Engine                       │
│ persistent dictionary / config / lint group         │
└──────────────────────┬───────────────────────────────┘
                       │
                       v
┌──────────────────────────────────────────────────────┐
│                    harper-core                       │
│ Document / Parser / Dictionary / LintGroup / lints  │
└──────────────────────────────────────────────────────┘
```

## Key architectural rule

The boundary must remain:

```text
Android
   -> HarperAndroid stable API
   -> Rust compatibility/adaptation layer
   -> harper-core
```

Do not make the app directly depend on unstable internal Harper implementation details unless there is no practical alternative.

---

# 4. Recommended Project Structure

Target structure should evolve toward something similar to:

```text
android/
  app/
    ...
  accessibility/
    HarperAccessibilityService.kt
    EditableNodeTracker.kt
    InputEventClassifier.kt
    ProtectedFieldDetector.kt
    AppPolicy.kt
    ImeCompositionTracker.kt
  analysis/
    AnalysisCoordinator.kt
    AnalysisRepository.kt
    AnalysisModels.kt
    AnalysisState.kt
    AnalysisScheduler.kt
  correction/
    CorrectionApplier.kt
    CorrectionStrategy.kt
    AccessibilityCorrectionStrategy.kt
    CorrectionModels.kt
  overlay/
    OverlayManager.kt
    OverlayController.kt
    OverlayPositioner.kt
    LintPopup.kt
    SuggestionList.kt
  settings/
    HarperSettings.kt
    HarperSettingsRepository.kt
    SettingsModel.kt
  security/
    SensitiveContentPolicy.kt
    PrivacyLogger.kt
  compatibility/
    AppCompatibilityPolicy.kt

rust/harper-android/
  src/
    lib.rs
    engine.rs
    config.rs
    dictionary.rs
    models.rs
    suggestions.rs
    edit_operations.rs
    document.rs
    compatibility.rs
    errors.rs
    tests/
```

Names may differ; responsibilities must not become tangled.

---

# 5. Core Data Models

## TextSnapshot

Represents the exact text state used for an analysis.

```text
TextSnapshot
- nodeIdentity
- packageName
- windowIdentity if available
- text
- selectionStart
- selectionEnd
- generation
- capturedAt
- fieldClassification
- appCompatibilityMode
```

Never apply an analysis result to a node unless the current field still matches the relevant snapshot/generation requirements.

## AnalysisRequest

```text
AnalysisRequest
- snapshot
- language/dialect configuration
- lint configuration version
- dictionary version if applicable
- document mode
- request ID
```

## HarperLint

At minimum:

```text
HarperLint
- issueId
- startUtf16
- endUtf16
- message
- ruleId/ruleName if the selected core exposes stable identifying information
- suggestions
```

## HarperSuggestion

```text
HarperSuggestion
- suggestionId
- displayText
- operation
```

Where operation is one of the supported edit forms, for example:

```text
ReplaceWith(text)
InsertAfter(text)
Remove
```

Do not reduce all suggestions to strings.

## EditOperation

```text
EditOperation
- startUtf16
- endUtf16
- operation
- expectedOriginalText
- replacement/inserted text if applicable
```

This allows the UI to remain independent from the internal Harper suggestion enum.

---

# 6. Unicode and Offset Rules

Android text APIs use UTF-16 indexing. Harper/Rust internals may operate using character or other indexing semantics depending on API.

The bridge must convert offsets correctly and centrally.

Do not scatter UTF-16 conversion logic across Android classes.

Required test cases include:

- ASCII
- accented Latin characters
- combining marks
- emoji
- emoji sequences
- supplementary Unicode code points
- mixed scripts
- replacement at start/middle/end
- zero-length insertion
- deletion
- multiple edits in one text state

Example principle:

```text
Harper/core offset representation
          |
          v
Rust adapter conversion
          |
          v
UTF-16 Android offset
```

The conversion must be tested independently.

---

# 7. Performance Architecture

Harper is expected to behave like a real-time editor engine. The app must treat latency as a first-class feature.

## Current critical issue

The Kotlin repository currently has a Main-thread coroutine scope and calls the native `engine.lint(...)` from the flow transformation. `mapLatest` alone does **not** guarantee that CPU/native work executes off the main thread.

This must be corrected first.

## Required model

```text
Accessibility event
        |
        v
cheap filtering on Main
        |
        v
snapshot creation
        |
        v
background analysis scheduler
        |
        v
Rust/Harper analysis
        |
        v
result validation
        |
        v
Main-thread UI update
```

Do not perform heavyweight linting on Main.

## Cancellation rule

Kotlin coroutine cancellation is not automatically equivalent to cancelling a synchronous Rust call already in progress.

Therefore:

1. Make obsolete requests easy to discard.
2. Use generation/request IDs.
3. Ensure the latest text wins.
4. Where practical, make the Rust boundary cancellation-aware.
5. If true mid-lint cancellation cannot be provided by the core API, keep the call on a dedicated worker and discard its result when stale.

Do not claim “cancelled” merely because `mapLatest` cancelled the Kotlin continuation.

## Input-size limits

Introduce a configurable/defensible maximum analysis size.

The exact default must be chosen from measurement and upstream behavior rather than an arbitrary tiny number.

For very large fields:

- avoid repeated full-document analysis
- consider truncation or a compatibility policy only when correct for the UX
- expose metrics so the limit can be tuned

Do not silently corrupt user text to improve performance.

## Performance metrics

Measure at least:

- event rate per second
- debounce-to-start delay
- native lint duration
- end-to-end analysis latency
- overlay update latency
- stale result percentage
- correction success/failure rate
- peak/steady memory where practical
- analysis time by text length bucket

Keep baseline numbers before optimization.

---

# 8. Accessibility Event Architecture

Accessibility events are noisy. Not every event should trigger a lint request.

The filtering path should distinguish:

- focus changes
- actual text mutation
- selection-only changes
- cursor movement
- IME/composition updates
- password/sensitive fields
- unsupported application/editor contexts
- stale events from previously focused nodes

## Event processing rule

Only schedule analysis when all required conditions are true, for example:

```text
Current node is relevant
AND editable
AND allowed by security policy
AND allowed by app policy
AND text changed meaningfully
AND not currently in a protected composition state
AND text size is acceptable
```

Do not use a blanket “every TYPE_VIEW_TEXT_CHANGED triggers a lint” strategy.

---

# 9. IME Composition Must Be a Dedicated Concern

Text composition is different from ordinary committed text.

Many keyboards and editors build words in stages. The app must not aggressively rewrite composing text while the IME is still constructing it.

Introduce an explicit composition-aware state such as:

```text
StableText
Composing
CommitPending
Committed
```

The exact Android implementation should be based on what information the accessibility framework and target editor actually expose.

Requirements:

- detect likely composition changes where possible
- avoid disruptive corrections during active composition
- re-analyze after composition/commit
- test with multiple keyboard/input methods and several editor apps

Do not assume every editor behaves like a simple `EditText`.

---

# 10. Overlay Architecture

The overlay should evolve from a simple notification into a real multi-lint suggestion surface.

## Required UX

When multiple lints are present, users should be able to inspect them without losing the context of the original text field.

A practical flow is:

```text
small anchor / indicator
        |
        v
lint card / suggestion panel
        |
        +--> issue message
        +--> all suggestions
        +--> dismiss
        +--> navigate to next/previous issue
```

The UI must not assume one lint per field.

## Multi-suggestion requirements

For each lint:

- show the rule/message clearly
- show all supported suggestions
- distinguish replacement/insertion/removal when useful
- allow user selection
- allow dismissing the lint
- remain usable with multiple lints

Do not silently show only the first suggestion.

## Coordinate separation

Keep these concepts separate:

```text
Text coordinates
    -> UTF-16 text offsets

Accessibility/node coordinates
    -> bounds in window/screen coordinates

Overlay coordinates
    -> actual overlay positioning space
```

Do not mix text offsets with screen geometry.

Where supported, use node/window bounds APIs appropriate to the accessibility service and display/window context.

The positioning component should own the conversion.

## Overlay edge cases

Test:

- portrait/landscape changes
- scrolling
- multi-window
- split-screen
- display changes
- IME visible/hidden
- fields near screen edges
- very small/large fields
- multiple lints close together
- overlay rotation/repositioning
- accessibility focus changes

---

# 11. Correction Architecture

Do not let the overlay directly manipulate accessibility nodes.

Use:

```text
UI
 -> EditOperation
 -> CorrectionController
 -> CorrectionStrategy
 -> Accessibility node
```

## Validation before applying

Before every correction:

1. Verify node is still valid/editable.
2. Verify text still matches the analyzed state or expected original segment.
3. Verify UTF-16 range is valid.
4. Verify the requested operation is still applicable.
5. Apply correction.
6. Re-read/refresh the field when possible.
7. Re-analyze resulting text.

## `ACTION_SET_TEXT` rule

`ACTION_SET_TEXT` is a useful baseline but can affect selection/cursor behavior and may not behave identically across every app.

Therefore:

```text
CorrectionStrategy
  ├─ Accessibility set-text strategy
  ├─ App-specific compatibility strategy (only when justified)
  └─ Unsupported/failure state
```

Do not add fragile clipboard or screen-scraping fallbacks just to increase the number of “supported” apps.

## Selection preservation

The preferred behavior is to preserve a logical caret/selection whenever possible.

Because some accessibility set-text implementations move the cursor to the end, test and compensate where reliable and safe. Do not sacrifice correctness to pretend selection is preserved.

---

# 12. Security and Privacy

This is a system-wide typing assistant. Privacy must be stricter than for a normal editor.

## Never analyze protected fields

At minimum:

- password fields
- explicit sensitive/protected fields when reliably detectable

Extend detection cautiously. Avoid broad heuristics that cause false positives in normal text fields.

## App policy

Maintain a configurable compatibility/policy layer for applications where accessibility text extraction or editing is unreliable or unsafe.

Do not attempt to bypass an application's explicit security behavior.

## Logging rules

Never log:

- raw typed text
- passwords
- suggestion contents derived from private text
- full field contents

Safe logging examples:

```text
package=com.example.app
nodeChanged=true
analysisDurationMs=23
lintCount=2
resultStale=false
```

---

# 13. Configuration Architecture

Configuration must be centralized rather than scattered through UI and engine code.

Model something similar to:

```text
HarperConfig
- dialect
- enabled/disabled lints where supported
- ignored rules
- document mode
- dictionary settings
- max input length
- analysis debounce
- app compatibility policy
- privacy policy
```

## Configuration rule

The Rust engine and Android UI must not each maintain unrelated copies of configuration.

Use a single persisted source of truth on Android and convert it into a versioned Rust configuration object.

Consider a configuration version/hash so an analysis result can be associated with the configuration that produced it.

---

# 14. Dictionaries

The architecture should support:

- curated/default dictionary
- user dictionary
- future workspace/file-local/static concepts when relevant to the Android product

The exact available Harper dictionary APIs must be confirmed against the selected `harper-core` version.

Dictionary ownership should be explicit.

The same effective dictionary configuration must be used consistently where the Harper API requires it for document/linter behavior.

Do not recreate dictionary structures for every keystroke.

---

# 15. Lint Lifecycle

Use an explicit analysis lifecycle rather than implicit booleans.

Recommended state machine:

```text
Idle
  -> Scheduled
  -> Analyzing
  -> Result

Result
  -> Applied
  -> Dismissed
  -> Stale
  -> ReplacedByNewAnalysis

Analyzing
  -> CancelRequested
  -> Stale
```

The exact state names may differ, but the semantics must be explicit.

This prevents UI bugs where a result remains visible after its source text has changed.

---

# 16. Stable Issue Identity

Never identify a lint only by:

```text
index = 0
index = 1
```

A user selecting suggestion 2 for issue A must still act on issue A even if the lints are re-ordered.

Build a stable issue ID from the analysis/request context plus deterministic issue characteristics where appropriate.

The ID does not have to survive arbitrary future text edits forever; it only needs to be stable enough for the current analysis/UI lifecycle and safely invalidated when the source changes.

---

# 17. Phase-by-Phase Implementation Plan

Each phase is intentionally small and should end with a working, testable state.

---

## Phase 0 — Baseline and Inventory

### Goal
Freeze the current behavior and understand exactly what the MVP does.

### Tasks

- Record current Git status and baseline commit.
- Inventory all Kotlin/Rust files involved in accessibility, analysis, FFI, overlay, correction, and configuration.
- Record exact resolved `harper-core` version from `Cargo.lock`.
- Record Rust/Android/NDK/UniFFI versions actually used.
- Verify current build on every intended Android architecture.
- Run existing tests.
- Capture baseline latency for short/medium/long text.
- Record known incompatible apps from `COMPATIBILITY.md`.
- Document current correction behavior.

### Acceptance gate

Build succeeds, tests pass, and baseline metrics/build versions are documented.

---

## Phase 1 — Move Native Linting Off the Main Thread

### Goal
Remove the current risk that `engine.lint(...)` runs on Main.

### Tasks

- Introduce dedicated background analysis execution.
- Keep only cheap event processing/snapshot creation on Main.
- Ensure results return to Main before UI mutation.
- Add a regression test or instrumentation check preventing accidental Main-thread linting.

### Acceptance gate

Typing remains responsive while analysis runs; no heavy lint call executes on the Android main thread.

---

## Phase 2 — Introduce a Persistent Rust Harper Engine

### Goal
Stop rebuilding expensive Harper structures on every lint call.

### Tasks

- Replace stateless `HarperEngine {}` with persistent engine state.
- Initialize dictionary once per engine/session.
- Initialize or reuse lint group state as appropriate.
- Keep configuration state inside the engine where appropriate.
- Ensure ownership/lifetimes are correct.
- Verify thread-safety rather than assuming it.

### Important constraint

Do not parallelize engine access merely because it “looks safe.” Verify actual `Send`/`Sync` guarantees of the chosen objects and measure before enabling multi-threaded use.

A single dedicated analysis worker is a valid initial design.

### Acceptance gate

Repeated lint calls reuse core state and performance improves or stays safely equivalent without regressions.

---

## Phase 3 — Build the Stable Rust/UniFFI Compatibility Layer

### Goal
Create the durable boundary between Android and `harper-core`.

### Tasks

Create Android-facing types such as:

```text
HarperConfig
HarperLint
HarperSuggestion
EditOperation
AnalysisMetadata
HarperError
```

Keep `harper-core` types behind the adapter where practical.

Centralize:

- UTF-16 conversion
- suggestion conversion
- core error mapping
- configuration translation
- document creation
- lint invocation

### Acceptance gate

Android no longer needs to know how `harper-core` internally represents every lint/suggestion object.

---

## Phase 4 — Preserve All Supported Suggestion Semantics

### Goal
Stop throwing away valid Harper suggestions.

### Tasks

- Inspect the exact `Suggestion` enum in the resolved `harper-core` version.
- Map every currently supported variant.
- Represent operations explicitly.
- Add round-trip/unit tests for each operation.
- Verify empty/zero-length insertion behavior.
- Verify deletion behavior.

### Acceptance gate

No supported core suggestion is silently dropped by the Android bridge.

---

## Phase 5 — Add Real Dialect Support

### Goal
Remove the hard-coded American dialect.

### Tasks

- Define supported dialect enum in Android-facing API.
- Map it to exact `harper-core` dialect values.
- Persist user selection.
- Rebuild/update relevant engine configuration safely when dialect changes.
- Tie result/config version to analysis request.
- Add tests for at least two dialects if supported by the selected version.

### Acceptance gate

Changing the selected dialect changes lint behavior deterministically.

---

## Phase 6 — Add Lint Configuration and Ignored Rules

### Goal
Expose supported Harper lint controls rather than treating curated lints as immutable.

### Tasks

- Inspect exact configuration APIs available in the resolved core version.
- Add a Rust adapter configuration model.
- Support enabling/disabling relevant lint rules.
- Support ignored rules.
- Persist settings on Android.
- Add configuration version/hash to analysis requests.
- Ensure configuration changes invalidate stale results.

### Acceptance gate

A configured rule can be turned off/ignored and the result actually changes accordingly.

---

## Phase 7 — Add User Dictionary Support

### Goal
Allow users to whitelist words that are valid for them.

### Tasks

- Inspect current core dictionary APIs.
- Build a persistent user-dictionary store.
- Convert/store entries safely.
- Integrate the effective dictionary into the Harper engine.
- Avoid rebuilding the dictionary for every keystroke.
- Add add/remove tests.

### Acceptance gate

A user-added word stops producing the intended spelling complaint after configuration refresh.

---

## Phase 8 — Document/Parser Abstraction

### Goal
Stop baking `new_plain_english(...)` into the public engine contract.

### Tasks

Introduce a document mode abstraction such as:

```text
PlainText
Markdown
Other supported structured modes
```

Only implement modes actually supported and useful for the Android product.

Map each mode to the correct Harper parser/document API for the selected version.

### Constraint

Do not implement Markdown/code parsing merely because the enum exists. Use current upstream behavior and validate the actual Android use cases.

### Acceptance gate

The engine can make an explicit document-mode decision and the mode is test-covered.

---

## Phase 9 — Correct Analysis Scheduling and Stale-Result Protection

### Goal
Make real-time analysis deterministic under rapid typing.

### Tasks

Implement:

- debounce
- request IDs
- node identity checks
- generation counters
- stale-result rejection
- configuration-version checks
- bounded queueing
- cancellation/stale-result metrics

Use `mapLatest` or equivalent only as part of the design, not as the entire cancellation story.

### Acceptance gate

Rapidly typing/replacing text cannot cause an older lint result to overwrite a newer state.

---

## Phase 10 — Add Explicit Accessibility Event Classification

### Goal
Reduce unnecessary analysis and prevent incorrect triggers.

### Tasks

Create an event classifier for:

- focus
- text changed
- selection changed
- cursor-only changes
- composition-related events
- node replacement/recycling
- unsupported app behavior

Track the current editable node identity robustly.

### Acceptance gate

Selection movement alone does not create unnecessary full lint runs; actual text edits still do.

---

## Phase 11 — IME/Composition Handling

### Goal
Prevent the assistant from fighting the keyboard while text is being composed.

### Tasks

- Identify composition information available through Android APIs and observed apps.
- Introduce composition-aware state.
- Delay/reduce disruptive corrections during composition.
- Re-run analysis on commit/stable text.
- Test with multiple keyboard/input methods and editors.

### Acceptance gate

Typing and composing words feels natural and corrections do not interfere with active composition.

---

## Phase 12 — Multi-Lint / Full Suggestion Overlay UI

### Goal
Build a usable UI for every lint and all its suggestions.

### Tasks

- Display multiple lints.
- Present message/details for selected lint.
- Present every supported suggestion.
- Handle insert/replace/remove visually where useful.
- Allow dismiss.
- Allow navigation among issues.
- Prevent stale overlay actions from modifying new text.
- Use stable issue IDs.

### Acceptance gate

A field containing multiple issues can be reviewed and each applicable suggestion can be selected safely.

---

## Phase 13 — Modern Overlay Positioning and Geometry

### Goal
Make the overlay robust across real Android window/display layouts.

### Tasks

- Isolate overlay positioning in a dedicated component.
- Use appropriate accessibility overlay/window APIs.
- Use node/window bounds correctly.
- Separate text offsets from geometric coordinates.
- Handle display metrics and orientation changes.
- Reposition after scrolling and focus changes.
- Handle IME-induced layout movement.

### Acceptance gate

Overlay stays near the relevant text field across common phone/window configurations without blocking normal interaction.

---

## Phase 14 — Correction Strategy and Selection Safety

### Goal
Make applying suggestions safe and compatible.

### Tasks

- Create correction strategy abstraction.
- Implement accessibility `ACTION_SET_TEXT` path.
- Validate current text before applying.
- Validate range.
- Apply explicit edit operation.
- Re-read node after edit where possible.
- Re-analyze after correction.
- Test caret/selection behavior.
- Record app-specific failure patterns.

### Acceptance gate

A stale suggestion cannot corrupt current text, and successful correction results in expected final text in supported apps.

---

## Phase 15 — App Compatibility Hardening

### Goal
Make compatibility policy explicit instead of relying on accidental behavior.

### Tasks

- Maintain allow/deny/limited support policy.
- Test standard Android text fields.
- Test major browsers/editor-style apps as permitted.
- Test apps with custom editors.
- Document expected failures.
- Keep conservative blocks for apps where accessibility mutation is unsafe/unreliable.

### Constraint

Do not add per-app hacks until generic behavior is verified and the limitation is demonstrated.

### Acceptance gate

Each supported app category has a documented reason for its support level.

---

## Phase 16 — Input Bounds and Performance Optimization

### Goal
Optimize after correctness is stable.

### Tasks

- Add input-size limits.
- Benchmark small/medium/large text.
- Profile dictionary/linter initialization.
- Profile allocation/copy overhead.
- Reduce Kotlin/Rust string copying only where measured.
- Reuse buffers/objects where safe.
- Evaluate persistent worker vs parallel analysis.
- Measure effect of debounce changes.

### Future-ready design

Keep interfaces compatible with incremental analysis, but do not implement incremental parsing until profiling proves it worthwhile and the upstream APIs make it practical.

### Acceptance gate

Performance targets are documented and met for the chosen device/test matrix.

---

## Phase 17 — Security and Privacy Hardening

### Goal
Make system-wide operation privacy-safe by default.

### Tasks

- Expand protected field detection where reliably supported.
- Confirm password fields are never analyzed.
- Audit logs.
- Audit crash/error reporting for text leakage.
- Audit storage for user dictionary/configuration.
- Verify sensitive content is not persisted unintentionally.
- Review overlay accessibility and tapjacking-related concerns.
- Review package/application policies.

### Acceptance gate

No known raw user text/password leakage remains in normal logs or persistent storage paths.

---

## Phase 18 — Full Automated Test Matrix

### Goal
Turn the project into a regression-resistant system.

### Rust tests

- UTF-16 conversion
- suggestion mapping
- replacement
- insertion
- deletion
- dictionary behavior
- dialect behavior
- lint configuration
- ignored rules
- document modes
- configuration invalidation
- error mapping

### Kotlin tests

- debounce
- request generation
- stale-result rejection
- event classification
- protected-field detection
- app policy
- correction validation
- overlay state machine

### Instrumentation tests

- editable node tracking
- focus changes
- actual typing
- selection changes
- scrolling
- keyboard visibility
- orientation
- overlay interaction
- correction

### Acceptance gate

All automated suites pass in CI and critical functionality has device-level coverage.

---

## Phase 19 — Build/CI Matrix and Release Hardening

### Goal
Make the implementation reproducible and maintainable.

### Tasks

- Lock dependency versions appropriately.
- Verify exact `harper-core` compatibility on every upgrade.
- Build all intended Android ABIs.
- Verify UniFFI generated bindings are reproducible.
- Add Rust formatting/lint checks.
- Add Kotlin formatting/lint checks.
- Run unit/instrumentation tests in CI.
- Run performance smoke tests.
- Generate release notes for core-version changes.
- Document rollback procedure for a bad Harper core upgrade.

### Acceptance gate

A clean checkout can reproduce the documented release artifact using documented toolchain versions.

---

# 18. Configuration/UI Roadmap

Do not build the full settings UI before the engine contracts are stable.

A practical order is:

```text
Phase 3-7 engine capabilities
        |
        v
configuration persistence
        |
        v
settings UI
```

Potential settings:

- dialect
- spelling/grammar toggles
- ignored rules
- user dictionary
- analysis debounce
- protected/supported apps
- overlay behavior
- accessibility compatibility mode

Keep advanced controls understandable to normal users. Internal rule identifiers should not leak into the UI without human-readable labels.

---

# 19. Harper Version Management

This project must be conservative about `harper-core` upgrades.

For every upgrade:

1. Record old and new resolved versions.
2. Read release notes/source changes.
3. Inspect breaking API differences.
4. Inspect `Suggestion` changes.
5. Inspect dictionary/configuration changes.
6. Re-run all Rust adapter tests.
7. Re-run Android instrumentation tests.
8. Re-run performance benchmarks.
9. Verify all intended Android ABIs.
10. Keep compatibility code localized.

Never upgrade solely because a newer version number exists.

When documentation and registry metadata disagree, trust the actual resolved dependency and source used by the build, then verify the upstream state at implementation time.

---

# 20. Recommended Error Model

Do not leak opaque Rust errors directly into UI.

Map errors into stable categories such as:

```text
InvalidInput
UnsupportedConfiguration
EngineInitializationFailed
AnalysisFailed
StaleRequest
UnsupportedOperation
CorrectionFailed
DictionaryError
InternalError
```

UI should choose whether to show, log, retry, or silently ignore each category.

For expected transient/stale cases, avoid scary user-facing errors.

---

# 21. Memory and Lifecycle Rules

Accessibility services may live for long periods.

Avoid:

- unbounded cached snapshots
- unbounded lint history
- per-keystroke object retention
- duplicate dictionaries
- leaked accessibility nodes/context references
- overlay references surviving service teardown

On service shutdown:

- stop analysis work
- release overlay
- release/cancel observers
- cleanly drop engine/session state as required

---

# 22. Concurrency Rules

Start conservative.

Preferred first design:

```text
one analysis worker
      |
      v
persistent Harper engine
```

Then measure.

Only introduce parallel workers if:

- engine objects are verified safe for concurrent use,
- the actual Android workload benefits,
- memory cost is acceptable,
- and stale-result management remains deterministic.

Do not create a pool simply because the device has many CPU cores.

---

# 23. What NOT to Do

Do not:

- reimplement Harper grammar rules in Kotlin
- silently ignore unsupported suggestion kinds
- initialize a new dictionary/linter for every keystroke
- run full linting on Main
- assume coroutine cancellation stops synchronous native work
- treat every accessibility event as a text mutation
- fight IME composition
- apply stale suggestions
- identify issues only by list index
- mix UTF-16 offsets with screen coordinates
- assume `ACTION_SET_TEXT` works identically in every app
- use clipboard/screenshot/OCR as the normal correction mechanism
- log raw typed content
- create unbounded memory caches
- expose every `harper-core` internal type through FFI
- upgrade Harper blindly
- optimize before measuring
- implement huge refactors in one phase
- add app-specific hacks before proving a general Android approach fails

---

# 24. Agent Workflow for Every Phase

For each phase, the coding agent must follow this sequence:

### A. Inspect

- Read current code.
- Read relevant tests.
- Read relevant upstream Harper docs/source.
- Confirm the exact API/version in use.

### B. Design

- State the minimal change.
- State impacted modules.
- State compatibility risks.
- State test plan.

### C. Implement

- Keep change focused.
- Preserve existing behavior unless the phase intentionally changes it.
- Update types/contracts together.

### D. Validate

Run the smallest useful test first, then the broader suite.

### E. Report

The agent should report:

```text
Phase:
Status:
Files changed:
Behavior added:
Tests run:
Performance impact:
Compatibility impact:
Known limitations:
Next gate:
```

Do not report a phase complete if its acceptance gate is not satisfied.

---

# 25. Recommended Implementation Order at a Glance

```text
0  Baseline/inventory
1  Background analysis
2  Persistent engine
3  Stable FFI/domain layer
4  All suggestion semantics
5  Dialects
6  Lint config/ignored rules
7  User dictionary
8  Document/parser abstraction
9  Scheduling + stale protection
10 Event classification
11 IME composition
12 Multi-lint suggestion UI
13 Overlay geometry
14 Correction strategy
15 App compatibility
16 Performance/input bounds
17 Security/privacy
18 Full tests
19 CI/release hardening
```

This order deliberately puts correctness and architecture before polish.

---

# 26. Definition of Done

The project is considered “full integration complete” when all of the following are true:

## Harper core

- [ ] Real `harper-core` is used.
- [ ] Curated linting is used.
- [ ] Dictionary state is reused appropriately.
- [ ] Dialect is configurable where supported.
- [ ] Lint configuration/ignored rules are supported where available.
- [ ] User dictionary is supported where available.
- [ ] Document/parser mode is explicit.
- [ ] All supported suggestion kinds are preserved.

## FFI

- [ ] Stable Android-facing models exist.
- [ ] UTF-16 conversion is centralized and tested.
- [ ] Harper internals are isolated behind the compatibility layer.
- [ ] Errors map to stable categories.

## Performance

- [ ] Native linting never blocks Main.
- [ ] Dictionary/linter state is reused.
- [ ] Debounce is configurable and tested.
- [ ] Stale analysis cannot overwrite newer text.
- [ ] Performance benchmarks exist.
- [ ] Input bounds exist.
- [ ] Cancellation behavior is accurately documented.

## Accessibility

- [ ] Event classification distinguishes real edits from noise.
- [ ] Node identity/generation checks exist.
- [ ] Password/sensitive fields are protected.
- [ ] IME composition is handled safely.
- [ ] App compatibility policy is explicit.

## UI

- [ ] Multiple lints can be displayed.
- [ ] All suggestions can be displayed.
- [ ] Suggestions can be selected.
- [ ] Issue dismissal/navigation exists.
- [ ] Stable issue IDs are used.
- [ ] Overlay positioning is robust.
- [ ] UI actions reject stale results.

## Correction

- [ ] Correction operations are explicit.
- [ ] Text/range validation occurs before mutation.
- [ ] Selection/cursor behavior is tested.
- [ ] Correction is revalidated/reanalyzed afterward.
- [ ] No fragile clipboard/screen-scraping fallback is required for the normal path.

## Security

- [ ] No raw text is logged.
- [ ] Sensitive fields are protected.
- [ ] Persistent storage is reviewed.
- [ ] Overlay lifecycle is safe.

## Quality

- [ ] Rust tests pass.
- [ ] Kotlin tests pass.
- [ ] Instrumentation tests pass.
- [ ] Device compatibility is documented.
- [ ] CI is reproducible.
- [ ] Harper version upgrades are controlled.

---

# 27. Final Architectural Principle

The Android application should be:

> **A high-quality Android host for the actual Harper engine, not a Harper clone.**

That means:

```text
harper-core owns language intelligence

Android owns:
- accessibility integration
- privacy/security policy
- scheduling
- lifecycle
- overlay UX
- correction strategy
- persistence
- device/app compatibility

The Rust compatibility layer connects them cleanly.
```

If a feature can be implemented correctly by using Harper's existing core capability, prefer that over writing parallel language logic in Kotlin.

If a feature does not exist in the selected `harper-core` version, document the limitation explicitly rather than pretending it is supported.

---

# 28. Upstream/Reference Sources to Verify During Implementation

The agent should consult the live upstream sources before making version-sensitive decisions.

- Harper main repository: https://github.com/elijah-potter/harper
- Harper core crate source/documentation: https://docs.rs/harper-core/
- Harper language-server/configuration documentation: https://writewithharper.com/docs/integrations/language-server
- Android `AccessibilityService`: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Android `AccessibilityNodeInfo.ACTION_SET_TEXT`: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo

For every implementation task, prefer current upstream source/docs over assumptions from this document when APIs have changed.

---

# 29. Final Agent Instruction

Do not treat this guide as permission to skip investigation.

For every phase:

1. inspect the current repository;
2. inspect the exact resolved Harper version;
3. verify current upstream APIs when version-sensitive;
4. implement the smallest complete change;
5. write/update tests immediately;
6. validate on the actual Android build/test matrix available;
7. measure performance when the phase affects performance;
8. document deviations from this guide;
9. stop at the phase gate if the acceptance criteria are not met.

The implementation should converge toward a robust, privacy-first, real-time Android editor assistant that maximizes use of Harper's actual language engine while keeping Android-specific integration concerns cleanly separated.
