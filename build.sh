#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="/opt/android-sdk/android-sdk"
ANDROID_JAR="$SDK_DIR/platforms/android-34/android.jar"
BUILD_TOOLS="$(ls -d "$SDK_DIR/build-tools/"* | sort -V | tail -n 1)"

AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

BUILD_DIR="$PROJECT_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"
KEYSTORE="$PROJECT_DIR/redline-release.keystore"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/res_flat" "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$DIST_DIR"

echo "==> [1/6] Compiling resources with aapt2..."
"$AAPT2" compile --dir "$PROJECT_DIR/res" -o "$BUILD_DIR/res_flat/resources.zip"

echo "==> [2/6] Linking resources, assets & manifest..."
"$AAPT2" link \
  -o "$BUILD_DIR/unaligned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$PROJECT_DIR/AndroidManifest.xml" \
  -A "$PROJECT_DIR/assets" \
  --java "$BUILD_DIR/gen" \
  --auto-add-overlay \
  "$BUILD_DIR/res_flat/resources.zip"

echo "==> [3/6] Compiling Java sources (JDK 17 -> bytecode 11)..."
mapfile -t JAVA_FILES < <(find "$PROJECT_DIR/src" "$BUILD_DIR/gen" -name '*.java')
javac \
  --release 11 \
  -cp "$ANDROID_JAR" \
  -d "$BUILD_DIR/classes" \
  "${JAVA_FILES[@]}"

echo "==> [4/6] Converting bytecode to DEX with d8..."
mapfile -t CLASS_FILES < <(find "$BUILD_DIR/classes" -name '*.class')
"$D8" \
  --min-api 26 \
  --lib "$ANDROID_JAR" \
  --output "$BUILD_DIR/dex" \
  "${CLASS_FILES[@]}"

echo "==> [5/6] Packaging & zipaligning APK..."
(cd "$BUILD_DIR/dex" && zip -q -u "$BUILD_DIR/unaligned.apk" classes.dex)
"$ZIPALIGN" -f -p 4 "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/aligned.apk"

echo "==> [6/6] Signing APK (v1 + v2 + v3)..."
if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storepass redline2026 \
    -keypass redline2026 \
    -alias redline \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Redline Keyboard, O=Redline, C=US" >/dev/null 2>&1
fi

OUT_APK="$DIST_DIR/RedlineKeyboard.apk"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias redline \
  --ks-pass pass:redline2026 \
  --key-pass pass:redline2026 \
  --out "$OUT_APK" \
  "$BUILD_DIR/aligned.apk"

"$APKSIGNER" verify --verbose "$OUT_APK"

if [[ -d /sdcard/Download ]]; then
  cp -f "$OUT_APK" /sdcard/Download/RedlineKeyboard.apk
  echo "==> Copied to /sdcard/Download/RedlineKeyboard.apk"
fi

ls -lh "$OUT_APK"
echo "==> BUILD SUCCEEDED!"
