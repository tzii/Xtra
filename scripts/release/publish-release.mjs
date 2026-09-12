import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import {
  assertCompleteReleaseNotes, parseTagMessage, readVersionConfig,
  verifyPromotion, verifyReleaseTagRulesets, verifyWorkflowRun,
} from "./release-metadata.mjs";

const repository = "tzii/ThystTV";
const authorityId = 178386212;
const defaultRoot = fileURLToPath(new URL("../../", import.meta.url));
const sha256 = (file) => createHash("sha256").update(fs.readFileSync(file)).digest("hex");
const readJson = (file) => JSON.parse(fs.readFileSync(file, "utf8"));
const execute = (command, args, options) => execFileSync(command, args, {
  ...options, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"],
});

export function parseArguments(args) {
  const [tag, ...flags] = args;
  if (!/^v\d+\.\d+\.\d+$/.test(tag ?? "")) throw new Error("Expected a release tag such as v1.3.1");
  let publish = false;
  let directory;
  while (flags.length) {
    const flag = flags.shift();
    if (flag === "--publish" && !publish) publish = true;
    else if (flag === "--evidence-dir" && directory === undefined && flags[0] && !flags[0].startsWith("--")) {
      directory = path.resolve(flags.shift());
    } else throw new Error(`Unknown, duplicate or incomplete option: ${flag}`);
  }
  return { tag, publish, directory };
}

// Verification is the default. --publish is the only path that can create a release.
// Evidence is retained in a new directory; existing files/assets are never replaced.
export function publishRelease({ tag, publish = false, directory, root = defaultRoot }, run = execute) {
  parseArguments([tag]);
  const evidence = directory ? path.resolve(directory) : fs.mkdtempSync(path.join(os.tmpdir(), "thysttv-release-"));
  if (directory) fs.mkdirSync(evidence); // Refuse an existing evidence directory, including stale downloads.
  const save = (name, value) => fs.writeFileSync(path.join(evidence, name), JSON.stringify(value, null, 2) + "\n");
  const invoke = (command, args, options = {}) => run(command, args, { cwd: root, ...options }).toString();
  const gh = (args) => invoke("gh", args);
  const api = (endpoint) => JSON.parse(gh(["api", endpoint]));
  const source = (sha, file) => invoke("git", ["show", `${sha}:${file}`]);
  let publicationAttempted = false;
  let created = false;

  try {
    const actor = api("user");
    assert.equal(actor.login, "tzii", "Publication requires the maintainer tzii");
    assert.equal(actor.id, authorityId, "Unexpected maintainer identity");
    assert.equal(actor.type, "User", "Publication requires a user credential");
    save("actor.json", { login: actor.login, id: actor.id, type: actor.type });

    const inspectTag = (phase) => {
      const ref = api(`repos/${repository}/git/ref/tags/${tag}`);
      assert.equal(ref.object.type, "tag", "Release tag must be annotated");
      assert.match(ref.object.sha, /^[0-9a-f]{40}$/);
      const object = api(`repos/${repository}/git/tags/${ref.object.sha}`);
      assert.equal(object.sha, ref.object.sha);
      assert.equal(object.tag, tag);
      assert.equal(object.object.type, "commit");
      assert.match(object.object.sha, /^[0-9a-f]{40}$/);
      const binding = parseTagMessage(object.message);
      assert.ok(Number.isSafeInteger(binding.runId) && binding.runId > 0, "Invalid RC run ID");
      const result = { objectSha: object.sha, rcSha: object.object.sha, ...binding };
      save(`tag-${phase}.json`, { ref, object });
      return result;
    };
    const inspectPolicies = (phase) => {
      const pages = JSON.parse(gh(["api", "--paginate", "--slurp", `repos/${repository}/rulesets`]));
      assert.ok(Array.isArray(pages) && pages.every(Array.isArray), "Malformed ruleset listing");
      const list = pages.flat();
      const policies = ["Protect release tags", "Authorize release tag creation"].map((name) => {
        const matches = list.filter((rule) => rule.name === name);
        assert.equal(matches.length, 1, `Expected exactly one ${name} ruleset`);
        assert.ok(Number.isSafeInteger(matches[0].id) && matches[0].id > 0);
        const rule = api(`repos/${repository}/rulesets/${matches[0].id}`);
        assert.equal(rule.id, matches[0].id);
        return rule;
      });
      save(`policy-${phase}.json`, { list, protection: policies[0], authorization: policies[1] });
      verifyReleaseTagRulesets(...policies, { userId: authorityId });
      return policies.map((rule) => rule.id);
    };

    const binding = inspectTag("before");
    const policyIds = inspectPolicies("before");
    const runEndpoint = `repos/${repository}/actions/runs/${binding.runId}`;
    const runRecord = api(runEndpoint);
    verifyWorkflowRun(runRecord, binding);
    save("run.json", runRecord);
    const version = readVersionConfig(source(binding.rcSha, "app/build.gradle.kts"));
    assert.equal(`v${version.versionName}`, tag, "Tag/version mismatch");
    const certificate = source(binding.rcSha, "scripts/release/official-signing-certificate.sha256").trim();
    assert.match(certificate, /^[0-9a-f]{64}$/);
    assert.equal(certificate, fs.readFileSync(path.join(root, "scripts/release/official-signing-certificate.sha256"), "utf8").trim());
    const notes = source(binding.rcSha, `docs/release-notes/${version.versionName}.md`);
    assertCompleteReleaseNotes(notes);
    const notesFile = path.join(evidence, "release-notes.md");
    fs.writeFileSync(notesFile, notes);
    const apkName = `ThystTV-${version.versionName}.apk`;
    const bundle = path.join(evidence, "bundle");
    fs.mkdirSync(bundle);
    gh(["run", "download", String(binding.runId), "--repo", repository, "--name", `${binding.rcSha}-${binding.runId}`, "--dir", bundle]);
    const bundleFiles = [apkName, `${apkName}.sha256`, "rc-manifest.json", "rc-manifest.json.sha256"].sort();
    const inspectFiles = (folder, names) => {
      assert.deepEqual(fs.readdirSync(folder).sort(), [...names].sort(), "Unexpected asset set");
      for (const name of names) assert.ok(fs.lstatSync(path.join(folder, name)).isFile(), `Not a regular file: ${name}`);
    };
    const inspectBundle = () => {
      inspectFiles(bundle, bundleFiles);
      for (const name of [apkName, "rc-manifest.json"]) {
        assert.equal(fs.readFileSync(path.join(bundle, `${name}.sha256`), "utf8").trim(), `${sha256(path.join(bundle, name))}  ${name}`, `Checksum mismatch: ${name}`);
      }
      assert.equal(sha256(path.join(bundle, "rc-manifest.json")), binding.manifestSha256, "Tag/manifest digest mismatch");
      const expected = {
        repository, workflow: "release.yml", runId: binding.runId, rcSha: binding.rcSha,
        ...version, packageId: "com.tzii.thysttv", apkFilename: apkName,
        apkSha256: sha256(path.join(bundle, apkName)), certificateSha256: certificate,
      };
      verifyPromotion(readJson(path.join(bundle, "rc-manifest.json")), expected);
      return expected;
    };
    const expected = inspectBundle();
    const apkEvidence = invoke("bash", [path.join(root, "scripts/release/verify-apk.sh").replaceAll("\\", "/")], {
      env: { ...process.env, APK_PATH: path.join(bundle, apkName).replaceAll("\\", "/"),
        EXPECTED_PACKAGE_ID: expected.packageId, EXPECTED_VERSION_NAME: version.versionName,
        EXPECTED_VERSION_CODE: String(version.versionCode), EXPECTED_CERT_SHA256: certificate },
    });
    fs.writeFileSync(path.join(evidence, "apk-evidence.txt"), apkEvidence);
    assert.deepEqual(Object.fromEntries(apkEvidence.trim().split(/\r?\n/).map((line) => line.split("="))), {
      package_id: expected.packageId, version_name: expected.versionName, version_code: String(expected.versionCode),
      certificate_sha256: certificate, apk_sha256: expected.apkSha256,
    }, "APK verifier output does not match approved manifest");

    const releaseEndpoint = `repos/${repository}/releases/tags/${tag}`;
    const findRelease = () => {
      try { return api(releaseEndpoint); }
      catch (error) {
        // Only this exact API 404 means absent; authentication/network errors stop.
        if (/\(HTTP 404\)/.test(error.stderr?.toString() ?? "")) return null;
        throw error;
      }
    };
    let release = findRelease();
    if (release) assert.ok(!release.draft && !release.prerelease, "Existing draft/prerelease requires maintainer investigation; nothing will be overwritten");
    assert.deepEqual(inspectTag("ready"), binding, "Tag changed during verification");
    assert.deepEqual(inspectPolicies("ready"), policyIds, "Ruleset identities changed");
    verifyWorkflowRun(api(runEndpoint), binding);
    assert.deepEqual(inspectBundle(), expected, "Bundle changed after signature verification");
    const assetNames = [apkName, `${apkName}.sha256`, "rc-manifest.json"];
    if (!release && publish) {
      // gh creates and uploads the new release; never edit, delete, clobber or retry a partial draft.
      publicationAttempted = true;
      gh(["release", "create", tag, "--repo", repository, "--verify-tag", "--title", `ThystTV ${version.versionName}`,
        "--notes-file", notesFile, ...assetNames.map((name) => path.join(bundle, name))]);
      created = true;
      release = api(releaseEndpoint);
    }
    if (release) {
      assert.equal(release.tag_name, tag);
      assert.ok(!release.draft && !release.prerelease, "Release is not public/stable");
      assert.deepEqual(release.assets.map((asset) => asset.name).sort(), [...assetNames].sort(), "Published asset set mismatch");
      save("release.json", release);
      const published = path.join(evidence, "published");
      fs.mkdirSync(published);
      gh(["release", "download", tag, "--repo", repository, "--dir", published, ...assetNames.flatMap((name) => ["--pattern", name])]);
      inspectFiles(published, assetNames);
      for (const name of assetNames) {
        const asset = release.assets.find((item) => item.name === name);
        assert.equal(asset.state, "uploaded");
        assert.equal(sha256(path.join(published, name)), sha256(path.join(bundle, name)), `Published bytes differ: ${name}`);
        if (asset.digest != null) assert.equal(asset.digest, `sha256:${sha256(path.join(published, name))}`);
      }
      if (publish) assert.equal(api(`repos/${repository}/releases/latest`).id, release.id, "Published release is not latest");
    }
    assert.deepEqual(inspectTag("after"), binding, "Tag changed after publication");
    assert.deepEqual(inspectPolicies("after"), policyIds, "Policy identities changed after publication");
    const result = { status: release ? (created ? "published" : "existing-release-verified") : "ready-for-publication",
      tag, ...expected, manifestSha256: binding.manifestSha256, evidenceDirectory: evidence,
      releaseUrl: release?.html_url ?? null, deviceQa: "Not performed by this command" };
    save("result.json", result);
    return result;
  } catch (error) {
    const publicationStatus = created ? "Release was created, but final verification failed. "
      : publicationAttempted ? "Publication was attempted and may have left a partial release. Inspect the release/draft before retrying. " : "";
    throw new Error(`${publicationStatus}${error.message}\nEvidence retained: ${evidence}`, { cause: error });
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { console.log(JSON.stringify(publishRelease(parseArguments(process.argv.slice(2))), null, 2)); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
