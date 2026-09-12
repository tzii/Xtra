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
fixed headers, a single scroll owner, bottom-row reachability, host parentage, mixed
codec/Auto labels, and one accessible 48dp Quality gear through compact/wide/compact
layout measurements at enlarged font sizes. `StatsLayoutTest` checks compact card text,
range-button height, number/unit wrapping, and horizontal reachability at 100%/200%
font size. Native graphics renders generate sample-data previews under
`app/build/ui-previews/`, including More before/after scrolling and Quality/Stats at
both font sizes. Tests explicitly resolve layout direction for detached preview roots.
These check Android layout/rendering, not real player playback, device window placement,
or animation smoothness. Human checks below remain required.

## Manual regression checklist

`PlayerPinchChatTest` covers pinch takeover from hidden/sidebar/floating chat,
floating-chat disabled, unchanged ordinary pinches, normal double-tap transitions,
rejected duplicate claims and preservation of an unset chat preference. It invokes
the real listener and fragment chat/pinch methods with Android views and
preferences; player attachment and network chat are mocked. It does not simulate
the platform's double-tap timing or actual playback.

### Release resource encoding

- `LocalizedStringsTest` loads packaged resources in all 13 affected locales and
  checks representative accented/non-Latin strings. The 1.3 restoration was verified
  against every corresponding v1.2.1 resource value while preserving new Close labels.
- Use UTF-8 explicitly when scripts read/write source text on Windows. The older
  PowerShell/Python default Windows-1252 decoding can silently corrupt valid UTF-8.

### Launcher artwork

- `LauncherIconTest` checks packaged adaptive/round layers, cyan foreground color,
  opaque background, aligned themed silhouette/play counter and the adaptive safe
  circle. A native normal/light-themed/dark-themed preview is saved in
  `app/build/ui-previews/launcher-gem-native.png`. Legacy PNGs at all five densities
  and the drawable XML were checked against the supplied archive bytes.
- Device launcher masks, actual Android 13+ themed-icon selection, launcher cache
  refresh and pre-Oreo launcher display remain human QA; simulated themed rendering
  on API 28 does not exercise the Android 13 launcher implementation.

### Updater presentation and recovery

- `UpdateUiTest`: constrained portrait/landscape layouts at 100%/200% font size,
  fixed 48dp-or-larger actions, scrollable notes, missing metadata, Markdown heading
  deduplication, date validation, known/unknown download sizes, actual dialog action
  dispatch and short-window download-body scrolling. Native sample-data
  previews cover light/dark prompt and download layouts in `app/build/ui-previews/`.
- `UpdateAttemptTest`: successful preparation, network/installer errors, cancellation
  propagation and prevention of work in an already-cancelled job. These are policy/
  layout tests, not live downloads or OS installer tests.
- See the updater pass in `MANUAL_QA.md` for real permission, network, lifecycle and
  signed-upgrade checks. Local unsigned release assembly is not signed-RC approval.
### Selective upstream correctness ports (September 2026)

- `ChatReplayTimingTest`: positive sub-millisecond waits at high speeds still
  suspend for at least 1ms; normal timing, invalid speed fallback and cancellation.
  Both network and downloaded-chat replay use this policy.
- `FollowedChannelsDataSourceTest`: actual first/append paging loads using mocked
  repositories, returned account-follow rows, last-page termination, local/account
  merging and integrity failures.
- `TwitchEmoteSuggestionsTest`: native Twitch suggestions without third-party
  emotes, image metadata/current-channel priority, duplicate/blank names, snapshot
  replacement, and preserved third-party/chatter entries. Cached/fresh/emote-set
  ViewModel paths use the same publication helper; device account/reload QA remains.
- `AutoCompleteSnapshotTest`: real adapter filtering copies its source while holding
  the same lock as emote/chatter producers. Guarded lists catch unlocked reads
  deterministically, and subsequent filters see replaced Twitch suggestions.
- `SocialLinkLabelTest`: parsed destination host, www/subdomains, user-info, and
  absent/relative/opaque URLs. Labels do not establish destination trustworthiness.

### Controls / resume / headings follow-up

- `MediaSeekFallbackTest`: unhandled previous/next dispatch, already-handled events,
  key-up/cancelled/unrelated/malformed inputs and unavailable seek commands.
- `VideoResumePositionTest` / `PlayerResumeLookupTest`: exact completion boundary,
  unknown durations, Long millisecond conversion, explicit offsets bypassing the
  repository and downloaded-segment duration independent of its source offset.
- `HeadingFixPluginTest`: actual CommonMark parsing of compact/ordinary/setext
  headings, Unicode, nested containers, links, code and non-heading hashes.

### Player basics

- `PlayerSystemUiListenerTest`: real View callback dispatch, view-lifecycle destroy,
  idempotent portrait-style detach, repeated landscape registration, stale callback
  invalidation, decor replacement and old/new player ownership. These deterministic
  cleanup checks do not replace repeated rotation/stream-close LeakCanary device QA.
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


## Updater and release checks

```bash
python3 scripts/check-updater-contracts.py
bash scripts/check-updater-core.sh
node --test docs/site.test.js .github/workflows/release.test.mjs scripts/release/*.test.mjs
./gradlew test lintDebug assembleDebug assembleRelease
```

The standalone Kotlin runner uses kotlinc and a compatible coroutines JVM jar;
set KOTLINC_BIN and COROUTINES_JAR when needed. The Android Gradle suite runs
these same production-core cases with the Android adapter and resource tests.

On Windows, use Git Bash with Java 21 and the Android SDK available. Set
THYSTTV_GIT_BASH for a nonstandard installation. All native Windows verifier
cases must run on Windows; a shell script named .bat is not native validation.

Use the existing developer debug key for upgrades on a development device. An
unsigned release assembly is not an official candidate. See
[the 1.3 review guide](RELEASE_1_3_REVIEW.md) for validation and remaining checks,
and [RELEASE_PROCESS.md](RELEASE_PROCESS.md) for signed-candidate requirements.
