# AccessibilityService Requirements

Harper Android relies on the Android `AccessibilityService` API to function as a system-wide grammar assistant.

## Why AccessibilityService is Required
Unlike a custom keyboard (IME), an `AccessibilityService` allows Harper to provide grammar suggestions directly over the user's existing preferred keyboard. It reads the DOM of the active foreground application to extract the text from standard `EditText` nodes and provides interactive overlays without requiring the user to switch input methods.

## Planned API Capabilities
We restrict our accessibility footprint to the absolute minimum necessary event masks:
- `TYPE_VIEW_FOCUSED`: Detect when the user enters a new text field.
- `TYPE_VIEW_TEXT_CHANGED`: Detect when the user modifies text so we can trigger a debounced snapshot analysis.
- `TYPE_WINDOW_STATE_CHANGED`: Detect when overlays or system dialogs appear, which may require the service to dismiss its active suggestions.

## Data Accessed
Harper exclusively accesses the raw text property, window bounds, and cursor selection state of editable `AccessibilityNodeInfo` objects. 

## User Controls
Users retain complete control over the service. It can be toggled on or off at any time via Android's built-in **Settings -> Accessibility -> Downloaded Apps** menu. Turning it off completely halts all background processes.

## Sensitive-Field Handling
Harper proactively ignores fields marked with `isPassword`. It also evaluates an `AppPolicy` blocklist to drop events originating from unsupported or highly sensitive applications (like Google Chrome).

## Play-Policy Considerations
In compliance with Google Play Developer Policies regarding the `AccessibilityService` API:
- We display a prominent disclosure screen in `MainActivity` before prompting the user to enable the service.
- The disclosure explicitly states that the app requires accessibility access to analyze typed text, and clearly outlines that the text is analyzed purely locally on-device without any cloud telemetry.
- In the Play Console, Harper must declare that its use of the Accessibility API is to "support users with disabilities or to provide accessibility features."
