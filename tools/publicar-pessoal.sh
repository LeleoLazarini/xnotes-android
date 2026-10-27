#!/usr/bin/env bash
# Publishes Leonardo's personal build: compiles the "pessoal" APK, creates the GitHub release the
# in-app updater looks for (tag v<versionCode>, APK attached), and drops a copy in the OneDrive
# folder. Usage: tools/publicar-pessoal.sh "Novidades desta versão"
set -euo pipefail
cd "$(dirname "$0")/.."
NOTES="${1:-Melhorias e correções.}"
export JAVA_HOME="$(cygpath -w /c/dev/android-toolchain/jdk-17.0.20.1+1)"
export ANDROID_HOME='C:\dev\android-toolchain\sdk'
./gradlew.bat assemblePessoal --console=plain > /c/dev/xnotes-build.log 2>&1 || { tail -30 /c/dev/xnotes-build.log; exit 1; }
BT=$(ls -d /c/dev/android-toolchain/sdk/build-tools/* | tail -1)
APK=app/build/outputs/apk/pessoal/app-pessoal.apk
CODE=$("$BT/aapt2.exe" dump badging "$APK" | grep -oE "versionCode='[0-9]+'" | grep -oE '[0-9]+')
OUT="/c/dev/xnotes-release/xnotes-pessoal-$CODE.apk"
mkdir -p /c/dev/xnotes-release && cp "$APK" "$OUT"
git push -q
# The tag goes up with git (gh lacks the workflow scope to create it), and the release names the
# fork explicitly: left to itself, gh picks the upstream repository.
git tag "v$CODE" && git push -q origin "v$CODE"
gh release create "v$CODE" "$OUT" --repo LeleoLazarini/xnotes-android --verify-tag --title "xnotes pessoal $CODE" --notes "$NOTES"
cp "$OUT" "/c/Users/leona/Diamante/Soluções Integradas - Documentos/App Anotações Tablet/"
echo "Publicado v$CODE"
