# ThystTV Agent Map

## Staleness Note

This map reflects the current expected structure. If a listed path has moved, search the repo for the current location, use the current location, and update this map in the same PR when appropriate.

## Product Identity

ThystTV is viewer-first. Protect stable playback, fast VoD controls, floating chat, local stats, large-screen layouts, and polished open-source presentation.

## Current Release Target

Read:

- `docs/ROADMAP.md`
- `docs/RELEASE_1_2_PLAN.md`
- `CHANGELOG.md`

The 1.2 line should focus on quality, player UX, visual polish, and release readiness. Do not turn 1.2 work into broad architecture churn.

## Contribution And Style Conventions

Read:

- `CONTRIBUTING.md`

If more code-style rules are needed later, add `docs/CONVENTIONS.md` in a separate PR.

## Main Code Areas

### Android App

- App module: `app/`
- Build config: `app/build.gradle.kts`
- Main activity / app-level player entry point: `app/src/main/java/com/github/andreyasadchy/xtra/ui/main/MainActivity.kt`
- Player area: `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/`
- Chat/floating chat area: `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/`
- Stats area: `app/src/main/java/com/github/andreyasadchy/xtra/ui/stats/`
- Android resources: `app/src/main/res/`
- In-app updater/changelog UI: search under `app/src/main/java/com/github/andreyasadchy/xtra/ui/common/`, `app/src/main/java/com/github/andreyasadchy/xtra/ui/settings/`, and `app/src/main/res/layout/`

### Release

- Release process: `docs/RELEASE_PROCESS.md`
- Release notes: `docs/release-notes/`
- CI: `.github/workflows/ci.yml`
- Debug APK workflow: `.github/workflows/debug-build.yml`
- Release workflow: `.github/workflows/release.yml`
- Maintainer publication and verification: `scripts/release/publish-release.mjs`

### Upstream Xtra Sync

- Policy: `docs/UPSTREAM_SYNC.md`
- 1.2 ledger: `docs/RELEASE_1_2_UPSTREAM_COMMITS.md`

### Visual And Repo Presentation

- README: `README.md`
- Screenshots: `docs/images/screenshots/`
- Icon / branding assets: inspect `docs/images/` and launcher resources under `app/src/main/res/`
- Website/docs site: inspect `docs/index.html` before editing

## Work Classification

### Small Changes

Examples:

- typo fixes
- one string update
- one doc link
- one small screenshot replacement

Use a short plan in the PR summary. No ExecPlan required.

### Medium Changes

Examples:

- one focused player UI tweak
- one release workflow tweak
- one README/site section update
- one focused upstream port

Write a brief implementation plan before editing.

### Complex Changes

Examples:

- player lifecycle changes
- minimized-player / PiP / stream handoff work
- broad UI/resource changes
- release finalization
- upstream sync batch
- website redesign
- multi-file refactor

Use `.agent/PLANS.md`.

## Recommended Branch Names

- `agent/harness-docs`
- `agent/player-regression-fix`
- `agent/quality-dialog`
- `agent/visual-polish`
- `agent/release-sweep`
- `agent/upstream-sync-post-1.2`

## Public/Private Boundary

Public repo may contain:

- code
- docs
- agent instructions
- skills
- workflows
- issue/PR templates

Never commit:

- keystores
- API tokens
- OAuth/session tokens
- private user crash logs
- test account credentials
- unreleased private APKs
- `.private/`
