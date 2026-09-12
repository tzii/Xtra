#!/usr/bin/env bash
# Fail-closed APK inspection for ThystTV release candidates.
# Verifies package id, version name/code, and signing certificate against
# mandatory expected values, then reports non-secret key=value evidence.
set -euo pipefail

: "${APK_PATH:?APK_PATH is required}"
: "${EXPECTED_PACKAGE_ID:?EXPECTED_PACKAGE_ID is required}"
: "${EXPECTED_VERSION_NAME:?EXPECTED_VERSION_NAME is required}"
: "${EXPECTED_VERSION_CODE:?EXPECTED_VERSION_CODE is required}"
: "${EXPECTED_CERT_SHA256:?EXPECTED_CERT_SHA256 is required}"
: "${ANDROID_HOME:?ANDROID_HOME is required}"

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found: $APK_PATH" >&2
  exit 1
fi

windows_shell=false
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) windows_shell=true ;; esac

# A readable .bat is executable through cmd.exe even when MSYS reports -x false.
# Prefer the native SDK launcher on Windows; never feed a real .bat to Bash.
apkanalyzer=""
candidates=("$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" "$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer.bat")
if "$windows_shell"; then candidates=("${candidates[1]}" "${candidates[0]}"); fi
for candidate in "${candidates[@]}"; do
  if [[ -f "$candidate" ]] && { [[ -x "$candidate" ]] || { "$windows_shell" && [[ "$candidate" == *.bat && -r "$candidate" ]]; }; }; then
    apkanalyzer="$candidate"
    break
  fi
done
if [[ -z "$apkanalyzer" ]]; then
  echo "apkanalyzer not found (expected an executable apkanalyzer or native readable apkanalyzer.bat under $ANDROID_HOME/cmdline-tools/latest/bin)" >&2
  exit 1
fi

# cmd.exe expands %variables% even in quoted paths. Refuse such paths instead
# of altering their meaning. Quoting protects ordinary spaces and metacharacters;
# /d disables AutoRun and /v:off prevents delayed !variable! expansion.
validate_cmd_path() {
  case "$1" in
    *'%'*|*'"'*|*$'\r'*|*$'\n'*) echo 'Unsupported cmd.exe path: percent, quote or newline' >&2; return 1 ;;
  esac
}
run_apkanalyzer() {
  local field="$1" value tool_path apk_path
  if "$windows_shell" && [[ "$apkanalyzer" == *.bat ]]; then
    command -v cmd.exe >/dev/null 2>&1 || { echo 'cmd.exe not found' >&2; return 1; }
    command -v cygpath >/dev/null 2>&1 || { echo 'cygpath not found' >&2; return 1; }
    tool_path="$(cygpath -aw "$apkanalyzer")"
    apk_path="$(cygpath -aw "$APK_PATH")"
    validate_cmd_path "$tool_path" || return
    validate_cmd_path "$apk_path" || return
    # Pass a literal environment-variable reference as cmd's command argument.
    # MSYS otherwise escapes embedded quotes using C argv rules, which cmd does
    # not understand. Expansion happens once, inside cmd, with validated paths
    # still quoted; do not use CALL (which performs a second expansion).
    value="$(MSYS2_ARG_CONV_EXCL='*' MSYS2_ENV_CONV_EXCL='THYSTTV_ANALYZER_COMMAND' \
      THYSTTV_ANALYZER_COMMAND="\"$tool_path\" manifest $field \"$apk_path\"" \
      cmd.exe /d /v:off /c '%THYSTTV_ANALYZER_COMMAND%')" || return
  else
    value="$("$apkanalyzer" manifest "$field" "$APK_PATH")" || return
  fi
  # Command substitution removes final LF, leaving CR from native Windows output.
  # Strip that one terminator only, not arbitrary whitespace or extra output lines.
  printf '%s' "${value%$'\r'}"
}

apksigner_jar="$(
  find "$ANDROID_HOME/build-tools" -type f -path '*/lib/apksigner.jar' 2>/dev/null |
    sort -V |
    tail -n 1 || true
)"
if [[ -z "$apksigner_jar" || ! -f "$apksigner_jar" ]]; then
  echo "apksigner.jar not found (expected under $ANDROID_HOME/build-tools/*/lib)" >&2
  exit 1
fi
if ! command -v java >/dev/null 2>&1; then
  echo "java not found" >&2
  exit 1
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
structured_verifier="$script_dir/VerifyApkSigner.java"
if [[ ! -f "$structured_verifier" ]]; then
  echo "structured APK signer verifier not found: $structured_verifier" >&2
  exit 1
fi

actual_package="$(run_apkanalyzer application-id)"
actual_version_name="$(run_apkanalyzer version-name)"
actual_version_code="$(run_apkanalyzer version-code)"
actual_certificate="$(java --class-path "$apksigner_jar" --source 21 "$structured_verifier" "$APK_PATH")"
actual_certificate="$(printf '%s' "$actual_certificate" | tr -d '[:space:]:' | tr '[:upper:]' '[:lower:]')"
expected_certificate="$(printf '%s' "$EXPECTED_CERT_SHA256" | tr -d '[:space:]:' | tr '[:upper:]' '[:lower:]')"
apk_sha256="$(sha256sum "$APK_PATH" | awk '{print $1}')"

if [[ ! "$actual_certificate" =~ ^[0-9a-f]{64}$ ]]; then
  echo "certificate digest is not a 64-character lowercase hex value" >&2
  exit 1
fi
if [[ ! "$expected_certificate" =~ ^[0-9a-f]{64}$ ]]; then
  echo "expected certificate digest is not a 64-character lowercase hex value" >&2
  exit 1
fi
if [[ ! "$apk_sha256" =~ ^[0-9a-f]{64}$ ]]; then
  echo "APK digest is not a 64-character lowercase hex value" >&2
  exit 1
fi

if [[ "$actual_package" != "$EXPECTED_PACKAGE_ID" ]]; then
  echo "package id mismatch: expected $EXPECTED_PACKAGE_ID, got $actual_package" >&2
  exit 1
fi
if [[ "$actual_version_name" != "$EXPECTED_VERSION_NAME" ]]; then
  echo "version name mismatch: expected $EXPECTED_VERSION_NAME, got $actual_version_name" >&2
  exit 1
fi
if [[ "$actual_version_code" != "$EXPECTED_VERSION_CODE" ]]; then
  echo "version code mismatch: expected $EXPECTED_VERSION_CODE, got $actual_version_code" >&2
  exit 1
fi
if [[ "$actual_certificate" != "$expected_certificate" ]]; then
  echo "signing certificate mismatch: expected $expected_certificate, got $actual_certificate" >&2
  exit 1
fi

echo "package_id=$actual_package"
echo "version_name=$actual_version_name"
echo "version_code=$actual_version_code"
echo "certificate_sha256=$actual_certificate"
echo "apk_sha256=$apk_sha256"
