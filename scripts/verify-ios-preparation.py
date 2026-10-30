"""Static Apple-project checks; never substitutes for Xcode, signing or a physical phone."""
import argparse
import copy
import hashlib
import json
import plistlib
import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--yaml-modules", type=Path)
parser.add_argument("--report", required=True, type=Path)
args = parser.parse_args()
if args.yaml_modules:
    sys.path.insert(0, str(args.yaml_modules))
import yaml

class Loader(yaml.SafeLoader):
    pass
Loader.yaml_implicit_resolvers = copy.deepcopy(Loader.yaml_implicit_resolvers)
for key, values in Loader.yaml_implicit_resolvers.items():
    Loader.yaml_implicit_resolvers[key] = [(tag, regex) for tag, regex in values if tag != "tag:yaml.org,2002:bool"]
Loader.add_implicit_resolver("tag:yaml.org,2002:bool", re.compile(r"^(?:true|false)$"), list("tf"))

root = Path(__file__).resolve().parents[1]
project = yaml.load((root / "mobile-ios/project.yml").read_text(encoding="utf-8"), Loader=Loader)
workflow = yaml.load((root / ".github/workflows/ayudix-ios.yml").read_text(encoding="utf-8"), Loader=Loader)
assert set(workflow["on"]) == {"workflow_dispatch"}, "Only explicitly started builds are allowed"
assert workflow["permissions"] == {"contents": "read"}
assert workflow["on"]["workflow_dispatch"]["inputs"]["build_scope"]["options"] == ["all", "device-only"]
job = workflow["jobs"]["build-and-check"]
assert job["runs-on"] == "macos-26" and job["timeout-minutes"] == 45
assert "private == false" in job["if"]
assert job["env"]["DEVELOPER_DIR"] == "/Applications/Xcode_26.4.app/Contents/Developer"
assert all("runner." not in str(value) for value in job["env"].values()), "Runner context is not available in job-level env"
for step in job["steps"]:
    if "uses" in step:
        assert re.fullmatch(r"actions/[a-z-]+@[a-f0-9]{40}", step["uses"]), "Actions must be pinned to reviewed commits"
assert "secrets." not in json.dumps(workflow)
assert "build-ios.sh simulator" in json.dumps(workflow) and "build-ios.sh device" in json.dumps(workflow)
assert project["targets"]["Ayudix"]["settings"]["base"]["INFOPLIST_FILE"] == "Resources/Info.plist"
assert project["targets"]["Ayudix"]["scheme"]["testTargets"] == ["AyudixUITests"]
assert project["targets"]["AyudixUITests"]["dependencies"] == [{"target": "Ayudix"}]
for target in project["targets"].values():
    for source in target["sources"]:
        assert (root / "mobile-ios" / source["path"]).exists(), f"Missing Apple source: {source}"

info = plistlib.loads((root / "mobile-ios/Resources/Info.plist").read_bytes())
privacy = plistlib.loads((root / "mobile-ios/Resources/PrivacyInfo.xcprivacy").read_bytes())
assert info["NSCameraUsageDescription"] and info["NSLocalNetworkUsageDescription"]
assert info.get("CADisableMinimumFrameDurationOnPhone") is True, "Compose requires ProMotion support at runtime"
assert info["NSAppTransportSecurity"] == {"NSAllowsLocalNetworking": True}
assert "NSMicrophoneUsageDescription" not in info, "Microphone is not implemented in this stage"
assert privacy["NSPrivacyTracking"] is False and privacy["NSPrivacyCollectedDataTypes"] == []
assert "version: 2" in (root / "src/renderer/src/screens/SecurityScreen.tsx").read_text(encoding="utf-8")
assert "payload.version == 2" in (root / "mobile-shared/core/src/commonMain/kotlin/pe/ayudix/shared/NetworkPolicy.kt").read_text(encoding="utf-8")
store = (root / "mobile-ios/Sources/AppleConnectionStore.swift").read_text(encoding="utf-8")
assert "kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly" in store
assert "kSecAttrSynchronizable as String: false" in store
assert "UserDefaults" not in (root / "mobile-ios/Sources/AyudixApp.swift").read_text(encoding="utf-8")
for relative in ["mobile-shared/gradlew", "scripts/ios/build-ios.sh"]:
    assert b"\r\n" not in (root / relative).read_bytes(), f"Unix line endings required: {relative}"
jar = root / "mobile-shared/gradle/wrapper/gradle-wrapper.jar"
assert hashlib.sha256(jar.read_bytes()).digest() == hashlib.sha256((root / "mobile-android/gradle/wrapper/gradle-wrapper.jar").read_bytes()).digest()
assert "distributionSha256Sum=" in (root / "mobile-shared/gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")

tests = failures = errors = skipped = 0
for module in ["core", "ui"]:
    reports = list((root / "mobile-shared" / module / "build/test-results/jvmTest").glob("TEST-*.xml"))
    assert reports, f"No executed test reports for {module}"
    for report in reports:
        suite = ET.parse(report).getroot()
        tests += int(suite.attrib["tests"])
        failures += int(suite.attrib["failures"])
        errors += int(suite.attrib["errors"])
        skipped += int(suite.attrib.get("skipped", 0))
assert tests >= 22 and failures == errors == skipped == 0
report = {"ok": True, "sharedJvmTests": tests, "failures": failures, "errors": errors, "skipped": skipped,
          "projectAndWorkflowParsed": True, "explicitPublicMacRunnerOnly": True, "appleCredentialsIncluded": False,
          "xcodeBuildExecuted": False, "physicalIPhoneTest": False, "installableIpaCreated": False}
args.report.parent.mkdir(parents=True, exist_ok=True)
args.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report))
