# ThystTV 1.3.0

## Player and viewing

- Consistent Quality, Speed, Stream volume and More overlays with fixed headers and reachable options.
- Fit/Fill/Stretch landscape display modes, pinch feedback, a gesture guide and improved tablet mini-player sizing.
- Stable Quality gear through rotation and clearer brightness, device-volume and stream-volume feedback.
- Closed players release their landscape system-bar listener.
- Completed saved videos restart at zero; unfinished positions and explicit timestamps stay respected.
- Legacy headset previous/next controls seek once per press.

## Discovery, chat and Stats

- Live status and stream entry points in channel search and profiles.
- Responsive high-speed VoD chat replay, including downloaded chat.
- Correct followed-channel paging and native Twitch emote autocomplete refresh.
- Destination-host labels for social links and compact Markdown heading support.
- Compact Stats cards, readable chart scales and smoother range changes.

## Updates and appearance

- Clear update metadata and release notes, fixed actions and live window resizing.
- Shared download and installer state survives screen pauses/recreation while the process remains alive.
- Cancel/retry/browser recovery, install-permission guidance and private session-specific installer callbacks.
- Refreshed adaptive/themed/legacy gem icon and restored localized text.

## Upgrade notes

Landscape display settings migrate automatically to Fit/Fill/Stretch; no database schema migration is added.
Updater downloads do not resume after process death. Cronet/HttpEngine may report only final progress.
New updater captions use English fallback where translations are unavailable.
For unfinished downloads originally started on 1.2.0, delete and restart those downloads.

# ThystTV 1.2.1

ThystTV 1.2.1 is a focused compatibility and reliability release.

## Compatibility fixes

- Bookmark expiry now follows Twitch's current retention policy: 7 days for regular accounts, 14 days for Affiliates, and 60 days for Partner/Prime/Turbo accounts.
- Stream results with missing broadcaster identity are filtered before display.
- Chat subscription, gift, and raid events without text keep their event metadata.
- Twitch GraphQL and playback-token requests use the canonical endpoint.
- Downloaded video thumbnails update correctly after moving files.

## Bookmarks and downloads

- Saved bookmarks and the Downloads tab stay reachable offline; metadata refreshes are deferred instead of blocking access.
- Bookmark expiry estimates for regular accounts now use 7-day retention (display and sorting only).
- Partial downloads include the HLS segment overlapping the requested start time.
- 7TV channel emotes load via the emote-set endpoint fallback.

## Release verification

- Signed releases are verified by APK checksum and official signing certificate before install (see the APK verification guide).

# ThystTV 1.2.0

ThystTV 1.2.0 is the first major milestone release for the fork. It brings the app closer to a complete ThystTV experience with player polish, local stats improvements, a ThystTV-owned updater, bundled changelog support, refreshed project docs, and release automation.

## Player and controls

- Added a custom fullscreen quality picker with clearer video quality, audio-only, and chat-only controls.
- Fixed the landscape audio-only/manual-quality edge case that could collapse the quality list to only Auto and utility modes.
- Improved fullscreen playback speed controls and gesture feedback.
- Made VoD scrubbing duration-aware so long videos are easier to navigate.
- Cleaned up minimized player presentation on wide aspect ratios.

## Updater and changelog

- Update checks now use ThystTV GitHub releases by default.
- Release notes render in the updater and changelog screens.
- APK downloads show progress before the install handoff.
- A bundled changelog is available when network release data cannot be loaded.

## Stats and layout

- Added stats filters for 7 days, 30 days, and all time.
- Polished stats range controls.
- Improved layout behavior for phones, tablets, and wider screens.

## Stability and upstream sync

- Included a focused Media3/ExoPlayer rollback from upstream Xtra as a fix candidate for the player crash regression reported in issue #5.
- Included or adapted upstream Xtra fixes for strings, stream download quality, ProGuard, unraid message handling, and updater download progress.
- Deferred broad upstream player rewrites, Hilt removal, generated query churn, network refactors, and repo-wide rename churn until after 1.2.

## Thanks

Thanks to @Bijman for opening issue #5 and providing LogFox captures for the random player crash regression. Those logs helped narrow the regression window and guided the focused Media3/ExoPlayer rollback in this release.

Thanks also to the user who reported the landscape quality-dialog regression before release.
