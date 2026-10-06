# Releasing Readora

Installers and portable builds are **not committed** (50-100 MB each). They are attached to a GitHub Release.

## What has and has not been verified

| Artifact | Status |
|---|---|
| `build.sh` / tests / runnable jar (Linux) | Built, 123 tests pass, jar launches. Also verified from a folder path containing spaces. |
| `.deb` (Ubuntu/Debian) | **Built, installed, launched and uninstalled** on Ubuntu 24.04. |
| Portable Linux zip | Built, unzipped and launched. |
| Whole UI audit inside the *packaged* runtime | 18 checks, 0 exceptions (see below). |
| `build.bat`, `package.bat`, `.exe`, `.msi`, Windows portable | **Not run** (no Windows machine available). The commands follow the same jpackage options that were verified on Linux. GitHub Actions runs `build.bat test` on Windows on every push - **watch the first CI run**. |
| "Open with" (Linux `.deb`) | **Verified**: the installed entry has `MimeType=application/pdf` and `Exec=... %f`; the installed app, launched with a PDF path (including one with spaces, a comma and parentheses), opened it. |
| "Open with" (Windows `.exe`/`.msi`, Settings button) | **Not run** (no Windows machine). Uses jpackage's `--file-associations` and per-user `HKCU` registry keys; the registry commands are generated and unit-tested but never executed here. |
| `.rpm` | Not built (needs `rpmbuild`). |
| macOS installer | Not supported: it needs an `.icns` icon. The runnable jar works on macOS. |

## 1. Pre-flight

```bash
./build.sh test                      # build + 123 checks; must end with "0 failed"
./build.sh run                       # click through the app once
```

Bump the version in **`src/app/AppInfo.java`** (the package scripts read it from there), and add the entry to `CHANGELOG.md`.

## 2. Commit, tag, push

```bash
git add -A
git commit -m "Release 1.0.0"
git tag -a v1.0.0 -m "Readora 1.0.0"
git push origin main --tags
```

Pushing the `v*` tag makes GitHub Actions build on Windows, macOS and Linux and publish a GitHub Release
containing `Readora-v1.0.0-jar.zip` (runnable jar + libs).

## 3. Build the installers (each on its own OS)

**Linux** - needs a full JDK 17+, `fakeroot` and `dpkg-deb` (for `.deb`):

```bash
scripts/package.sh            # portable zip + .deb  ->  installers/
scripts/package.sh deb        # only the .deb
scripts/package.sh rpm        # only the .rpm (needs rpmbuild)
```

Produces `readora_1.0.0_amd64.deb` and `Readora-1.0.0-portable-linux.zip`.

**Windows** - needs a full JDK 17+ and [WiX Toolset 3.x](https://wixtoolset.org) on PATH:

```bat
scripts\package.bat           :: .exe + .msi + portable zip  ->  installers\
scripts\package.bat exe
scripts\package.bat msi
scripts\package.bat portable
```

Produces `Readora-1.0.0.exe`, `Readora-1.0.0.msi` and `Readora-1.0.0-portable-windows.zip`.

> The Windows script contains a fixed `--win-upgrade-uuid`. **Never change it**: it is how Windows knows
> a newer Readora is an upgrade of the installed one rather than a second app.

### The jpackage command, if you prefer to run it yourself

```bash
./build.sh                                                      # creates dist/
jpackage --type deb --name Readora --linux-package-name readora --app-version 1.0.0 \
  --vendor "Readora" --description "Readora: The PDF App" \
  --input dist --main-jar Readora.jar --main-class app.Main --icon resources/logo.png \
  --linux-shortcut --linux-menu-group Office --linux-app-category office \
  --add-modules java.base,java.desktop,java.logging,java.naming,java.prefs,java.sql,java.xml,jdk.unsupported,jdk.charsets,jdk.localedata \
  --jlink-options "--strip-debug --no-header-files --no-man-pages --compress zip-6" \
  --dest installers
```

`--add-modules` lists only what Readora needs (found with `jdeps`), which keeps the bundled runtime at about
80 MB instead of 170 MB. If you ever add a dependency, re-run
`jdeps --multi-release 21 --ignore-missing-deps --print-module-deps -cp "dist/libs/*" dist/Readora.jar`
and update the list in both scripts.

## 4. Attach installers to the Release

After the CI release exists (step 2), upload your files:

```bash
gh release upload v1.0.0 installers/* --clobber
```

or open the Release on github.com, **Edit**, and drag the files in. Publish checksums if you like:

```bash
cd installers && sha256sum * > SHA256SUMS.txt      # Windows: certutil -hashfile <file> SHA256
```

## Notes on "Open with"

- jpackage builds the association into the `.exe`/`.msi`/`.deb` from `packaging/pdf-association-*.properties`.
  On Linux, jpackage's own desktop entry omits `%f`, so the file manager would start Readora without telling it
  which file; `packaging/linux-resources/Readora.desktop` overrides it. Keep the file name: jpackage finds it by
  the application name.
- Windows protects the *default* app choice, so installing Readora adds it to "Open with" but doesn't take over
  double-click; users pick "Always use this app" themselves.
- The portable zip can't register itself at install time, so Settings > File association does it for the
  current user (no administrator rights).

## 5. Smoke-test what you published

Download the artifact from the Release page (not from your build folder), install it on a clean machine or
VM, open a PDF, switch theme, right-click a PDF and check "Open with" lists Readora, drag a PDF onto the window, create a New PDF and save an edit, and uninstall. Windows SmartScreen will warn about the unsigned installer - that
is expected (see the README note).
