#!/data/data/com.termux/files/usr/bin/bash
# PHONEBOOST: Termux build skripti.  Ishlatish:  ./build.sh   yoki   ./build.sh assembleRelease
set -e
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
if [ ! -d "$SDK" ]; then
  echo "Android SDK topilmadi. ANDROID_HOME ni o'rnating (masalan: export ANDROID_HOME=\$HOME/android-sdk)."
  exit 1
fi
echo "sdk.dir=$SDK" > local.properties

if [ ! -f gradlew ]; then
  gradle wrapper --gradle-version 8.9
fi

AAPT2="${PREFIX:-/data/data/com.termux/files/usr}/bin/aapt2"
TASK="${1:-assembleDebug}"

./gradlew "$TASK" --no-daemon -Pandroid.aapt2FromMavenOverride="$AAPT2"

echo
echo "Tayyor. APK:"
find app/build/outputs/apk -name "*.apk"
