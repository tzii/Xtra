#!/usr/bin/env bash
# Runs the same production-core cases as UpdatePolicyTest, without an Android SDK.
# This does NOT compile Android adapters or replace Gradle/Robolectric/device checks.
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
kotlinc_bin="${KOTLINC_BIN:-$(command -v kotlinc || true)}"
[[ -n "$kotlinc_bin" ]] || { echo 'kotlinc is required' >&2; exit 1; }
kotlin_lib="$(cd -- "$(dirname -- "$kotlinc_bin")/../lib" && pwd -P)"
coroutines="${COROUTINES_JAR:-$kotlin_lib/kotlinx-coroutines-core-jvm.jar}"
[[ -f "$coroutines" ]] || { echo 'Set COROUTINES_JAR to a Kotlin-compatible kotlinx-coroutines-core JVM jar' >&2; exit 1; }
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
main="$root/app/src/main/java/com/github/andreyasadchy/xtra/util/update"
tests="$root/app/src/test/java/com/github/andreyasadchy/xtra/util/update"
"$kotlinc_bin" -version
"$kotlinc_bin" -classpath "$coroutines" -include-runtime -d "$tmp/update-core.jar" \
  "$main/UpdateState.kt" "$main/UpdateBodyReader.kt" "$main/InstallCallbackPolicy.kt" \
  "$main/UpdateWindowPolicy.kt" "$main/UpdateCheckResult.kt" \
  "$tests/UpdatePolicyCases.kt" "$root/scripts/validation/UpdatePolicyMain.kt"
java -cp "$tmp/update-core.jar:$coroutines" com.github.andreyasadchy.xtra.util.update.UpdatePolicyMainKt
