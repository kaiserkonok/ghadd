#!/usr/bin/env bash
# Builds the APK and publishes it as a GitHub Release. Installed copies of Ghadd pick it up
# automatically. Before running: bump versionCode AND versionName in app/build.gradle.kts.
#
#   tools/release.sh "What changed"
#
# All releases must be signed with the same key (~/.android/debug.keystore on the build
# machine). Back that file up: if it is lost, installed copies can no longer be updated.
set -euo pipefail
cd "$(dirname "$0")/.."

ver=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts)
tag="v$ver"
notes="${1:-Ghadd $ver}"

if gh release view "$tag" >/dev/null 2>&1; then
    echo "Release $tag already exists. Bump versionCode and versionName first." >&2
    exit 1
fi

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
./gradlew assembleDebug -q

# The app downloads releases/download/<tag>/ghadd.apk, so the asset name must stay fixed.
mkdir -p dist
apk="dist/ghadd.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$apk"

gh release create "$tag" "$apk" --title "Ghadd $ver" --notes "$notes"
echo "Published $tag"
