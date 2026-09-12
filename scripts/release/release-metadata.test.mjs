import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import {
  assertCompleteReleaseNotes,
  createManifest,
  parseTagMessage,
  readVersionConfig,
  verifyPromotion,
  verifyReleaseTagRulesets,
  verifyWorkflowRun,
} from "./release-metadata.mjs";

const officialCertificate = "7f8a843b92561e0fff49d77589f54d95169f6b739fcf235b52d4ca6b8ab71f4a";
const validManifestFields = {
  repository: "tzii/ThystTV",
  workflow: "release.yml",
  runId: 12345,
  rcSha: "b".repeat(40),
  versionName: "1.2.1",
  versionCode: 11,
  packageId: "com.tzii.thysttv",
  apkFilename: "ThystTV-1.2.1.apk",
  apkSha256: "c".repeat(64),
  certificateSha256: officialCertificate,
};

test("reads one version name and code", () => {
  assert.deepEqual(readVersionConfig('versionCode = 11\nversionName = "1.2.1"'), {
    versionName: "1.2.1",
    versionCode: 11,
  });
});

test("rejects duplicate or missing Gradle values", () => {
  assert.throws(() => readVersionConfig('versionName = "1.2.1"'));
  assert.throws(() => readVersionConfig("versionCode = 11"));
  assert.throws(() => readVersionConfig('versionName = "1.2.1"\nversionName = "1.2.2"\nversionCode = 11'));
  assert.throws(() => readVersionConfig('versionName = "1.2.1"\nversionCode = 11\nversionCode = 12'));
});

test("rejects malformed semantic versions and nonpositive version codes", () => {
  assert.throws(() => readVersionConfig('versionName = "1.2"\nversionCode = 11'));
  assert.throws(() => readVersionConfig('versionName = "1.2.1.3"\nversionCode = 11'));
  assert.throws(() => readVersionConfig('versionName = "v1.2.1"\nversionCode = 11'));
  assert.throws(() => readVersionConfig('versionName = "1.2.1"\nversionCode = 0'));
});

test("rejects incomplete notes", () => {
  assert.throws(() => assertCompleteReleaseNotes("Release notes were not found"));
  assert.throws(() => assertCompleteReleaseNotes("# ThystTV 1.2.1\n\nTODO"));
  assert.throws(() => assertCompleteReleaseNotes("Short note."));
});

test("accepts complete notes", () => {
  assert.doesNotThrow(() =>
    assertCompleteReleaseNotes(
      "# ThystTV 1.2.1\n\nCompatibility fixes, offline bookmarks, 7TV channel emotes, video download fixes, and release verification improvements."
    )
  );
});

test("tag message contains exactly one run and manifest digest", () => {
  assert.deepEqual(parseTagMessage("ThystTV 1.2.1\n\nRC-Workflow-Run: 12345\nRC-Manifest-SHA256: " + "a".repeat(64)), {
    runId: 12345,
    manifestSha256: "a".repeat(64),
  });
  assert.throws(() => parseTagMessage("RC-Workflow-Run: 1\nRC-Workflow-Run: 2\nRC-Manifest-SHA256: " + "a".repeat(64)));
});

test("rejects tag messages with missing or duplicated fields", () => {
  assert.throws(() => parseTagMessage("RC-Workflow-Run: 12345"));
  assert.throws(() => parseTagMessage("RC-Manifest-SHA256: " + "a".repeat(64)));
  assert.throws(() => parseTagMessage("RC-Workflow-Run: 12345\nRC-Manifest-SHA256: " + "a".repeat(64) + "\nRC-Manifest-SHA256: " + "b".repeat(64)));
  assert.throws(() => parseTagMessage("RC-Workflow-Run: 12345\nRC-Manifest-SHA256: " + "a".repeat(63)));
  assert.throws(() => parseTagMessage("RC-Workflow-Run: 12345\nRC-Manifest-SHA256: " + "g".repeat(64)));
});

test("workflow run is a successful dispatch for the peeled tag commit", () => {
  assert.doesNotThrow(() => verifyWorkflowRun({
    id: 12345,
    event: "workflow_dispatch",
    conclusion: "success",
    head_sha: "b".repeat(40),
    path: ".github/workflows/release.yml",
  }, { runId: 12345, rcSha: "b".repeat(40) }));
});

test("rejects wrong run event, conclusion, path, run ID, or SHA", () => {
  const base = {
    id: 12345,
    event: "workflow_dispatch",
    conclusion: "success",
    head_sha: "b".repeat(40),
    path: ".github/workflows/release.yml",
  };
  const expected = { runId: 12345, rcSha: "b".repeat(40) };
  assert.throws(() => verifyWorkflowRun({ ...base, event: "push" }, expected));
  assert.throws(() => verifyWorkflowRun({ ...base, conclusion: "failure" }, expected));
  assert.throws(() => verifyWorkflowRun({ ...base, conclusion: null }, expected));
  assert.throws(() => verifyWorkflowRun({ ...base, path: ".github/workflows/ci.yml" }, expected));
  assert.throws(() => verifyWorkflowRun({ ...base, path: ".github/workflows/release.yml@refs/heads/master" }, expected));
  assert.throws(() => verifyWorkflowRun({ ...base, path: 42 }, expected));
  assert.throws(() => verifyWorkflowRun({ ...base, id: 99999 }, expected));
  assert.throws(() => verifyWorkflowRun({ ...base, head_sha: "d".repeat(40) }, expected));
});

test("promotion binds source, run, APK, certificate, and version", () => {
  const manifest = createManifest(validManifestFields);
  assert.doesNotThrow(() => verifyPromotion(manifest, validManifestFields));
});

test("createManifest rejects missing, unknown, and malformed fields", () => {
  for (const key of Object.keys(validManifestFields)) {
    const partial = { ...validManifestFields };
    delete partial[key];
    assert.throws(() => createManifest(partial), new RegExp(`missing manifest field: ${key}`));
  }
  assert.throws(() => createManifest({ ...validManifestFields, apkFilename: "" }), /missing manifest field: apkFilename/);
  assert.throws(() => createManifest({ ...validManifestFields, extra: true }), /unknown manifest field/);
  assert.throws(() => createManifest({ ...validManifestFields, rcSha: "short" }), /invalid RC SHA/);
  assert.throws(() => createManifest({ ...validManifestFields, apkSha256: "x".repeat(64) }), /invalid SHA-256 field/);
  assert.throws(() => createManifest({ ...validManifestFields, certificateSha256: "c".repeat(63) }), /invalid SHA-256 field/);
});

test("verifyPromotion rejects every mismatched field", () => {
  const manifest = createManifest(validManifestFields);
  for (const [key, value] of Object.entries(validManifestFields)) {
    const expected = { ...validManifestFields, [key]: typeof value === "number" ? value + 1 : `${value}-tampered` };
    assert.throws(() => verifyPromotion(manifest, expected), new RegExp(`promotion mismatch: ${key}`));
  }
  assert.throws(() => verifyPromotion({ extra: true }, validManifestFields), /manifest schema mismatch/);
});

test("1.3.0 release metadata is finalized", () => {
  const buildGradle = fs.readFileSync("app/build.gradle.kts", "utf8");
  const changelog = fs.readFileSync("CHANGELOG.md", "utf8");
  const bundledChangelog = fs.readFileSync("app/src/main/res/raw/thysttv_changelog.md", "utf8");
  const releaseNotes = fs.readFileSync("docs/release-notes/1.3.0.md", "utf8");
  const fastlaneNotes = fs.readFileSync("fastlane/metadata/android/en-US/changelogs/12.txt", "utf8");

  assert.deepEqual(readVersionConfig(buildGradle), {
    versionName: "1.3.0",
    versionCode: 12,
  });
  assert.match(changelog, /^## \[1\.3\.0\] - \d{4}-\d{2}-\d{2}$/m);
  assert.match(bundledChangelog, /^# ThystTV 1\.3\.0$/m);
  assert.match(releaseNotes, /^# ThystTV 1\.3\.0$/m);
  assert.match(
    releaseNotes,
    /landscape display setting migrates automatically/i,
  );
  assert.doesNotMatch(releaseNotes, /TBD|TODO|placeholder|Release notes were not found/i);
  assert.ok(fastlaneNotes.length <= 500);
});

test("release tag policy separates creation authority from immutable refs", () => {
  const common = {
    target: "tag",
    enforcement: "active",
    conditions: { ref_name: { include: ["refs/tags/v*"], exclude: [] } },
  };
  assert.doesNotThrow(() => verifyReleaseTagRulesets({
    ...common,
    name: "Protect release tags",
    bypass_actors: [],
    rules: [{ type: "deletion" }, { type: "update" }],
  }, {
    ...common,
    name: "Authorize release tag creation",
    bypass_actors: [{ actor_id: 178386212, actor_type: "User", bypass_mode: "always" }],
    rules: [{ type: "creation" }],
  }, { userId: 178386212 }));
});

test("release tag policy rejects misconfigured rulesets", () => {
  const common = {
    target: "tag",
    enforcement: "active",
    conditions: { ref_name: { include: ["refs/tags/v*"], exclude: [] } },
  };
  const protection = {
    ...common,
    name: "Protect release tags",
    bypass_actors: [],
    rules: [{ type: "deletion" }, { type: "update" }],
  };
  const authorization = {
    ...common,
    name: "Authorize release tag creation",
    bypass_actors: [{ actor_id: 178386212, actor_type: "User", bypass_mode: "always" }],
    rules: [{ type: "creation" }],
  };
  const expected = { userId: 178386212 };

  // Wrong names, target, enforcement, includes, exclusions.
  assert.throws(() => verifyReleaseTagRulesets({ ...protection, name: "Guard release tags" }, authorization, expected), /wrong release ruleset name/);
  assert.throws(() => verifyReleaseTagRulesets(protection, { ...authorization, name: "Allow release tags" }, expected), /wrong release ruleset name/);
  assert.throws(() => verifyReleaseTagRulesets({ ...protection, target: "branch" }, authorization, expected), /not an active tag ruleset/);
  assert.throws(() => verifyReleaseTagRulesets(protection, { ...authorization, enforcement: "evaluate" }, expected), /not an active tag ruleset/);
  assert.throws(() => verifyReleaseTagRulesets({ ...protection, conditions: { ref_name: { include: ["refs/tags/v*", "refs/tags/other"], exclude: [] } } }, authorization, expected), /wrong tag target/);
  assert.throws(() => verifyReleaseTagRulesets(protection, { ...authorization, conditions: { ref_name: { include: ["refs/tags/v*"], exclude: ["refs/tags/v0.*"] } } }, expected), /wrong tag target/);

  // Missing/extra protection rule types and any protection bypass.
  assert.throws(() => verifyReleaseTagRulesets({ ...protection, rules: [{ type: "deletion" }] }, authorization, expected), /exactly deletion and update/);
  assert.throws(() => verifyReleaseTagRulesets({ ...protection, rules: [{ type: "deletion" }, { type: "update" }, { type: "creation" }] }, authorization, expected), /exactly deletion and update/);
  assert.throws(() => verifyReleaseTagRulesets({ ...protection, bypass_actors: [{ actor_id: 178386212, actor_type: "User", bypass_mode: "always" }] }, authorization, expected), /must not have bypass actors/);

  // Missing/extra creation rules and bypass actor problems.
  assert.throws(() => verifyReleaseTagRulesets(protection, { ...authorization, rules: [] }, expected), /must contain only creation/);
  assert.throws(() => verifyReleaseTagRulesets(protection, { ...authorization, rules: [{ type: "creation" }, { type: "update" }] }, expected), /must contain only creation/);
  assert.throws(() => verifyReleaseTagRulesets(protection, { ...authorization, bypass_actors: [] }, expected), /creation authority mismatch/);
  assert.throws(() => verifyReleaseTagRulesets(protection, {
    ...authorization,
    bypass_actors: [
      { actor_id: 178386212, actor_type: "User", bypass_mode: "always" },
      { actor_id: 1, actor_type: "User", bypass_mode: "always" },
    ],
  }, expected), /creation authority mismatch/);
  assert.throws(() => verifyReleaseTagRulesets(protection, { ...authorization, bypass_actors: [{ actor_id: 999, actor_type: "User", bypass_mode: "always" }] }, expected), /creation authority mismatch/);
  assert.throws(() => verifyReleaseTagRulesets(protection, { ...authorization, bypass_actors: [{ actor_id: 178386212, actor_type: "RepositoryRole", bypass_mode: "always" }] }, expected), /creation authority mismatch/);
  assert.throws(() => verifyReleaseTagRulesets(protection, { ...authorization, bypass_actors: [{ actor_id: 178386212, actor_type: "User", bypass_mode: "pull_request" }] }, expected), /creation authority mismatch/);
});
