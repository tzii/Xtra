# Player Architecture And Regression Notes

## Why This Area Is High Risk

The player is ThystTV's most important UX surface. Small lifecycle changes can cause double audio, stale fragments, broken minimize/restore, PiP regressions, gesture conflicts, black surfaces during transitions, or unreadable overlay controls.

## Staleness Note

The paths below are a map, not a guarantee. If code has moved, search for the current file/function and update this doc when useful.

## Core Invariants

- Only one active player session should exist.
- Starting a new player should close or safely replace the old player.
- Closing a player should release playback resources.
- Minimize/restore must not leave stale fragments.
- Sleep timer should close only the current player.
- PiP auto-enter should be enabled only while a player is open.
- Stream switching must not leave old audio playing.
- UI controls should stay responsive during live/VoD transitions.
- Overlay surfaces should remain readable over arbitrary video content.
- Quality, Speed, Stream volume, and More share one player-owned popup host; only one
  can be attached at a time, and teardown/minimize/PiP must remove it with the player view.

## High-Risk Files

Update this list as the code evolves.

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/main/MainActivity.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/`
- `app/src/main/java/com/github/andreyasadchy/xtra/player/lowlatency/`
- player-related layouts in `app/src/main/res/layout/`
- player-related strings in `app/src/main/res/values/`
- player settings/preferences in `app/src/main/res/xml/`
- Media3 / ExoPlayer dependency declarations in Gradle catalogs/build files

## Recent Lessons From ThystTV Fixes

- For issue #5, logs did not show a clean app `FATAL EXCEPTION`; the safer fix was the narrow Media3/ExoPlayer rollback and HLS parser compatibility change, not broad WIP player sync.
- Quality menu labels can regress to HLS variant indices after chat-only -> auto transitions. Preserve readable labels derived from labels, format height/frame rate, or URL path.
- Mixed-codec Quality choices keep resolution as the primary label and show a standardized
  codec name as secondary metadata. Audio-only and Chat-only stay outside the scrolling
  video grid so they remain reachable in compact landscape players.
- Selected Quality/Speed chips render as solid primary pills with luminance-matched
  content color (`PlayerPanelColors.onSelected`); do not revert to low-contrast blends.
- Quality chip column counts are derived from measured bold label widths so labels such
  as "1080p60" never truncate; do not return to fixed 4-column grids with silent clipping.
- VoD and live menus may legitimately differ. Do not add chat-only to VoD unless the data/model supports it.
- Floating chat should use a dark translucent video-overlay palette in both app themes; background opacity remains user-controlled.
- Stats/player overlay controls need compact and landscape checks because label clipping and stacked graph labels have happened before.
- Any view adapter tied to fragments can leak destroyed views. Clear listeners/adapters in `onDestroyView()` when changing RecyclerView or fragment lifecycles.

## Player System-UI Listener Ownership

The landscape decor-view system-UI listener is owned by `PlayerSystemUiListener`
and the player's **view** lifecycle. Detach it on portrait layout and explicitly
at the start of `onDestroyView`, as well as on the view lifecycle's destroy event.
Its per-decor registration replaces and invalidates the previous callback; old
player teardown must not clear a newer player's registration. Detach drops the
callback, lifecycle and decor references, including on replacement. Do not install
a raw fragment-capturing callback on the activity window: the decor can outlive
the player and retain a closed fragment after rotation/stream switching.

## Player Popup Ownership

Quality uses the same 48dp gear button on compact, landscape, tablet and resized
surfaces. Never replace it with the selected-quality text based on width or label
availability: rotation callbacks can still see the previous layout's width, and
replacing the trigger also changes its popup anchor. The menu retains the selected
quality; the gear's accessible description includes it when available.

`PlayerFragment` owns the full-player dismissal host and delegates each popup's controls
to a short-lived binder. Quality, Speed, Stream volume, and More use the same bounded,
inset-aware, trigger-relative placement and alpha/scale motion. The host consumes panel
and outside touches, keeps controls visible while open, closes on back/outside tap, and
restores focus plus the normal control auto-hide timer on dismissal.

Popup motion animates the bounded container in both directions (220ms eased reveal,
140ms exit), with its pivot clamped toward the cached trigger. Never animate the inner
card on entry and the viewport on exit: scrolling makes their geometry differ. The
scrim fades during dismissal, and replacement/teardown cancels pending motion. Platform
animator duration settings apply. Quality/Speed chips provide bounded ripple feedback.
Selected chip foregrounds use the higher-contrast black/white color for the accent.
Quality binds text colors immediately because inherited drawable state can leave the
first, unattached frame using the unselected text color. Speed uses a short visual
heading with a full playback-speed accessibility label, keeping 200% text readable.

Cards close via outside tap or back. Quality, Speed, and More also have persistent
header close buttons with 48dp touch targets; Stream volume keeps its auto-dismiss timer.
In portrait a light scrim dims video/chat and passes taps to the outside-dismiss handler.
Taps in the card itself do not dismiss it unless an option's action requests dismissal.

More keeps its existing grouped action order and preference gates. Its display-mode row
intentionally opens the existing single-choice alert above the embedded host; the four
top-level player popups themselves must not return to DialogFragment or bottom-sheet
ownership.

Placement rules: popups prefer the space above their trigger, fall back to below it, and
when the surface is too short for either they pin to the edge nearest the trigger
(bottom-bar buttons such as Stream volume stay bottom-anchored; top-bar triggers such as
Quality, Speed, and More stay top-anchored). Horizontally, left-side controls align the
panel's left edge and right-side controls align its right edge before bounds clamping, so
placement remains visibly tied to the button rather than drifting around the surface.
The host is a sibling of `slidingLayout`, above floating chat, **not a child of the
16:9 `PlayerLayout`**. Portrait may use the visible fragment space over chat; landscape
is bounded by the video's visible screen area. Surface and window rectangles use screen
coordinates consistently; trigger coordinates are relative to the host. Width classes
use the available video width, not the full window width including side chat.

`PlayerPopupContent` preserves the card and its first/header row, moving only options
into `playerPopupViewport`. More's old inner scroll view is unwrapped so only one scroll
view handles options. Natural content height is measured at final width, then the card
height is bounded and its weighted viewport receives the remaining space. Never wrap
the whole card in a scroll view: that hides the rounded outline, title, and close button.
Quality chips keep single-line Auto vertically centered beside codec pairs, and chip
heights grow when enlarged fonts need more room.
Popup content is bound before the host is shown, and `showPlayerPopup` places the
container (explicit measure, no layout pass needed) before the host becomes visible,
so the reveal animation's first frame is already at the anchored geometry. Placement
application is idempotent (geometry written only on change), host-bound changes trigger
repositioning, and the first valid trigger rect is cached as the popup's anchor
for its whole lifetime: while the cache is empty the trigger is re-read (a popup that
opened from fallback geometry corrects itself when the trigger gains bounds, for
example controls were GONE at open time), but once cached, later control-bar reflows —
quality-label or viewer-count text changes — must not drag a visible popup around.
Do not remove these guards; stale inherited margins, reveal-before-place frame races,
and untracked trigger moves caused jump/multi-press misplacement reports.

## Saved resume and headset fallback

`PlayerViewModel` resolves automatic saved positions for all player backends. A
position at or beyond a known positive duration restarts at zero; unknown/invalid
durations and unfinished positions are retained. Network argument durations are
seconds converted to Long milliseconds; downloaded videos use their own duration,
not the source VoD offset. No eager database reset is needed; normal playback saves
the new position. Explicit timestamp requests bypass automatic resume, and existing
active-session restoration remains unchanged. Do not apply the completion rule to
quality changes or minimize/restore of an active player.

The optional legacy `ExoPlayerService` supplements Android media-session dispatch
only for unhandled previous/next key-down events. It checks seek command availability
and ignores cancelled/release events. The default Media3 path and platform
MediaPlayer path are unchanged; never introduce a second handler that repeats an
already-handled seek. Test actual headset and background dispatch on devices.

## Required Checks For Player Work

Run:

```bash
./gradlew assembleDebug
./gradlew test
```

Also run when resources/UI are touched:

```bash
./gradlew lintDebug
```

For release-risk player work, also verify:

```bash
./gradlew assembleRelease
```

## Human QA Handoff

Agents should not claim physical-device QA unless it was actually performed. Instead, list the required human QA from `docs/MANUAL_QA.md`.

At minimum, player work should request human verification for:

- live stream opens
- VoD opens
- live -> live switching
- live -> VoD switching, if touched
- VoD -> live switching, if touched
- minimize / restore
- close / reopen
- orientation change
- PiP/background behavior where relevant
- playback speed menu
- quality menu
- gestures
- floating chat overlay

## Common Regression Smells

- old stream audio continues after opening a new stream
- player view is black after minimize/restore
- controls show stale speed/quality
- more than one player popup is visible, or popup content survives minimize/PiP/rotation
- controls auto-hide while a popup is open or fail to resume auto-hide after dismissal
- sleep timer closes the wrong player
- PiP remains enabled after player close
- orientation recreates UI with stale player state
- gestures conflict with chat or controls
- lifecycle callbacks apply to an old fragment
- quality menu shows raw numeric HLS variants
- floating chat is unreadable in light mode or on bright video

## PR Expectations For Player Changes

A player PR must include:

- changed files
- risk areas
- Gradle checks run
- human QA required
- human QA completed only if actually performed
- screenshots/video when UI changed
- known risks or follow-up issues
