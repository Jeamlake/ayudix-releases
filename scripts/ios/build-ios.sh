#!/bin/bash
set -euo pipefail

# Xcode lives only on a Mac. Windows uses test-mobile-shared.ps1 for the common code.
if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "La compilación iOS requiere macOS con Xcode. No se instaló un SDK Apple en Windows." >&2
  exit 2
fi
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODE="${1:-simulator}"
if [[ "$MODE" != "simulator" && "$MODE" != "device" ]]; then echo "Uso: build-ios.sh simulator|device" >&2; exit 2; fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/Library/Caches/Ayudix/Gradle}"
export KONAN_DATA_DIR="${KONAN_DATA_DIR:-$HOME/Library/Caches/Ayudix/KotlinNative}"
LOGS="${AYUDIX_IOS_LOGS:-$ROOT/qa/ios-preparation}"
DERIVED="${AYUDIX_IOS_DERIVED:-$HOME/Library/Caches/Ayudix/Xcode/$MODE}"
mkdir -p "$LOGS" "$DERIVED"
if ! command -v xcodegen >/dev/null; then echo "Instala XcodeGen en la Mac y vuelve a ejecutar. No se descargan instaladores por suposición." >&2; exit 2; fi
if ! xcodebuild -version | head -n1 | grep -q '^Xcode 26\.4'; then echo "Esta preparación fija Xcode 26.4.x, compatible con Kotlin 2.4.10. Selecciónalo con DEVELOPER_DIR." >&2; exit 2; fi
xcodebuild -version > "$LOGS/xcode-version.txt"
xcodegen --version > "$LOGS/xcodegen-version.txt"
xcodegen generate --spec "$ROOT/mobile-ios/project.yml" --project "$ROOT/mobile-ios"
if [[ "$MODE" == "simulator" ]]; then
  if [[ -z "${AYUDIX_SIMULATOR_UDID:-}" ]]; then echo "Define AYUDIX_SIMULATOR_UDID con un simulador disponible de iPhone 17." >&2; exit 2; fi
  RESULT="$LOGS/ios-ui-tests-$(date -u +%Y%m%dT%H%M%SZ)-$$.xcresult"
  xcodebuild test -project "$ROOT/mobile-ios/Ayudix.xcodeproj" -scheme Ayudix \
    -configuration Debug -sdk iphonesimulator -destination "platform=iOS Simulator,id=$AYUDIX_SIMULATOR_UDID" \
    -derivedDataPath "$DERIVED" -resultBundlePath "$RESULT" \
    CODE_SIGNING_ALLOWED=NO | tee "$LOGS/simulator-build.log"
else
  xcodebuild build -project "$ROOT/mobile-ios/Ayudix.xcodeproj" -scheme Ayudix \
    -configuration Debug -sdk iphoneos -destination 'generic/platform=iOS' \
    -derivedDataPath "$DERIVED" CODE_SIGNING_ALLOWED=NO | tee "$LOGS/device-build.log"
  APP="$DERIVED/Build/Products/Debug-iphoneos/Ayudix.app"
  test -f "$APP/Info.plist"
  # This package has no certificate or provisioning profile. Never call it ready to install.
  OUT="$ROOT/release/ios-preparation"
  STAGE="$(mktemp -d "${TMPDIR:-/tmp}/ayudix-ipa.XXXXXX")"
  trap 'echo "Carpeta temporal del paquete: $STAGE"' EXIT
  mkdir -p "$OUT" "$STAGE/Payload"
  /usr/bin/ditto "$APP" "$STAGE/Payload/Ayudix.app"
  /usr/bin/ditto -c -k --keepParent "$STAGE/Payload" "$OUT/Ayudix-iOS-0.1.0-preparation-UNSIGNED.ipa"
  shasum -a 256 "$OUT/Ayudix-iOS-0.1.0-preparation-UNSIGNED.ipa" > "$OUT/SHA256SUMS.txt"
  printf '%s\n' 'IPA sin firma: requiere una ruta de firma elegida por el propietario antes de instalar. No contiene cuentas de Apple ni credenciales del HONOR.' > "$OUT/LEEME.txt"
fi
