#!/usr/bin/env bash
# Builds Readora installers with jpackage (Linux). Run from anywhere; output goes to installers/.
#
#   scripts/package.sh            app-image + .deb   (what this machine can build)
#   scripts/package.sh deb        only the .deb
#   scripts/package.sh rpm        only the .rpm      (needs rpmbuild)
#   scripts/package.sh app-image  only the portable folder (installers/Readora/), zipped
#
# The version comes from src/app/AppInfo.java, so it always matches the code.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v jpackage >/dev/null || { echo "jpackage not found - install a full JDK 17+ (not just a JRE)." >&2; exit 1; }
VERSION=$(grep -oE 'VERSION = "[^"]+"' src/app/AppInfo.java | cut -d'"' -f2)
[ -n "$VERSION" ] || { echo "Could not read VERSION from src/app/AppInfo.java" >&2; exit 1; }
echo "Packaging Readora $VERSION"

./build.sh                      # fresh dist/ with the jar and libs
rm -rf installers && mkdir -p installers

# Only the Java modules Readora uses (found with jdeps), so the bundled runtime is ~80 MB instead of ~170 MB.
COMMON=(--app-version "$VERSION" --vendor "Readora" --description "Readora: The PDF App"
        --input dist --main-jar Readora.jar --main-class app.Main --icon packaging/logo.png
        --add-modules java.base,java.desktop,java.logging,java.naming,java.prefs,java.sql,java.xml,jdk.unsupported,jdk.charsets,jdk.localedata
        --jlink-options "--strip-debug --no-header-files --no-man-pages --compress zip-6 --disable-plugin dedup-legal-notices")
# These options are only valid for real installers (deb/rpm), not for the portable app-image.
# --file-associations puts Readora in file managers' "Open with" list for PDFs; the resource override adds
# "%f" to the desktop entry's Exec line (without it the file manager starts Readora but never says which file).
LINUX=(--linux-package-name readora --linux-shortcut --linux-menu-group Office --linux-app-category office
       --file-associations packaging/pdf-association-linux.properties --resource-dir packaging/linux-resources)

build() {
  echo "--> $1"
  if [ "$1" = app-image ]; then
    jpackage --type app-image --name Readora "${COMMON[@]}" --dest installers
  else
    jpackage --type "$1" --name Readora "${COMMON[@]}" "${LINUX[@]}" --dest installers
  fi
}

TARGET="${1:-default}"
case "$TARGET" in
  deb)        build deb ;;
  rpm)        build rpm ;;
  app-image)  build app-image ;;
  default)    build app-image; command -v dpkg-deb >/dev/null && build deb || echo "(skipping .deb: dpkg-deb not found)" ;;
  *) echo "Unknown target '$TARGET' (use deb, rpm or app-image)" >&2; exit 1 ;;
esac

# Portable zip of the app-image, if one was built.
if [ -d installers/Readora ]; then
  (cd installers && zip -qr "Readora-$VERSION-portable-linux.zip" Readora && rm -rf Readora)
fi
echo; echo "Done. Files in installers/:"; ls -lh installers
