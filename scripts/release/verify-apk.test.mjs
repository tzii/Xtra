import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";

const expectedCertificate = "ab".repeat(32);
const verifierSource = fs.readFileSync(new URL("verify-apk.sh", import.meta.url), "utf8").replaceAll("\r\n", "\n");
const gitBash = process.platform === "win32"
  ? [process.env.THYSTTV_GIT_BASH, path.join(process.env.ProgramFiles || "C:\\Program Files", "Git", "bin", "bash.exe"),
      path.join(process.env.LOCALAPPDATA || "", "Programs", "Git", "bin", "bash.exe")].find(value => value && fs.existsSync(value))
  : null;
const bash = gitBash || "bash";
const platform = spawnSync(bash, ["-c", "uname -s"], { encoding: "utf8", timeout: 10_000 });
assert.equal(platform.status, 0, platform.stderr || String(platform.error));
const nativeWindows = /^(?:MINGW|MSYS|CYGWIN)/.test(platform.stdout.trim());
if (process.platform === "win32") assert.ok(nativeWindows, "Use Git Bash, not WSL; set THYSTTV_GIT_BASH when installed elsewhere");
const shellQuote = text => `'${text.replaceAll("'", "'\\''")}'`;

function executable(file, contents, mode = 0o755) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, contents, { mode }); fs.chmodSync(file, mode);
}
function safeRoot(root, workspace) {
  assert.equal(path.dirname(root), workspace);
  assert.match(path.basename(root), /^\.verify-apk-test-[A-Za-z0-9]+$/);
}
function runVerifier({
  structuredSignerOutput = expectedCertificate, structuredSignerStderr = "", structuredSignerExit = 0,
  pinnedCertificate = expectedCertificate, toolExtension = "", toolMode = 0o755, omitTool = false,
  packageId = "com.tzii.thysttv", versionName = "1.2.1", versionCode = "11", analyzerExit = 0,
  crlf = false, sdkName = "android-sdk", apkName = "candidate.apk",
} = {}) {
  const workspace = process.cwd();
  const root = fs.mkdtempSync(path.join(workspace, ".verify-apk-test-"));
  safeRoot(root, workspace);
  try {
    const home = path.join(root, sdkName);
    fs.writeFileSync(path.join(root, "verify-apk.sh"), verifierSource);
    fs.writeFileSync(path.join(root, apkName), "test APK bytes");
    fs.writeFileSync(path.join(root, "structured-signer-output.txt"), `${structuredSignerOutput}\n`);
    fs.writeFileSync(path.join(root, "structured-signer-stderr.txt"), `${structuredSignerStderr}\n`);
    fs.writeFileSync(path.join(root, "VerifyApkSigner.java"), "// fixture intercepted by fake java\n");
    const batch = nativeWindows && toolExtension === ".bat";
    const analyzer = batch ? [
      "@echo off", "setlocal DisableDelayedExpansion", `if not exist "%~3" exit /b 7`,
      `if not "${analyzerExit}"=="0" exit /b ${analyzerExit}`,
      'if "%~2"=="application-id" goto app', 'if "%~2"=="version-name" goto name',
      'if "%~2"=="version-code" goto code', "exit /b 2", ":app", `echo ${packageId}`, "exit /b 0",
      ":name", `echo ${versionName}`, "exit /b 0", ":code", `echo ${versionCode}`, "exit /b 0", "",
    ].join("\r\n") : `#!/usr/bin/env bash\n[[ -f "$3" ]] || exit 7\n[[ ${analyzerExit} == 0 ]] || exit ${analyzerExit}\ncase "$2" in\n  application-id) printf '%s${crlf ? "\\r\\n" : "\\n"}' ${shellQuote(packageId)} ;;\n  version-name) printf '%s${crlf ? "\\r\\n" : "\\n"}' ${shellQuote(versionName)} ;;\n  version-code) printf '%s${crlf ? "\\r\\n" : "\\n"}' ${shellQuote(versionCode)} ;;\n  *) exit 2 ;;\nesac\n`;
    if (!omitTool) executable(path.join(home, "cmdline-tools", "latest", "bin", `apkanalyzer${toolExtension}`), analyzer, toolMode);
    fs.mkdirSync(path.join(home, "build-tools", "35.0.0", "lib"), { recursive: true });
    fs.writeFileSync(path.join(home, "build-tools", "35.0.0", "lib", "apksigner.jar"), "fixture jar");
    executable(path.join(root, "bin", "java"), `#!/usr/bin/env bash\ncat structured-signer-output.txt\ncat structured-signer-stderr.txt >&2\nexit ${structuredSignerExit}\n`);
    const command = [
      `cd ${shellQuote(path.basename(root))}`, `export ANDROID_HOME=${shellQuote(`./${sdkName}`)}`,
      `export APK_PATH=${shellQuote(`./${apkName}`)}`, "export EXPECTED_PACKAGE_ID=com.tzii.thysttv",
      "export EXPECTED_VERSION_NAME=1.2.1", "export EXPECTED_VERSION_CODE=11",
      `export EXPECTED_CERT_SHA256=${shellQuote(pinnedCertificate)}`, 'export PATH="./bin:$PATH"', "bash verify-apk.sh",
    ].join("\n");
    return spawnSync(bash, ["-c", command], { cwd: workspace, encoding: "utf8", env: process.env, timeout: 15_000 });
  } finally { safeRoot(root, workspace); fs.rmSync(root, { recursive: true, force: true }); }
}
function success(options) {
  const result = runVerifier(options); assert.equal(result.status, 0, result.stderr || String(result.error));
  assert.match(result.stdout, new RegExp(`certificate_sha256=${expectedCertificate}`));
  assert.match(result.stdout, /^apk_sha256=[a-f0-9]{64}$/m);
}
function rejection(options, error) {
  const result = runVerifier(options); assert.notEqual(result.status, 0, result.stdout);
  if (error) assert.match(result.stderr, error);
}

test("accepts and normalizes exactly one structured signer digest", () => success({ structuredSignerOutput: expectedCertificate.toUpperCase().match(/../g).join(":") }));
test("rejects a valid signer digest that does not match the pin", () => rejection({ structuredSignerOutput: "cd".repeat(32) }, /signing certificate mismatch/));
for (const [name, options, error] of [
  ["zero structured signer digests", { structuredSignerOutput: "" }, /certificate digest is not a 64-character/],
  ["multiple structured signer digests", { structuredSignerOutput: `${expectedCertificate}\n${"cd".repeat(32)}` }, /certificate digest is not a 64-character/],
  ["a malformed structured signer digest", { structuredSignerOutput: "not-a-sha256-digest" }, /certificate digest is not a 64-character/],
  ["a malformed pinned signer digest", { pinnedCertificate: "not-a-pin" }, /expected certificate digest is not a 64-character/],
  ["a structured verifier failure", { structuredSignerStderr: "structured APK signer verification failed: simulated failure", structuredSignerExit: 23 }, /structured APK signer verification failed/],
  ["wrong package", { packageId: "other.package" }, /package id mismatch/],
  ["wrong version name", { versionName: "1.3.0" }, /version name mismatch/],
  ["wrong version code", { versionCode: "12" }, /version code mismatch/],
  ["extra package output", { packageId: "com.tzii.thysttv\nextra" }, /package id mismatch/],
  ["leading package whitespace", { packageId: " com.tzii.thysttv" }, /package id mismatch/],
  ["missing apkanalyzer", { omitTool: true }, /apkanalyzer not found/],
]) test(`rejects ${name}`, () => rejection(options, error));
test("propagates apkanalyzer failure rather than verifying empty output", () => {
  const result = runVerifier({ analyzerExit: 19 }); assert.equal(result.status, 19, result.stderr);
  assert.doesNotMatch(result.stdout, /apk_sha256=/);
});
test("normalizes a single CRLF metadata terminator", () => success({ crlf: true }));
test("supports SDK and APK paths containing spaces", () => success({ sdkName: "android sdk", apkName: "candidate release.apk" }));
test("POSIX executable with .bat suffix is only a launcher fixture, not Windows evidence", { skip: nativeWindows }, () => success({ toolExtension: ".bat" }));
test("nonexecutable POSIX apkanalyzer is not silently interpreted", { skip: nativeWindows }, () => rejection({ toolMode: 0o644 }, /apkanalyzer not found/));

// These tests intentionally SKIP on Linux. Only a native Git Bash/cmd.exe run can clear them.
test("native Windows readable .bat discovery and CRLF output", { skip: !nativeWindows }, () => success({ toolExtension: ".bat", toolMode: 0o644 }));
test("native Windows .bat supports paths with spaces and ampersands", { skip: !nativeWindows }, () => success({ toolExtension: ".bat", sdkName: "android sdk & tools", apkName: "candidate & signed.apk" }));
test("native Windows .bat preserves exclamation marks without delayed expansion", { skip: !nativeWindows }, () => success({ toolExtension: ".bat", sdkName: "sdk!literal", apkName: "candidate!.apk" }));
test("native Windows .bat fails closed on percent expansion in paths", { skip: !nativeWindows }, () => rejection({ toolExtension: ".bat", sdkName: "sdk%PATH%" }, /Unsupported cmd.exe path/));
test("native Windows .bat propagates nonzero exit", { skip: !nativeWindows }, () => {
  const result = runVerifier({ toolExtension: ".bat", analyzerExit: 19 }); assert.equal(result.status, 19, result.stderr);
});
