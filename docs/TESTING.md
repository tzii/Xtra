# Testing Guide

## Minimum local checks before merge

```bash
./gradlew assembleDebug
./gradlew test
```

For release-related work also run:

```bash
./gradlew assembleRelease
```

For UI/resources/player work, also run `./gradlew lintDebug`.

Android resource/layout regression tests use Robolectric with Android resources enabled.
They run on API 28 with Conscrypt disabled (these tests do not perform networking; its
JNI provider is unavailable on the Windows test host). `PlayerPopupContentTest` checks
fixed headers, a single scroll owner, bottom-row reachability, host parentage, and mixed
codec/Auto labels at enlarged font sizes. `StatsLayoutTest` checks compact card text,
range-button height, number/unit wrapping, and horizontal reachability at 100%/200%
font size. Native graphics renders generate sample-data previews under
`app/build/ui-previews/`, including More before/after scrolling and Quality/Stats at
both font sizes. Tests explicitly resolve layout direction for detached preview roots.
These check Android layout/rendering, not real player playback, device window placement,
or animation smoothness. Human checks below remain required.

## Manual regression checklist

### Player basics
- live stream opens
- VoD opens
- stream switching works
- minimize / restore works
- no obvious visual glitches during transition

### Speed control
- current speed is shown correctly
- speed changes update immediately
- state survives orientation and minimize/restore if relevant

### Gestures
- brightness still works
- volume still works
- VoD seek is responsive
- large drags allow fast movement through long VoDs
- gesture conflicts remain acceptable

### Floating chat
- overlay opens correctly
- drag / resize persistence works
- no broken empty-sidebar/floating-chat state

### Layouts
- portrait phone
- landscape phone
- at least one wide/tablet profile
- split-screen if layout code changed
- open Quality, Speed and More on a portrait live stream and VoD: card can extend over
  chat, title/close/rounded edges stay fixed while only options scroll
- repeat with large fonts, RTL, top/bottom controls, side chat, and floating chat; every
  option must be reachable and no popup may inherit the previous popup's position
- close via header/back/outside; rotate, minimize/restore, close/reopen, and enter/leave
  PiP/background while open; no orphaned overlay or blocked chat touches after dismissal

### Stats
- stats screen opens
- rotation while already on stats screen behaves correctly
- no obviously broken spacing in compact / wide layouts
- switch ranges rapidly: bar motion should settle without whole-card blinking
- leave/reopen Stats during animation and verify the chart shows final values
- repeat with system animator duration scale disabled
- check the compact summary metrics and streak row with long values and 200% text
- range selector stays compact at normal text size; enlarged/localized labels remain
  reachable by horizontal scrolling rather than clipping
- empty, minute-long, and multi-hour data use readable scales; compare bar values with
  totals, and check labels in portrait, landscape, and tablet layouts
