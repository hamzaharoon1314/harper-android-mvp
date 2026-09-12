# Agent Rules

Read `AGENT_GUIDE.md` before making architectural changes.

`AGENT_GUIDE.md` is the authoritative project specification.

## Development rules

1. Work incrementally.
2. Make small, buildable changes.
3. Run local tests before pushing.
4. Never ignore a failing test without documenting why.
5. Never bypass CI failures by weakening tests.
6. Keep AccessibilityService separate from grammar logic.
7. Keep Rust responsible for Harper.
8. Keep Kotlin responsible for Android integration.
9. Use UniFFI for the Rust/Kotlin boundary.
10. Keep FFI models small and versioned.
11. Use UTF-16 offsets at the Android-facing correction boundary.
12. Never apply a stale correction.
13. Never log raw user text.
14. Never persist raw user text.
15. Never send grammar text to a cloud service.
16. Never use clipboard automation as a correction fallback.
17. Never use OCR or screenshots to read text.
18. Do not build a custom keyboard/IME for MVP.
19. Do not build a full in-app writing editor just to test Harper.
20. Use the minimal Android accessibility fixture for integration tests.
21. Do not request unnecessary AccessibilityService capabilities.
22. Do not use broad accessibility event masks without a measured reason.
23. Preserve the Play-policy requirements in `AGENT_GUIDE.md`.

## Development loop

For every meaningful change:

```text
Inspect
→ implement
→ local test
→ commit
→ push
→ GitHub Actions
→ inspect results
→ fix if needed
```

Do not start the next architectural phase while the current phase is failing.

## Phase discipline

Complete phases in `AGENT_GUIDE.md` in order.

Do not jump from:

```text
Accessibility prototype
```

directly to:

```text
Grammarly-like UI
```

before:

```text
Accessibility
→ TextSnapshot
→ Harper
→ stale-result validation
→ CorrectionApplier
```

is stable.

## Git discipline

Use focused commits.

Preferred format:

```text
feat(accessibility): add focused node detection
feat(native): integrate harper-core
feat(grammar): add debounced analysis
feat(edit): add stale correction protection
feat(ui): add suggestion overlay
test(privacy): protect password fields
```

Never commit generated secrets, signing keys, keystores, local SDK paths, or raw user text.
