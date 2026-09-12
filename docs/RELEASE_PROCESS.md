# Release Process

ThystTV publishes releases through a build-once, exact-byte promotion pipeline. A signed
release candidate (RC) is built and independently approved exactly once; the GitHub Release
then promotes those exact bytes without rebuilding or re-signing anything.

## Branch gate

Before any release work is merged:

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat lintDebug assembleDebug assembleRelease --no-daemon --console=plain
node --test docs/site.test.js
node --test .github/workflows/release.test.mjs scripts/release/*.test.mjs
```

Local `assembleRelease` must stay unsigned unless `app/release-keystore.jks` and every
credential (`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) are present; Gradle never
falls back to the debug key. Complete the branch-level manual smoke suite and record the
evidence in the release PR.

## Merge and freeze the RC SHA

Merge the release PR through the repository's normal review policy, without tagging during
the merge, then freeze the exact post-merge commit:

```powershell
git fetch origin master
$rcSha = git rev-parse origin/master
$rcSha
```

Record `$rcSha` out of tree in the merged PR/review record. Evidence added after this freeze
belongs in merged-PR comments/attachments or workflow artifacts, never in a new source commit.

## Signed verification-only workflow dispatch

Dispatch the release workflow against the frozen SHA:

```powershell
gh workflow run release.yml --repo tzii/ThystTV --ref master -f expected_rc_sha=$rcSha
```

`build_signed_rc` asserts the dispatch targets `master` at exactly `$rcSha`, runs unit tests
and lint before decoding any key, decodes `app/release-keystore.jks` with `umask 077` and an
exit cleanup, builds the signed APK, verifies package/version/code/certificate against the
pinned official certificate, and uploads the APK, APK checksum, manifest, and manifest
checksum as artifact `{rc_sha}-{run_id}` with `overwrite: false`.

## Independent APK and manifest verification

Require event `workflow_dispatch`, conclusion `success`, and `head_sha == $rcSha`. Download
the `{rc_sha}-{run_id}` bundle and independently re-run the APK package/version/code/
certificate/checksum/manifest verification. Then smoke-test the exact signed RC APK,
including the logged-in cold-start offline Saved/Bookmarks/Downloads scenario, the
network-restoration retry, and updater/install behavior. Manual approval applies to these
exact bytes, not to a local rebuild.

## Mandatory GPT-5.6-sol/high adversarial review

Provide the exact base/head SHAs, full diff, workflow, release notes, upstream SHAs,
automated results, signed-RC run ID, manifest, checksum, certificate result, and manual
evidence. Require correctness, security, signing, lifecycle, compatibility, and regression
attacks. Preserve the raw attributed transcript outside the source tree. If GPT-5.6-sol/high
is unavailable, stop for user direction.

## Best-effort verified GLM 5.2 review

Use a local subagent or external crew harness only after confirming the actual model identity
is GLM 5.2. Give it an isolated snapshot and the same evidence without the first review's
conclusions. Preserve raw output outside the source tree. If unavailable, record the failed
check and use one clearly named independent external fallback reviewer.

## Finding disposition and RC invalidation

For each finding record reviewer, severity, verification, disposition, commit, and retest
evidence in merged-PR comments/attachments or workflow artifacts. Any code, docs, metadata,
or workflow change invalidates the RC: it requires a narrow follow-up PR, a new frozen master
SHA, a new signed RC bundle, the full smoke suite, and both adversarial lanes again.

## Release tag creation authorization and update/deletion protection

Before tagging, verify both rulesets exist exactly once and validate completely:

- `Protect release tags` targets exactly `refs/tags/v*` with no exclusions, is `active`,
  contains exactly the `deletion` and `update` rules, and has no bypass actors. While it
  remains active, nobody can update or delete `v*` tags directly.
- `Authorize release tag creation` targets exactly `refs/tags/v*` with no exclusions, is
  `active`, contains only the `creation` rule, and has exactly one always-bypass actor:
  GitHub user `tzii`, numeric ID `178386212`, type `User`. Only that exact user may create
  `v*` tags.

Record both ruleset IDs and full configurations in the out-of-tree release evidence before
tagging, and re-run every invariant against their post-publication configurations.
Use the maintainer's local GitHub CLI authentication for these checks. GitHub only returns
`bypass_actors` to callers with write access to the ruleset; the workflow's built-in token
cannot verify this authority. An omitted or malformed bypass list is an error, never an
empty list. See [GitHub's ruleset API documentation](https://docs.github.com/en/rest/repos/rules#get-a-repository-ruleset).
Administrators can still edit the rulesets themselves even though direct tag mutation is
blocked while the rules remain active; never weaken, duplicate, or silently re-target a
ruleset to work around a failed validation — stop for deliberate policy correction instead.

## Annotated tag fields

Create an annotated tag whose message binds exactly one approved RC run and manifest:

```text
RC-Workflow-Run: 123456789
RC-Manifest-SHA256: 64-lowercase-hex-characters
```

The numeric/digest examples describe format only; maintainers must copy both values from the
approved RC run and compare them with the values approved during review before tagging. The
peeled tag SHA must equal the frozen RC SHA. Lightweight tags, duplicate tag fields, or a
peeled SHA mismatch stop the release.

## Read-only tag verification

Pushing the tag runs `verify_release_tag`, which verifies `github.actor == 'tzii'`, requires an
annotated tag object, parses exactly one run ID and manifest digest from the tag message,
validates the RC workflow run (exact run ID, `workflow_dispatch`, `success`, release workflow
path, `head_sha` equal to the peeled SHA), downloads artifact `{peeled_sha}-{run_id}`, and
re-verifies the manifest digest, APK checksum, package/version/code/certificate, and the
full promotion binding. Its summary hands publication to the maintainer.

This job has only `contents: read` and `actions: read`. A green tag job means the candidate
was verified; it does **not** mean a release was published or full ruleset authority was
verified. No repository-admin credential is stored in Actions. Signing secrets remain
exclusive to the signed-RC build.

## Maintainer exact-byte publication

After the required reviews and device QA, use `scripts/release/publish-release.mjs` from
a trusted checkout containing this tool. Prerequisites are Node.js, Git, GitHub CLI
authenticated as `tzii` (user ID `178386212`) with ruleset write access, Java 21, and an
Android SDK with command-line tools and build-tools. On Windows put Git Bash and Java on
`PATH`, and set `ANDROID_HOME` to the SDK directory. The frozen RC commit must be available
locally; the tool reads its version, notes and certificate with `git show`.

Fetch the frozen source and tags, then verify without publishing (replace the sample tag
with the approved tag):

```powershell
git fetch origin master --tags
node scripts/release/publish-release.mjs v1.3.1
```

The default is read-only on GitHub. It verifies the authenticated maintainer, annotated
tag, full ruleset configurations, successful signed-RC run, exact four-file bundle,
manifest binding and APK signature/package/version/checksum. It reads release notes from
the frozen commit and does not build or re-sign the APK. Review the JSON result and
retained evidence. `ready-for-publication` means these automated checks passed; the command
does not perform or certify device QA or independent review.

Once approval for the exact candidate is recorded, publish with:

```powershell
node scripts/release/publish-release.mjs v1.3.1 --publish
```

This repeats verification, checks the tag and policies immediately before publication,
then uses `gh release create --verify-tag` with the frozen release notes and exactly three
assets: APK, APK checksum and RC manifest. It downloads the public assets, compares their
bytes with the signed bundle, confirms the release is stable/latest, and rechecks tag and
ruleset invariants. It never edits an existing release or overwrites assets. An existing
matching stable release is verified and reported as `existing-release-verified`; drafts,
prereleases and mismatched assets stop for investigation. A failed create/upload may leave
a partial release; inspect it before attempting recovery.

Each invocation retains evidence in a fresh temporary directory printed in its output.
Use `--evidence-dir <new-directory>` to choose another location; its parent must exist and
the chosen directory must not. Do not reuse a directory from a previous attempt.

The historical v1.3.0 tag run failed because its built-in token could not see the creation
bypass actor. The approved exact-byte bundle was subsequently published through the
maintainer CLI and verified. That frozen run remains failed; this workflow change applies
to future tags. Do not rerun it to republish 1.3.0, or move/recreate its tag.

## Post-publication checks and immutable-tag rollback policy

Verify the release page, the required APK/checksum/manifest assets, updater discovery, the
install/upgrade path, canonical website download links, and the certificate fingerprint
against [`APK_VERIFICATION.md`](APK_VERIFICATION.md). Fetch both rulesets again by their
recorded IDs and re-run every invariant against their post-publication configurations.

`v*` tags are immutable while the protection rulesets remain active: never move, retarget,
or recreate a published tag, and never overwrite release assets. If a blocker appears after
publication, mark the affected version, prepare a new `versionCode`/`versionName`, and repeat
the entire RC/review pipeline with a new tag.

## Publication failure cases

Publication stops on any of: missing or expired run artifacts, non-dispatch runs,
unsuccessful conclusions, wrong head SHA, lightweight tags, duplicate or missing tag fields,
version mismatch, wrong package/certificate/checksum, a missing, duplicate, inactive, or
malformed release-tag ruleset, an unavailable bypass list, a wrong creation authority, any
protection bypass, a missing required release asset, or any attempt to rebuild the APK
during promotion.
