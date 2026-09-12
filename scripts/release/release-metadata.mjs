import fs from "node:fs";
import { pathToFileURL } from "node:url";

const manifestKeys = [
  "apkFilename",
  "apkSha256",
  "certificateSha256",
  "packageId",
  "rcSha",
  "repository",
  "runId",
  "versionCode",
  "versionName",
  "workflow",
];

export function readVersionConfig(text) {
  const names = [...text.matchAll(/\bversionName\s*=\s*"([^"]+)"/g)].map((m) => m[1]);
  const codes = [...text.matchAll(/\bversionCode\s*=\s*(\d+)/g)].map((m) => Number(m[1]));
  if (names.length !== 1 || codes.length !== 1) throw new Error("expected exactly one versionName and versionCode");
  if (!/^\d+\.\d+\.\d+$/.test(names[0]) || !Number.isSafeInteger(codes[0]) || codes[0] <= 0) {
    throw new Error("invalid Android version metadata");
  }
  return { versionName: names[0], versionCode: codes[0] };
}

export function assertCompleteReleaseNotes(text) {
  if (text.trim().length < 120) throw new Error("release notes are too short");
  if (/TBD|TODO|placeholder|Release notes were not found/i.test(text)) throw new Error("release notes contain placeholder text");
}

export function parseTagMessage(text) {
  const runs = [...text.matchAll(/^RC-Workflow-Run:\s*(\d+)\s*$/gm)];
  const manifests = [...text.matchAll(/^RC-Manifest-SHA256:\s*([0-9a-f]{64})\s*$/gmi)];
  if (runs.length !== 1 || manifests.length !== 1) throw new Error("tag must bind exactly one RC run and manifest");
  return { runId: Number(runs[0][1]), manifestSha256: manifests[0][1].toLowerCase() };
}

export function createManifest(fields) {
  for (const key of manifestKeys) if (fields[key] === undefined || fields[key] === "") throw new Error(`missing manifest field: ${key}`);
  if (Object.keys(fields).some((key) => !manifestKeys.includes(key))) throw new Error("unknown manifest field");
  if (!/^[0-9a-f]{40}$/.test(fields.rcSha)) throw new Error("invalid RC SHA");
  if (!/^[0-9a-f]{64}$/.test(fields.apkSha256) || !/^[0-9a-f]{64}$/.test(fields.certificateSha256)) throw new Error("invalid SHA-256 field");
  return Object.fromEntries(Object.entries(fields).sort(([a], [b]) => a.localeCompare(b)));
}

export function verifyWorkflowRun(run, expected) {
  if (Number(run.id) !== Number(expected.runId)) throw new Error("workflow run ID mismatch");
  if (run.event !== "workflow_dispatch") throw new Error("RC run was not manually dispatched");
  if (run.conclusion !== "success") throw new Error("RC run was not successful");
  if (run.head_sha !== expected.rcSha) throw new Error("RC run head SHA mismatch");
  if (run.path !== ".github/workflows/release.yml") throw new Error("wrong workflow path");
}

export function verifyPromotion(manifest, expected) {
  const keys = Object.keys(manifest).sort();
  if (JSON.stringify(keys) !== JSON.stringify(manifestKeys)) throw new Error("manifest schema mismatch");
  for (const key of manifestKeys) {
    if (manifest[key] !== expected[key]) throw new Error(`promotion mismatch: ${key}`);
  }
}

export function verifyReleaseTagRulesets(protection, authorization, expected) {
  for (const [name, rule] of [["Protect release tags", protection], ["Authorize release tag creation", authorization]]) {
    if (rule.name !== name) throw new Error(`wrong release ruleset name: ${rule.name}`);
    if (rule.target !== "tag" || rule.enforcement !== "active") throw new Error(`${name} is not an active tag ruleset`);
    const includes = rule.conditions?.ref_name?.include ?? [];
    const excludes = rule.conditions?.ref_name?.exclude ?? [];
    if (JSON.stringify(includes) !== JSON.stringify(["refs/tags/v*"]) || excludes.length !== 0) {
      throw new Error(`${name} has the wrong tag target`);
    }
    if (!Array.isArray(rule.bypass_actors)) {
      throw new Error(`${name}: bypass_actors is unavailable or malformed; use maintainer authentication with ruleset write access`);
    }
  }

  const protectionTypes = (protection.rules ?? []).map(({ type }) => type).sort();
  if (JSON.stringify(protectionTypes) !== JSON.stringify(["deletion", "update"])) {
    throw new Error("Protect release tags must contain exactly deletion and update");
  }
  if (protection.bypass_actors.length !== 0) throw new Error("Protect release tags must not have bypass actors");

  const authorizationTypes = (authorization.rules ?? []).map(({ type }) => type);
  if (JSON.stringify(authorizationTypes) !== JSON.stringify(["creation"])) {
    throw new Error("Authorize release tag creation must contain only creation");
  }
  const actors = authorization.bypass_actors;
  if (actors.length !== 1 || Number(actors[0].actor_id) !== Number(expected.userId) ||
      actors[0].actor_type !== "User" || actors[0].bypass_mode !== "always") {
    throw new Error("release tag creation authority mismatch");
  }
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [command, ...args] = process.argv.slice(2);
  try {
    if (command === "version" && args.length === 1) {
      process.stdout.write(JSON.stringify(readVersionConfig(fs.readFileSync(args[0], "utf8"))) + "\n");
    } else if (command === "notes" && args.length === 1) {
      assertCompleteReleaseNotes(fs.readFileSync(args[0], "utf8"));
    } else if (command === "tag" && args.length === 1) {
      process.stdout.write(JSON.stringify(parseTagMessage(fs.readFileSync(args[0], "utf8"))) + "\n");
    } else if (command === "manifest" && args.length === 2) {
      fs.writeFileSync(args[1], JSON.stringify(createManifest(readJson(args[0])), null, 2) + "\n");
    } else if (command === "run" && args.length === 2) {
      verifyWorkflowRun(readJson(args[0]), readJson(args[1]));
    } else if (command === "promotion" && args.length === 2) {
      verifyPromotion(readJson(args[0]), readJson(args[1]));
    } else if (command === "policy" && args.length === 3) {
      verifyReleaseTagRulesets(readJson(args[0]), readJson(args[1]), readJson(args[2]));
    } else {
      throw new Error("invalid release-metadata command or arguments");
    }
  } catch (error) {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  }
}
