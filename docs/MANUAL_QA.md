# Manual QA Handoff Checklist

Use this checklist for PRs that affect player behavior, UI, layouts, release builds, or visual presentation.

Agents should mark **Required** when a human should test an area. Mark **Completed** only when a real human/device check was actually performed.

## Build Under Test

- Branch:
- Commit:
- APK/build:
- Device/Android version, if actually tested:
- Tester, if actually tested:
- Date, if actually tested:

## Checklist

| Area | Required | Completed | Notes |
| --- | --- | --- | --- |
| App launches | [ ] | [ ] | |
| Navigation smoke test | [ ] | [ ] | |
| Live stream opens | [ ] | [ ] | |
| VoD opens | [ ] | [ ] | |
| Player closes/reopens | [ ] | [ ] | |
| Live -> live switching | [ ] | [ ] | |
| Old audio does not continue after switch | [ ] | [ ] | |
| Mini-player -> new stream | [ ] | [ ] | |
| Minimize / restore | [ ] | [ ] | |
| PiP/background behavior | [ ] | [ ] | |
| Playback speed display/control | [ ] | [ ] | |
| Quality menu/selection | [ ] | [ ] | |
| Chat-only/audio-only restore | [ ] | [ ] | |
| Gestures: brightness/volume/seek | [ ] | [ ] | |
| Floating chat: open/drag/resize | [ ] | [ ] | |
| Floating chat readability over video | [ ] | [ ] | |
| Portrait phone layout | [ ] | [ ] | |
| Landscape phone layout | [ ] | [ ] | |
| Wide/tablet layout | [ ] | [ ] | |
| Split-screen/resized window | [ ] | [ ] | |
| Stats screen | [ ] | [ ] | |
| Stats filters/charts | [ ] | [ ] | |
| Updater/changelog Markdown and actions | [ ] | [ ] | |
| README/site screenshots render | [ ] | [ ] | |
| Icon/banner assets look correct | [ ] | [ ] | |
| No private account data exposed | [ ] | [ ] | |

### Popup / Stats redesign regression pass

- Portrait: open Quality, Speed and More over live/VoD video. Menus may cover chat;
  scroll to the last option with title, close button and card outline still visible.
- Repeat in landscape, side-chat/tablet and split-screen, RTL, and 200% font size.
  Auto is centered next to codec labels; no quality label or utility option clips.
- Close with close/back/outside tap, then interact with chat. Repeat rapid open/close,
  rotation, stream switching, minimize/restore, close/reopen and PiP/background.
- Check speed/quality changes, gestures and floating chat before/after dismissal.
- Stats: switch 7 days / 30 days / All time quickly; check compact metrics, streaks,
  range-label reachability, empty/minute-long/multi-hour charts, wide layouts and rotation.
- Repeat transitions with system animations disabled. Real-device feel and playback
  verification are still required even when Android view-measurement tests pass.

## Evidence

Screenshots, videos, logs, or APK links:

-

## Known Issues / Deferred QA

-
