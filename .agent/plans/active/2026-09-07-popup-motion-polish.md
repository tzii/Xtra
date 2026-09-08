# Popup and Stats motion polish

## Goal

Review the committed rounds 5-8 and make popup motion, touch feedback, and Stats updates smooth and consistent.

## Non-goals

Playback backend, release work, data semantics, and new popup ownership.

## Current context

HEAD f6c7bbae contains the earlier work. Opening currently scales the inner card while closing scales its viewport. Stats disables bar animation and RecyclerView can crossfade complete cards on updates.

## Files likely involved

- PlayerFragment.kt, PlayerQualityPopupBinder.kt, PlayerSpeedPopupBinder.kt
- StatsFragment.kt, StatsDashboardAdapter.kt, DailyBarChartView.kt
- docs/PLAYER.md

## Risks

Interrupted animation cleanup and narrow-layout readability. Risk level: medium.

## Human approval

Required before implementation: no. User requested review and polish; changes are reversible UI work.

## Implementation steps

1. Animate one popup container in both directions, with button-relative pivot and coordinated scrim.
2. Add bounded ripple feedback to quality and speed controls.
3. Animate chart updates with cancellation and detach cleanup; avoid whole-card blinking.
4. Run assembleDebug, test, lintDebug and review the diff.

## Verification

Automated: assembleDebug, test, lintDebug, diff check.

Human QA required: portrait/landscape/wide popups, rapid open/dismiss, disabled system animations, Stats filters and rotation, live/VoD, switching, minimize/restore, close/reopen, PiP/background, speed/quality, gestures, floating chat.

Human QA completed: none.

## Progress log

- 2026-09-07: Reviewed current implementation and corrected stale commit state.
- 2026-09-07: Unified popup animation target and trigger pivot, eased entry/exit and scrim dismissal, added chip/control ripples, fixed quality selected-text inheritance, and added interruptible Stats bar transitions with detach cleanup. Disabled RecyclerView whole-card change flashes.
- 2026-09-07: Debug APK assembled; 322 tests passed with zero failures/errors. Final lint report generated at 13:12 reports 0 errors, 328 warnings. Reviewed diff and whitespace checks. No Android device attached; physical QA remains pending.

## Decisions

Use property animation on the bounded container; avoid layout animation during video playback. Keep platform animation duration scaling.

## Final PR summary draft

Popup reveals and exits share bounded geometry and trigger-relative motion; Quality/Speed controls give ripple feedback. Stats bar updates animate from current heights and no longer flash the entire card. Debug assembly, 322 tests and lint verified. Device QA remains required for motion feel, portrait readability, disabled animations, and the player regression checklist above.
