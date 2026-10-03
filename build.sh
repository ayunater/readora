#!/usr/bin/env bash
# Builds Readora with plain javac + jar (no Maven/Gradle needed). Requires JDK 17+.
#
#   ./build.sh          compile and create dist/Readora.jar (+ dist/libs)
#   ./build.sh run      build, then launch the app
#   ./build.sh test     build, then run the automated tests
#   ./build.sh clean    remove build output
set -euo pipefail
cd "$(dirname "$0")"

CP="libs/*"
OUT="out/classes"
DIST="dist"

case "${1:-build}" in
  clean)
    rm -rf out dist
    echo "Cleaned."
    exit 0
    ;;
esac

command -v javac >/dev/null || { echo "javac not found - install a JDK (17 or newer)." >&2; exit 1; }

rm -rf "$OUT" && mkdir -p "$OUT"
find src -name '*.java' > out/sources.txt
javac -encoding UTF-8 -Xlint:-options -cp "$CP" -d "$OUT" @out/sources.txt
cp -r resources/. "$OUT"/

# Runnable jar: the manifest points at the jars in dist/libs next to it.
rm -rf "$DIST" && mkdir -p "$DIST/libs"
cp libs/*.jar "$DIST/libs/"
CLASS_PATH=$(cd "$DIST" && ls libs/*.jar | tr '\n' ' ')
printf 'Main-Class: app.Main\nClass-Path: %s\n' "$CLASS_PATH" > out/manifest.txt
jar --create --file "$DIST/Readora.jar" --manifest out/manifest.txt -C "$OUT" .
echo "Built $DIST/Readora.jar"

case "${1:-build}" in
  run)
    exec java -jar "$DIST/Readora.jar"
    ;;
  test)
    mkdir -p out/test-classes
    find test -name '*.java' > out/test-sources.txt
    javac -encoding UTF-8 -Xlint:-options -cp "$OUT:$CP" -d out/test-classes @out/test-sources.txt
    java -Djava.awt.headless=true -cp "$OUT:out/test-classes:$CP" viewer.SmokeTest
    ;;
esac
