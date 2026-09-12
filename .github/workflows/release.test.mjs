import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";

const buildGradle = fs.readFileSync("app/build.gradle.kts", "utf8");
const ciWorkflow = fs.readFileSync(".github/workflows/ci.yml", "utf8");
const debugWorkflow = fs.readFileSync(".github/workflows/debug-build.yml", "utf8");
const workflow = fs.readFileSync(".github/workflows/release.yml", "utf8");
const publisher = fs.readFileSync("scripts/release/publish-release.mjs", "utf8");
const workflows = [
  ["release", workflow],
  ["CI", ciWorkflow],
  ["debug build", debugWorkflow],
];
const permittedActions = new Set([
  "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
  "actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961",
  "gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb",
  "actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a",
  "actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c",
]);

function actionSteps(workflowName, contents) {
  return contents
    .split(/(?=^ {6}- name: )/m)
    .filter((block) => block.startsWith("      - name: "))
    .map((block) => ({
      workflowName,
      stepName: block.match(/^ {6}- name:\s*([^\r\n]+)/m)?.[1] ?? "unnamed",
      uses: block.match(/^\s*uses:\s*([^\s#]+)/m)?.[1],
      block,
    }))
    .filter((step) => step.uses);
}

function actionInput(step, inputName) {
  const withSection = step.block.match(
    /^ {8}with:\s*\r?\n([\s\S]*?)(?=^ {8}\S|(?![\s\S]))/m,
  )?.[1] ?? "";
  return withSection.match(new RegExp(`^ {10}${inputName}:\\s*([^\\s#]+)`, "m"))?.[1];
}

function hasContentsReadPermission(contents) {
  for (const indent of [0, 4]) {
    const spaces = " ".repeat(indent);
    const childSpaces = " ".repeat(indent + 2);
    const blocks = contents.match(
      new RegExp(`^${spaces}permissions:\\s*\\r?\\n(?:^${childSpaces}[^\\r\\n]+\\r?\\n?)*`, "gm"),
    ) ?? [];
    if (blocks.some((block) => new RegExp(`^${childSpaces}contents:\\s*read\\s*$`, "m").test(block))) {
      return true;
    }
  }
  return false;
}

function releaseStepContaining(fragment) {
  return workflow
    .split(/(?=^ {6}- name: )/m)
    .find((step) => step.startsWith("      - name: ") && step.includes(fragment));
}

function stepScript(step) {
  const runBlock = step.match(/^ {8}run:\s*\|\s*\r?\n([\s\S]*?)(?=^ {6}- name: |(?![\s\S]))/m)?.[1] ?? "";
  return runBlock.replace(/^ {10}/gm, "");
}

function validateTemporaryRoot(root, workspace) {
  assert.equal(path.dirname(root), workspace);
  assert.match(path.basename(root), /^\.manifest-checksum-test-[A-Za-z0-9]+$/);
}

test("release signing never falls back to the debug key", () => {
  const releaseBlock = buildGradle.match(/release\s*\{[\s\S]*?\n\s{8}\}/)?.[0] ?? "";
  assert.doesNotMatch(releaseBlock, /getByName\("debug"\)/);
  assert.match(buildGradle, /releaseKeystore\.isFile/);
  for (const name of ["KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"]) {
    assert.match(buildGradle, new RegExp(name));
  }
});

test("every build and release workflow action is on the exact allowlist", () => {
  for (const [name, contents] of workflows) {
    const steps = actionSteps(name, contents);
    const uses = [...contents.matchAll(/^\s*uses:\s*([^\s#]+)/gm)].map((match) => match[1]);
    assert.ok(uses.length > 0, `${name} workflow has no actions`);
    assert.deepEqual(
      steps.map((step) => step.uses),
      uses,
      `${name} workflow has an action outside a named step block`,
    );
    for (const step of steps) {
      assert.ok(
        permittedActions.has(step.uses),
        `${name} step ${step.stepName} uses unapproved action: ${step.uses}`,
      );
    }
  }
});

test("CI and debug workflows explicitly default to read-only contents", () => {
  assert.ok(hasContentsReadPermission(ciWorkflow), "CI workflow must set contents: read");
  assert.ok(hasContentsReadPermission(debugWorkflow), "debug workflow must set contents: read");
});

test("every checkout step disables persisted credentials", () => {
  const checkouts = workflows
    .flatMap(([name, contents]) => actionSteps(name, contents))
    .filter((step) => step.uses === "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1");
  assert.equal(checkouts.length, 4);
  for (const step of checkouts) {
    assert.equal(
      actionInput(step, "persist-credentials"),
      "false",
      `${step.workflowName} step ${step.stepName} must disable persisted credentials`,
    );
  }
});

test("every Gradle setup step selects the basic cache provider", () => {
  const gradleSteps = workflows
    .flatMap(([name, contents]) => actionSteps(name, contents))
    .filter((step) => step.uses === "gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb");
  assert.equal(gradleSteps.length, 3);
  for (const step of gradleSteps) {
    assert.equal(
      actionInput(step, "cache-provider"),
      "basic",
      `${step.workflowName} step ${step.stepName} must select the basic cache provider`,
    );
  }
});

test("structured APK verification always runs after pinned Java 21 setup", () => {
  const javaSteps = workflows
    .flatMap(([name, contents]) => actionSteps(name, contents))
    .filter((step) => step.uses === "actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961");
  for (const step of javaSteps) {
    assert.equal(actionInput(step, "distribution"), "temurin", `${step.workflowName} must use Temurin`);
    assert.equal(actionInput(step, "java-version"), '"21"', `${step.workflowName} must pin Java 21`);
  }

  const ciJava = ciWorkflow.indexOf("      - name: Set up Java");
  const ciContracts = ciWorkflow.indexOf("      - name: Run repository contract tests");
  assert.ok(ciJava > -1 && ciJava < ciContracts, "CI must set up Java before structured verifier tests");
  const ciBuild = ciWorkflow.indexOf("      - name: Build debug");
  const ciStructuredIntegration = ciWorkflow.indexOf("      - name: Verify structured APK signer integration");
  assert.ok(
    ciBuild > -1 && ciBuild < ciStructuredIntegration,
    "CI must exercise structured signer verification against the built debug APK",
  );
  assert.match(
    ciWorkflow,
    /java --class-path "\$apksigner_jar" --source 21 scripts\/release\/VerifyApkSigner\.java/,
  );

  const promotion = workflow.split(/^ {2}verify_release_tag:\s*$/m)[1] ?? "";
  const promotionJava = promotion.indexOf("      - name: Set up Java");
  const promotionVerifier = promotion.indexOf("bash scripts/release/verify-apk.sh");
  assert.ok(promotionVerifier > -1, "promotion APK verifier not found");
  assert.ok(
    promotionJava > -1 && promotionJava < promotionVerifier,
    "promotion must set up Java before structured APK verification",
  );
});

test("workflow exposes the build-once promotion state machine", () => {
  assert.match(workflow, /expected_rc_sha:/);
  assert.match(workflow, /build_signed_rc:/);
  assert.match(workflow, /verify_release_tag:/);
});

test("dispatch SHA is data, not executable shell source", () => {
  const step = releaseStepContaining("Assert dispatch target");
  assert.ok(step, "dispatch target assertion step not found");

  const maliciousInput = "x'; exit 86; expected_rc_sha='";
  const expectedSha = "a".repeat(40);
  const script = stepScript(step);
  assert.doesNotMatch(script, /\$\{\{/);
  assert.match(step, /^ {8}env:\s*\r?\n/m);
  assert.match(step, /^ {10}EXPECTED_RC_SHA:\s*\$\{\{ inputs\.expected_rc_sha \}\}\s*$/m);
  assert.match(step, /^ {10}DISPATCH_REF:\s*\$\{\{ github\.ref \}\}\s*$/m);
  assert.match(step, /^ {10}DISPATCH_SHA:\s*\$\{\{ github\.sha \}\}\s*$/m);

  const result = spawnSync("bash", ["-c", script], {
    cwd: process.cwd(),
    encoding: "utf8",
    env: {
      ...process.env,
      EXPECTED_RC_SHA: maliciousInput,
      DISPATCH_REF: "refs/heads/master",
      DISPATCH_SHA: expectedSha,
    },
  });
  assert.notEqual(result.status, 0, "malformed dispatch SHA must be rejected");
  assert.notEqual(
    result.status,
    86,
    `dispatch input executed as shell source; stderr: ${result.stderr}`,
  );
});

test("default and job permissions are least privilege", () => {
  assert.match(workflow, /contents:\s*read/);
  assert.match(workflow, /actions:\s*read/);
  assert.doesNotMatch(workflow, /(?:contents|actions|administration):\s*write/);
});

test("keystore handling is guarded and cleaned up", () => {
  assert.match(workflow, /release-keystore\.jks/);
  assert.match(workflow, /if:\s*always\(\)/);
  // Test and lint must run before the keystore is decoded.
  const keystoreDecode = workflow.indexOf("base64 --decode > app/release-keystore.jks");
  const unitTest = workflow.search(/gradlew(\.bat)? test /);
  const lint = workflow.search(/gradlew(\.bat)?[^\n]*lintDebug/);
  assert.ok(keystoreDecode > -1);
  assert.ok(unitTest > -1 && unitTest < keystoreDecode, "unit tests must precede the keystore decode");
  assert.ok(lint > -1 && lint < keystoreDecode, "lint must precede the keystore decode");
});

test("RC binds run ID and manifest digest through tag metadata", () => {
  assert.match(workflow, /RC-Workflow-Run/);
  assert.match(workflow, /RC-Manifest-SHA256/);
});

test("tag workflow verifies bytes and explicitly hands publication to the maintainer", () => {
  const promotion = workflow.split(/^\s{2}verify_release_tag:\s*$/m)[1] ?? "";
  assert.ok(promotion.length > 0, "verify_release_tag job not found");
  assert.doesNotMatch(
    promotion,
    /(?:\.\/)?gradlew|assembleRelease|KEYSTORE_BASE64|KEYSTORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD/,
  );
  assert.doesNotMatch(promotion, /gh release create|gh api[^\n]*rulesets|secrets\./);
  assert.match(promotion, /has not published a release or verified the full ruleset authority/);
  assert.match(promotion, /node scripts\/release\/publish-release\.mjs v\$\{VERSION_NAME\} --publish/);
  assert.match(promotion, /github\.actor[^\n]*tzii|tzii[^\n]*github\.actor/);
  assert.match(promotion, /release-bundle\/rc-manifest\.json/);
  assert.match(publisher, /178386212/);
  assert.match(publisher, /verifyReleaseTagRulesets/);
});

test("manifest checksum is generated and verified with directory-stable paths", () => {
  const checksumStep = releaseStepContaining("rc-manifest.json >");
  assert.ok(checksumStep, "manifest checksum generation step not found");
  const workingDirectory = checksumStep.match(/^\s*working-directory:\s*([^\s#]+)\s*$/m)?.[1] ?? ".";
  const checksumCommand = checksumStep.match(/^\s*(sha256sum\s+[^\n]+rc-manifest\.json\.sha256)\s*$/m)?.[1];
  assert.ok(checksumCommand, "manifest checksum generation command not found");

  const promotion = workflow.split(/^\s{2}verify_release_tag:\s*$/m)[1] ?? "";
  const verifyCommand = promotion.match(/^\s*(\(cd release-bundle && sha256sum --check rc-manifest\.json\.sha256\))\s*$/m)?.[1];
  assert.ok(verifyCommand, "manifest checksum verification command not found");

  const workspace = process.cwd();
  const root = fs.mkdtempSync(path.join(workspace, ".manifest-checksum-test-"));
  validateTemporaryRoot(root, workspace);

  try {
    fs.mkdirSync(path.join(root, "release-bundle"));
    fs.writeFileSync(path.join(root, "release-bundle", "rc-manifest.json"), '{"version":1}\n');
    assert.match(workingDirectory, /^[A-Za-z0-9._/-]+$/);
    const temporaryDirectory = path.basename(root);
    const generated = spawnSync(
      "bash",
      ["-c", `cd ${temporaryDirectory}/${workingDirectory} && ${checksumCommand}`],
      { cwd: workspace, encoding: "utf8" },
    );
    const verified = spawnSync(
      "bash",
      ["-c", `cd ${temporaryDirectory} && ${verifyCommand}`],
      { cwd: workspace, encoding: "utf8" },
    );

    assert.equal(generated.status, 0, generated.stderr);
    assert.equal(verified.status, 0, verified.stderr);
    assert.match(verified.stdout, /rc-manifest\.json: OK/);
  } finally {
    validateTemporaryRoot(root, workspace);
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("tag verification checks the official certificate and required bundle assets", () => {
  assert.match(workflow, /official-signing-certificate\.sha256/);
  const promotion = workflow.split(/^\s{2}verify_release_tag:\s*$/m)[1] ?? "";
  assert.match(promotion, /download-artifact/);
  assert.match(promotion, /run-id:/);
  assert.match(promotion, /github-token:/);
  assert.match(promotion, /ThystTV-\$\{\{ steps.version.outputs.version_name \}\}\.apk\.sha256/);
  assert.match(promotion, /release-metadata\.mjs promotion release-bundle\/rc-manifest\.json promotion-expected\.json/);
});

test("artifacts are never overwritten", () => {
  assert.match(workflow, /overwrite:\s*false/);
  assert.doesNotMatch(workflow, /overwrite:\s*true/);
});

test("release notes cannot fall back to generated placeholder text", () => {
  assert.doesNotMatch(workflow, /Release notes were not found/);
  assert.match(publisher, /assertCompleteReleaseNotes\(notes\)/);
  assert.match(publisher, /"--notes-file", notesFile/);
});

test("PR CI runs repository contract tests and the release verifier syntax check", () => {
  assert.match(
    ciWorkflow,
    /node --test docs\/site\.test\.js \.github\/workflows\/release\.test\.mjs scripts\/release\/\*\.test\.mjs/,
  );
  assert.match(ciWorkflow, /bash -n scripts\/release\/verify-apk\.sh/);
});
