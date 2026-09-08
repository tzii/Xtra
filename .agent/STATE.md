# ThystTV current state

Status:  REVIEW
Agent:   Codex

Focus:   Device-verify the structural popup repair and compact Stats redesign.
Next:    Install app/build/outputs/apk/debug/app-debug.apk and run the popup/Stats
         regression pass in docs/MANUAL_QA.md, including playback/lifecycle checks.
Pointer: codex/player-ux-tablet-live-discovery
As-of:   2026-09-08 · 78493944 (implementation checkpoint)

Notes:   Popup host is outside 16:9 PlayerLayout; portrait uses space over chat,
         with fixed card/header and a single scrolling body. Stats has compact
         metrics, large-font reflow, and adaptive chart scales. Native previews
         inspected; 330 tests passed; debug assembly and lint passed (0 errors,
         333 warnings). Local UI/regression review and docs complete.
         Plan: .agent/plans/active/2026-09-08-popup-structure-stats-redesign.md.
         No device attached; actual window placement, animation feel, playback,
         gestures and floating-chat/lifecycle QA still required before merge.
         Implementation and findings committed on the feature branch; main untouched.
