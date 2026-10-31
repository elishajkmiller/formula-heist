#!/usr/bin/env bash
# Build a signed APK with no Android SDK and no Gradle.
# Fetches four small tools into tools/ on first run, then:
#   aapt2 compile + link -> ecj -> d8 -> append classes.dex -> apksig
set -eu

cd "$(dirname "$0")"
ROOT=$(pwd)
TOOLS=$ROOT/tools
BUILD=$ROOT/build
OUT=$ROOT/dist

ANDROID_JAR=$TOOLS/android.jar
AAPT2=$TOOLS/aapt2
R8=$TOOLS/r8.jar
APKSIG=$TOOLS/apksig.jar
ECJ=$TOOLS/ecj.jar
KEYSTORE=$ROOT/debug.keystore
KEY_ALIAS=debug
KEY_PASS=android

fetch() {
  local url=$1 dest=$2
  [ -s "$dest" ] && return 0
  echo "fetch $(basename "$dest")"
  curl -fsSL "$url" -o "$dest"
}

mkdir -p "$TOOLS" "$OUT"

fetch "https://dl.google.com/android/maven2/com/android/tools/build/aapt2/8.5.2-11315950/aapt2-8.5.2-11315950-linux.jar" "$TOOLS/aapt2-linux.jar"
if [ ! -x "$AAPT2" ]; then
  unzip -o -j "$TOOLS/aapt2-linux.jar" aapt2 -d "$TOOLS" >/dev/null
  chmod +x "$AAPT2"
fi
fetch "https://dl.google.com/android/maven2/com/android/tools/r8/8.3.37/r8-8.3.37.jar" "$R8"
fetch "https://dl.google.com/android/maven2/com/android/tools/build/apksig/8.13.2/apksig-8.13.2.jar" "$APKSIG"
fetch "https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.37.0/ecj-3.37.0.jar" "$ECJ"
if [ ! -s "$ANDROID_JAR" ]; then fetch "https://dl.google.com/android/repository/platform-35_r02.zip" "$TOOLS/platform-35.zip"; fi
if [ ! -s "$ANDROID_JAR" ]; then
  unzip -o -j "$TOOLS/platform-35.zip" "android-35/android.jar" -d "$TOOLS" >/dev/null
fi
rm -f "$TOOLS/platform-35.zip"

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
    -keypass "$KEY_PASS" -storepass "$KEY_PASS" \
    -dname "CN=Debug, OU=Dev, O=Dev, L=Home, ST=Home, C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 2>/dev/null
fi

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/obj" "$BUILD/dex"

echo "[1/6] aapt2 compile"
"$AAPT2" compile --dir res -o "$BUILD/res.zip"

echo "[2/6] aapt2 link"
"$AAPT2" link -o "$BUILD/unsigned.apk" \
  --manifest AndroidManifest.xml \
  -I "$ANDROID_JAR" \
  --java "$BUILD/gen" \
  --auto-add-overlay \
  "$BUILD/res.zip"

echo "[3/6] compile"
java -jar "$ECJ" -source 8 -target 8 \
  -classpath "$ANDROID_JAR:$APKSIG" \
  -d "$BUILD/obj" \
  $(find src -name '*.java') "$BUILD/gen/com/elishajkmiller/formulaheist/R.java" tools/Sign.java tools/Verify.java

echo "[4/6] dex"
java -cp "$R8" com.android.tools.r8.D8 \
  --min-api 26 --lib "$ANDROID_JAR" \
  --output "$BUILD/dex" \
  $(find "$BUILD/obj/com" -name '*.class')

echo "[5/6] append classes.dex"
cp "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
(cd "$BUILD/dex" && zip -q -X "$ROOT/build/aligned.apk" classes.dex)

echo "[6/6] sign"
java -cp "$APKSIG:$BUILD/obj" Sign "$KEYSTORE" "$KEY_ALIAS" "$KEY_PASS" "$KEY_PASS" \
  "$BUILD/aligned.apk" "$OUT/formula-heist.apk"
java -cp "$APKSIG:$BUILD/obj" Verify "$OUT/formula-heist.apk"

ls -la "$OUT/formula-heist.apk"
echo "done: $OUT/formula-heist.apk"
