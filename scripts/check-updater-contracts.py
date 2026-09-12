#!/usr/bin/env python3
"""Static source contracts only. Does not compile Kotlin, merge manifests or run Android."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
CODE = MAIN / "java/com/github/andreyasadchy/xtra"
NS = "{http://schemas.android.com/apk/res/android}"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)
    print(f"PASS {message}")


def text(path: str) -> str:
    return (CODE / path).read_text(encoding="utf-8")


def main() -> None:
    xmls = sorted((ROOT / "app/src").rglob("*.xml"))
    for path in xmls:
        ET.parse(path)
    require(bool(xmls), f"{len(xmls)} source XML files parse")
    manifest = ET.parse(MAIN / "AndroidManifest.xml").getroot()
    receivers = [node for node in manifest.findall("./application/receiver")
                 if node.get(NS + "name") == ".util.update.UpdateInstallReceiver"]
    require(len(receivers) == 1 and receivers[0].get(NS + "exported") == "false"
            and not receivers[0].findall("intent-filter"), "installer receiver is private and has no implicit filter")
    queries = {node.get(NS + "name") for node in manifest.findall("./queries/intent/action")}
    require({"android.content.pm.action.CONFIRM_INSTALL", "android.content.pm.action.CONFIRM_PERMISSIONS"} <= queries,
            "visibility queries include current and legacy system confirmation")
    main_activity = text("ui/main/MainActivity.kt")
    require("INTENT_INSTALL_UPDATE" not in main_activity and "Intent.EXTRA_INTENT" not in main_activity,
            "exported MainActivity no longer forwards installer nested intents")
    for path in ("ui/main/MainViewModel.kt", "ui/settings/SettingsViewModel.kt"):
        source = text(path)
        require("val updater: UpdateManager" in source and "updater.start(UpdateRequest" in source
                and "UpdateCheckMailbox<UpdateInfo?>" in source, f"{path} delegates to retained shared updater")
        require(not any(name in source for name in ("closeUpdateDialog", "updateProgress", "updateJob", "PackageInstaller")),
                f"{path} has no duplicated installer or lossy completion stream")
    for path in ("ui/main/MainActivity.kt", "ui/settings/SettingsActivity.kt"):
        source = text(path)
        require(source.count("UpdateDialogController(this,") == 1
                and "DialogUpdateDownloadBinding" not in source, f"{path} has one activity-owned updater renderer")
    receiver = text("util/update/UpdateInstallReceiver.kt")
    require("startActivity(" not in receiver and "@AndroidEntryPoint" in receiver,
            "private receiver stores callback state rather than launching background UI")
    intents = text("util/update/UpdateInstallIntents.kt")
    require("PendingIntent.getBroadcast(" in intents and "PendingIntent.getActivity(" not in intents
            and "PendingIntent.FLAG_MUTABLE" in intents and "Intent(context, UpdateInstallReceiver::class.java)" in intents,
            "callback uses explicit mutable broadcast capability")
    require("return Intent(candidate.action).apply" in intents and "putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)" in intents,
            "validated installer intent is rebuilt rather than forwarded")
    prompt = ET.parse(MAIN / "res/layout/dialog_update_available.xml").getroot()
    content = [node for node in prompt.iter() if node.get(NS + "id") == "@+id/updateSheetContent"]
    require(len(content) == 1 and content[0].tag.endswith(".UpdateSheetLayout")
            and "binding.updateSheetContent.maxAvailableHeight" in text("ui/common/UpdateAvailableDialog.kt"),
            "height constraint targets typed inner sheet, not outer MaterialCardView")
    declarations = {node.get("name") for path in (MAIN / "res/values").glob("*.xml")
                    for node in ET.parse(path).getroot() if node.tag == "string"}
    controller = text("ui/common/UpdateDialogController.kt")
    references = set(re.findall(r"(?<!android\.)R\.string\.([A-Za-z0-9_]+)", controller))
    require(references <= declarations, "every updater controller string resolves in default resources")
    require("Lifecycle.State.RESUMED" in controller and "takeConfirmation(id)" in controller,
            "automatic installer handoff is observed from a resumed owner")
    require(b"\r" not in (ROOT / "gradlew").read_bytes(), "Unix Gradle launcher has LF endings")
    print("Static contracts passed; Android build, manifest merger, lint, OS security and device QA remain separate gates.")


if __name__ == "__main__":
    main()
