# Palette's Journal

## 2024-05-22 - Accessibility on Custom Card Buttons
**Learning:** The app uses `MaterialCardView` wrapping `ImageView` for score control buttons. These lacked `contentDescription`, making core functionality inaccessible to screen readers.
**Action:** Always check custom compound button components for `contentDescription` on the touch target.

## 2024-05-22 - Mobile Input Optimization
**Learning:** Player name inputs were missing `inputType` and `imeOptions`, leading to poor keyboard experience (no auto-caps, no "Done" action).
**Action:** Ensure all `EditText`s have appropriate `inputType` and `imeOptions` defined.
