#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
ANDROID_JAR="${ANDROID_JAR:-$ANDROID_HOME/platforms/android-29/android.jar}"
BUILD_TOOLS="${BUILD_TOOLS:-$ANDROID_HOME/build-tools/35.0.1}"
D8_JAR="${D8_JAR:-$BUILD_TOOLS/lib/d8.jar}"
JAVA17="${JAVA17:-$(command -v java)}"
KEYTOOL17="${KEYTOOL17:-$(command -v keytool)}"

rm -rf build/gen build/classes build/dex build/apk build/test-classes
mkdir -p build/gen build/classes build/dex build/apk build/test-classes

javac -d build/test-classes \
  src/com/omri/healthscanner/SignalAnalyzer.java \
  src/com/omri/healthscanner/NutritionAssessment.java \
  src/com/omri/healthscanner/WellnessSummary.java \
  src/com/omri/healthscanner/ScanQualityGate.java \
  src/com/omri/healthscanner/FrameRegionMapper.java \
  src/com/omri/healthscanner/ScanStatusText.java \
  tests/com/omri/healthscanner/*.java
for test in SignalAnalyzerTest NutritionAssessmentTest WellnessSummaryTest ScanQualityGateTest FrameRegionMapperTest ScanStatusTextTest; do
  java -cp build/test-classes "com.omri.healthscanner.${test}"
done

aapt2 compile --dir res -o build/compiled-res.zip
aapt2 link -o build/apk/freecorder-unsigned.apk \
  --manifest AndroidManifest.xml -I "$ANDROID_JAR" \
  --java build/gen --min-sdk-version 26 --target-sdk-version 35 \
  build/compiled-res.zip
javac --release 11 -cp "$ANDROID_JAR" -d build/classes \
  $(find src build/gen -name '*.java' -print)
jar cf build/classes.jar -C build/classes .
"$JAVA17" -cp "$D8_JAR" com.android.tools.r8.D8 \
  --min-api 26 --output build/dex build/classes.jar

(
  cd build/dex
  jar uf ../apk/freecorder-unsigned.apk classes.dex
)
zipalign -f 4 build/apk/freecorder-unsigned.apk build/apk/freecorder-aligned.apk

if [[ ! -f build/freecorder-debug.keystore ]]; then
  "$KEYTOOL17" -genkeypair -keystore build/freecorder-debug.keystore \
    -storepass freecorder -keypass freecorder -alias freecorder \
    -keyalg RSA -keysize 2048 -validity 3650 \
    -dname 'CN=freecorder Development,O=Local Build,C=US' >/dev/null 2>&1
fi
apksigner sign --ks build/freecorder-debug.keystore \
  --ks-pass pass:freecorder --key-pass pass:freecorder \
  --out build/apk/freecorder.apk build/apk/freecorder-aligned.apk
apksigner verify --verbose build/apk/freecorder.apk
sha256sum build/apk/freecorder.apk
