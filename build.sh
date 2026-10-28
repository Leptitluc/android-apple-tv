#!/usr/bin/env bash
# Construit AccueilTV.apk sans Gradle : aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Besoin : un JDK 17+ et le SDK Android (build-tools 34+, platform android-34).
#   macOS : brew install --cask temurin android-commandlinetools android-platform-tools
#           sdkmanager "build-tools;34.0.0" "platforms;android-34"
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  for d in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" /opt/homebrew/share/android-commandlinetools \
           /usr/local/share/android-commandlinetools /usr/lib/android-sdk; do
    [ -d "$d/build-tools" ] && { SDK="$d"; break; }
  done
fi
[ -n "$SDK" ] && [ -d "$SDK/build-tools" ] || { echo "SDK Android introuvable. Installe-le et exporte ANDROID_HOME (voir README)."; exit 1; }
# build-tools : dossier versionné (34.0.0/) ou outils à plat
if ls "$SDK/build-tools"/*/aapt2 >/dev/null 2>&1; then
  BT="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)"; BT="${BT%/}"
else
  BT="$SDK/build-tools"
fi
JAR="$(ls "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1 || true)"
[ -f "${JAR:-}" ] || { echo "Plateforme Android introuvable. Lance : sdkmanager \"platforms;android-34\""; exit 1; }
command -v javac >/dev/null 2>&1 || { echo "JDK introuvable. Installe un JDK 17+."; exit 1; }

rm -rf build && mkdir -p build/gen build/classes build/dex
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -A assets --java build/gen -o build/base.apk build/res.zip --no-compress-regex '\.jpg$'
javac -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR" -classpath "$JAR:$BT/core-lambda-stubs.jar" -d build/classes \
  $(find src build/gen -name '*.java')
"$BT/d8" --min-api 26 --release --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

KS=accueil-tv.keystore
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass accueiltv -keypass accueiltv -alias accueiltv \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Accueil TV" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:accueiltv --ks-key-alias accueiltv --out app/AccueilTV.apk build/aligned.apk
"$BT/apksigner" verify app/AccueilTV.apk
echo "OK -> app/AccueilTV.apk"
