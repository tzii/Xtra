import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { publishRelease, parseArguments } from "./publish-release.mjs";
import { createManifest } from "./release-metadata.mjs";

const digest = (bytes) => createHash("sha256").update(bytes).digest("hex");
const cert = fs.readFileSync("scripts/release/official-signing-certificate.sha256", "utf8").trim();
const sha = "a".repeat(40);
const apkName = "ThystTV-1.3.0.apk";
const apk = Buffer.from("fixture bytes: the real Android signature verifier is covered separately");
const fields = { repository: "tzii/ThystTV", workflow: "release.yml", runId: 123,
  rcSha: sha, versionName: "1.3.0", versionCode: 12, packageId: "com.tzii.thysttv",
  apkFilename: apkName, apkSha256: digest(apk), certificateSha256: cert };
const manifest = JSON.stringify(createManifest(fields), null, 2) + "\n";
const fileBytes = {
  [apkName]: apk, [`${apkName}.sha256`]: `${digest(apk)}  ${apkName}\n`,
  "rc-manifest.json": manifest, "rc-manifest.json.sha256": `${digest(manifest)}  rc-manifest.json\n`,
};
const assetNames = [apkName, `${apkName}.sha256`, "rc-manifest.json"];

function scenario(t, options = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "thysttv-publish-test-"));
  t.after(() => {
    assert.equal(path.dirname(root), os.tmpdir());
    assert.match(path.basename(root), /^thysttv-publish-test-[A-Za-z0-9]+$/);
    fs.rmSync(root, { recursive: true });
  });
  const calls = [];
  let tagReads = 0;
  const common = { target: "tag", enforcement: "active",
    conditions: { ref_name: { include: ["refs/tags/v*"], exclude: [] } } };
  const protection = { ...common, id: 1, name: "Protect release tags", bypass_actors: [], rules: [{type:"deletion"},{type:"update"}] };
  const authorization = { ...common, id: 2, name: "Authorize release tag creation",
    bypass_actors: [{actor_id:178386212,actor_type:"User",bypass_mode:"always"}], rules: [{type:"creation"}] };
  if (options.redactedProtection) delete protection.bypass_actors;
  if (options.redactedAuthorization) delete authorization.bypass_actors;
  if (options.extraActor) authorization.bypass_actors.push({actor_id:1,actor_type:"User",bypass_mode:"always"});
  const publicRelease = { id: 99, tag_name: "v1.3.0", draft: false, prerelease: false,
    html_url: "https://github.com/tzii/ThystTV/releases/tag/v1.3.0",
    assets: assetNames.map(name => ({name, state:"uploaded", digest:`sha256:${digest(fileBytes[name])}`})) };
  let release = options.existing ? structuredClone(publicRelease) : null;
  if (options.draft) release = {...publicRelease, draft:true};
  if (options.missingAsset) release = {...publicRelease, assets:publicRelease.assets.slice(1)};
  const run = (command, args) => {
    calls.push([command, ...args]);
    if (command === "git") {
      assert.equal(args[0], "show");
      assert.ok(args[1].startsWith(`${sha}:`));
      if (args[1].endsWith("app/build.gradle.kts")) return 'versionName = "1.3.0"\nversionCode = 12';
      if (args[1].endsWith("official-signing-certificate.sha256")) return cert;
      return "# ThystTV 1.3.0\n\n" + "Verified player, updater, Stats and compatibility improvements. ".repeat(4);
    }
    if (command === "bash") return `package_id=com.tzii.thysttv\nversion_name=1.3.0\nversion_code=12\ncertificate_sha256=${options.badCertificate ? "0".repeat(64) : cert}\napk_sha256=${digest(apk)}\n`;
    assert.equal(command, "gh");
    if (args[0] === "api") {
      const endpoint = args.at(-1);
      if (endpoint === "user") return JSON.stringify({login:"tzii",id:options.wrongActor ? 1 : 178386212,type:"User"});
      if (endpoint.endsWith("/git/ref/tags/v1.3.0")) {
        tagReads++;
        return JSON.stringify({object:{type:options.lightweight ? "commit" : "tag",sha: options.movingTag && tagReads > 1 ? "b".repeat(40) : "c".repeat(40)}});
      }
      if (endpoint.includes("/git/tags/")) return JSON.stringify({sha:endpoint.split("/").at(-1),tag:"v1.3.0",object:{type:"commit",sha},message:`RC-Workflow-Run: 123\nRC-Manifest-SHA256: ${digest(manifest)}`});
      if (endpoint.endsWith("/rulesets")) return JSON.stringify([[protection,authorization], ...(options.duplicatePolicy ? [[authorization]] : [])]);
      if (endpoint.endsWith("/rulesets/1")) return JSON.stringify(protection);
      if (endpoint.endsWith("/rulesets/2")) return JSON.stringify(authorization);
      if (endpoint.endsWith("/actions/runs/123")) return JSON.stringify({id:123,event:"workflow_dispatch",conclusion:options.badRun ? "failure" : "success",head_sha:sha,path:".github/workflows/release.yml"});
      if (endpoint.endsWith("/releases/latest")) return JSON.stringify(publicRelease);
      if (endpoint.endsWith("/releases/tags/v1.3.0")) {
        if (release) return JSON.stringify(release);
        const error = new Error(options.apiFailure ? "Network unavailable" : "Not found");
        error.stderr = options.apiFailure ? "Connection reset" : "gh: Not Found (HTTP 404)";
        throw error;
      }
      assert.fail(`Unexpected API request ${endpoint}`);
    }
    if (args[0] === "run" && args[1] === "download") {
      assert.equal(args[args.indexOf("--name") + 1], `${sha}-123`);
      const directory = args[args.indexOf("--dir") + 1];
      for (const [name, bytes] of Object.entries(fileBytes)) fs.writeFileSync(path.join(directory,name),bytes);
      if (options.tamperedBundle) fs.appendFileSync(path.join(directory,apkName),"tampered");
      return "";
    }
    if (args[0] === "release" && args[1] === "create") {
      assert.equal(args[2],"v1.3.0");
      assert.equal(args[args.indexOf("--repo")+1],"tzii/ThystTV");
      assert.ok(args.includes("--verify-tag"));
      assert.equal(args.filter(arg=>arg.endsWith(".apk")).length,1);
      assert.equal(args.filter(arg=>arg.endsWith("rc-manifest.json")).length,1);
      for (const file of args.slice(-3)) assert.equal(digest(fs.readFileSync(file)),digest(fileBytes[path.basename(file)]));
      release = structuredClone(publicRelease);
      if (options.partialUpload) throw new Error("Asset upload connection reset");
      return release.html_url;
    }
    if (args[0] === "release" && args[1] === "download") {
      const directory = args[args.indexOf("--dir")+1];
      for (const name of assetNames) fs.writeFileSync(path.join(directory,name),fileBytes[name]);
      if (options.tamperedPublic) fs.appendFileSync(path.join(directory,apkName),"tampered");
      return "";
    }
    assert.fail(`Unexpected command ${JSON.stringify(args)}`);
  };
  return { calls, run: (publish = true) => publishRelease({tag:"v1.3.0",publish,directory:path.join(root,"evidence")},run) };
}

test("verification is the default and unsafe/ambiguous arguments are rejected", () => {
  assert.deepEqual(parseArguments(["v1.3.1"]),{tag:"v1.3.1",publish:false,directory:undefined});
  for (const args of [[],["../tag"],["v1.3.1;echo x"],["v1.3.1","--publish","--publish"],["v1.3.1","--evidence-dir"],["v1.3.1","--force"]]) assert.throws(()=>parseArguments(args));
});

test("read-only verification never creates an absent release", t => {
  const s = scenario(t);
  assert.equal(s.run(false).status,"ready-for-publication");
  assert.ok(!s.calls.some(call=>call[1]==="release"&&call[2]==="create"));
});

test("publish promotes exact files once and verifies public bytes and policies", t => {
  const s = scenario(t);
  const result = s.run();
  assert.equal(result.status,"published");
  assert.equal(result.apkSha256,digest(apk));
  assert.equal(s.calls.filter(call=>call[1]==="release"&&call[2]==="create").length,1);
  assert.ok(s.calls.filter(call=>call.at(-1).endsWith("/rulesets/2")).length >= 3);
});

test("an existing matching release is verified without modification", t => {
  const s = scenario(t,{existing:true});
  assert.equal(s.run().status,"existing-release-verified");
  assert.ok(!s.calls.some(call=>call[1]==="release"&&["create","edit","upload","delete"].includes(call[2])));
});

test("an existing evidence directory is never reused or overwritten", t => {
  const s = scenario(t, {existing:true});
  const result = s.run(false);
  const record = fs.readFileSync(path.join(result.evidenceDirectory, "result.json"), "utf8");
  const callsBefore = s.calls.length;
  assert.throws(() => s.run(), /EEXIST/);
  assert.equal(s.calls.length, callsBefore);
  assert.equal(fs.readFileSync(path.join(result.evidenceDirectory, "result.json"), "utf8"), record);
});

test("a partial upload reports uncertain publication without retrying", t => {
  const s = scenario(t, {partialUpload:true});
  assert.throws(() => s.run(), /may have left a partial release.*Inspect the release\/draft before retrying/);
  assert.equal(s.calls.filter(call => call[1] === "release" && call[2] === "create").length, 1);
});

for (const [option, error] of Object.entries({
  redactedProtection:/bypass_actors/,redactedAuthorization:/bypass_actors/,extraActor:/authority mismatch/,
  wrongActor:/maintainer identity/,lightweight:/annotated/,badRun:/not successful/,
  duplicatePolicy:/exactly one/,tamperedBundle:/Checksum mismatch/,badCertificate:/verifier output/,
  movingTag:/Tag changed/,draft:/draft\/prerelease/,missingAsset:/asset set mismatch/,apiFailure:/Network unavailable/,
})) test(`${option} prevents publication`, t => {
  const s = scenario(t,{[option]:true});
  assert.throws(()=>s.run(),error);
  assert.ok(!s.calls.some(call=>call[1]==="release"&&call[2]==="create"));
});

test("post-publication byte mismatch is reported without overwrite or retry", t => {
  const s = scenario(t,{tamperedPublic:true});
  assert.throws(()=>s.run(),/Release was created, but final verification failed.*Published bytes differ/);
  assert.equal(s.calls.filter(call=>call[1]==="release"&&call[2]==="create").length,1);
  assert.ok(!s.calls.some(call=>call.includes("--clobber")||call.includes("--force")));
});
