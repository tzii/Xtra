# Repair popup structure and redesign Stats

## Goal

Resolve screenshot-confirmed portrait clipping and disappearing popup headers; give Stats a compact, readable dashboard with a useful chart scale.

## Non-goals

Playback backend/session lifecycle changes, release work, upstream sync, or stats storage semantics.

## Starting context

HEAD f6c7bbae plus uncommitted motion polish. Popups live inside PlayerLayout, which forces a 16:9 height in portrait. The entire Material card scrolls inside an outer viewport, hiding corners and close controls. More nests another scroll view. Stats has excessive vertical stacking and a fixed six-hour chart floor that flattens short sessions.

## Files likely involved

- fragment_player.xml; PlayerFragment.kt; PlayerPopupPolicy.kt; popup layouts/binders
- Stats layouts/range styles; DailyBarChartView.kt and a scale policy
- Player and testing docs; regression tests

## Risks

Overlay touch ownership over chat, insets/RTL placement, sticky-header sizing, and chart labeling. Risk: medium; existing popup/session lifecycle ownership remains in PlayerFragment.

## Human approval

Required before implementation: no. The user requested fixes and a Stats redesign. These are reversible presentation changes.

## Implementation steps

1. Place the player-owned overlay above video/chat, using player bounds in landscape and visible content bounds in portrait.
2. Keep card and header fixed; give the body one bounded scroll region. Preserve trigger anchoring and cancellable animations.
3. Redesign Stats summary/streak cards, range selector and chart scale with compact/wide support.
4. Verify resource measurement with Android view tests if runtime is available; run debug assembly, all unit tests, lint, and review the final diff.

## Verification

- [x] assembleDebug
- [x] test — 330 passed, no failures/errors/skips
- [x] lintDebug — 0 errors, 333 warnings
- [x] local UI/regression review and diff check

Human QA required: supplied portrait cases with all options/close controls reachable; landscape/wide/RTL/large font; fast popup replacement; live and VoD, switching, minimize/restore, close/reopen, PiP/background, speed/quality, gestures, floating chat; Stats filters/rotation/empty and short-session charts.

Human QA completed: none for this build.

## Progress log

- 2026-09-08: Diagnosed outer-scroll and portrait parent-bound constraints from screenshots and source.
- 2026-09-08: Moved the host above video/chat; bounded one options viewport below a fixed header. Kept placement, dismissal, and animation ownership in PlayerFragment; restored background accessibility on dismissal.
- 2026-09-08: Rebuilt compact Stats summary/streak presentation, added a compact range rail with large-text reachability, adaptive chart ceilings, and large-font metric rows that keep units with values. Wide Stats layouts retain their side-by-side structure.
- 2026-09-08: Added API-28 Robolectric native view tests/previews. Disabled only the test Conscrypt provider for Windows compatibility. First native run exposed integer overflow in synthetic scroll targets; corrected those targets. Native preview review also caught low-contrast selected labels and a large-font Speed heading; repaired both, including an assertion on the rendered label color.
- 2026-09-08: An external review lane was rejected because it could export private source to a coding worker; no export was performed. Completed local UI/correctness review instead. Final debug assembly, 330 tests and lint passed after the label-state correction. Inspected native Quality, Speed, More and Stats previews; actual device QA remains outstanding.

## Decisions

Portrait popups may cover chat within the same fragment so they have useful height. Only the popup body scrolls; header and outline remain fixed. Landscape remains player-bounded. Reuse existing selected styles and motion.

Native previews use fixture data and are not device/player screenshots. Physical playback,
touch/animation feel, RTL/window cutouts, and floating-chat/lifecycle QA remain required.
The compact-only metrics ID is intentionally absent from wide resource variants; the
adapter null-checks it. Existing player cutout padding remains owned by the player layout.

## Final PR summary draft

Summary: fixed portrait popup parent bounds, persistent headers/scrolling, placement
coordinates and width classes, first-frame selected colors, large-font grids and
motion consistency. Redesigned compact Stats and large-font metrics/range handling;
chart scales now make short sessions visible.

Tests: `assembleDebug test lintDebug --continue` passed; 330 tests, 0 failures/errors/
skips; lint 0 errors and 333 warnings. `git diff --check` passed. Native sample-data
previews are in `app/build/ui-previews/`; APK is `app/build/outputs/apk/debug/app-debug.apk`.

Human QA: required as listed above and in `docs/MANUAL_QA.md`; no device attached.

Risks: native previews do not exercise real playback/window placement or frame pacing.
Verify all four popups, portrait/landscape/wide/RTL/cutouts, switching/minimize/PiP,
gestures/floating chat, and Stats ranges on device before merge. No playback backend,
stats storage, release/version, or upstream-sync changes. Checkpoint on
`codex/player-ux-tablet-live-discovery`; keep in REVIEW until device QA is complete.
