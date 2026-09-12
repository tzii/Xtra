# Changelog

All notable changes to ThystTV should be documented here.

## [Unreleased]

## [1.3.0] - 2026-09-11

### Added
- Consistent Quality, Speed, Stream volume and More player overlays with fixed headers and reachable options.
- Canonical Fit/Fill/Stretch landscape display modes, pinch feedback and a playback gesture guide.
- Live status and stream entry points in channel search and profiles.

### Changed
- Improved tablet mini-player sizing, gesture feedback and stable Quality gear behavior through rotation.
- Redesigned compact Stats cards and improved chart scales, range controls and animation interruption.
- Refreshed adaptive, themed and legacy gem launcher artwork.
- Refreshed the update prompt with clear version details, rendered release notes and fixed actions that reflow at large text sizes.
- Shared retained download/installer state across updater entry points, with pause/recreation recovery, cancel/retry and browser fallback.
- Updater dialogs follow host-window resizing and request install permission when needed.

### Fixed
- Restored correctly encoded translations across 13 locales after a source-edit encoding regression.
- Released the landscape system-UI listener when a player closes or its view is destroyed.
- Kept high-speed network/downloaded VoD chat replay responsive and preserved appended followed-channel pages.
- Restored native Twitch autocomplete suggestions and synchronized suggestion snapshots during refresh.
- Restarted completed saved videos at zero while preserving unfinished positions and explicit timestamps.
- Handled unclaimed legacy headset previous/next buttons once per press.
- Rendered compact Markdown headings and displayed destination hosts on channel social links.

### Security
- Replaced exported installer-intent forwarding with a private callback receiver, per-attempt session identity and validated system confirmation handoff.

### Release tooling
- Added native Windows verifier coverage and corrected SDK batch launcher handling.
- Version 1.3.0 uses version code 12. Full scope and upgrade notes: `docs/release-notes/1.3.0.md`.

## [1.2.1] - 2026-08-10

### Fixed
- Bookmark VOD expiry now uses Twitch's current 7-day regular, 14-day Affiliate, and 60-day Partner/Prime/Turbo retention policy (manual ports from upstream Xtra `627d440f` and `15dd7d9e`).
- Stream results with both broadcaster ID and login missing are now filtered before display (manual port from upstream Xtra `cfa61fc8`).
- USERNOTICE chat events without message text now preserve their `msg-id` for subscription, gift, and raid handling (manual port from upstream Xtra `ac0afa3d`).
- Twitch GraphQL and playback-token requests now use the canonical no-trailing-slash endpoint (manual port from upstream Xtra `345cff59`).
- Downloaded video thumbnails now update correctly when moving downloaded files (manual port from upstream Xtra `12a8fac5`).
- Channel point reward cost in chat now uses locale-aware number formatting (manual port from upstream Xtra `12a8fac5`).
- Saved bookmarks and the Downloads tab stay reachable offline while bookmark metadata refreshes are deferred instead of blocking access.
- 7TV channel emotes now load from the referenced `/v3/emote-sets/{setId}` endpoint when the channel response omits or returns an empty embedded set (manual port from upstream Xtra `9c47305f`).
- Partial video downloads now include the HLS segment that overlaps the requested start time (manual port from upstream Xtra `8a8b99c5`).

### Changed
- Bookmark expiry estimates for regular accounts now assume 7-day VOD retention instead of 14 days; this changes bookmark expiry display and sorting only and never deletes Twitch content.

### Release tooling
- Local release builds remain unsigned; signed release candidates are built in CI and promoted to a published release only after exact-byte verification against the release manifest.
- README and website download instructions now point to GitHub Releases with checksum and signing-certificate verification guidance (`docs/APK_VERIFICATION.md`).

## [1.2.0] - 2026-05-08

Major milestone release with the new ThystTV updater/changelog flow, custom quality and speed dialogs, local stats range filters, refreshed README/GitHub Pages assets, release automation, and focused upstream Xtra fixes.

See `docs/release-notes/1.2.0.md`.

## [1.1.6] - 2026-04-19

See `docs/release-notes/1.1.6.md`.

## [1.1.5] - 2026-04-15

See `docs/release-notes/1.1.5.md`.

## [1.1.4] - 2026-04-13

See `docs/release-notes/1.1.4.md`.
