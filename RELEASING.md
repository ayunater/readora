# Releasing Readora

Installers and the portable build are **not committed** (they are 50-100 MB each and make the repository
slow to clone). They are attached to a GitHub Release instead.

## 1. Check

```bash
./build.sh test        # all checks must pass
./build.sh run         # click through the app once
```

Update the version in `src/app/AppInfo.java` and `CHANGELOG.md`.

## 2. Tag and push

```bash
git tag -a v1.0.0 -m "Readora 1.0.0"
git push origin main --tags
```

Pushing a `v*` tag makes GitHub Actions build the project on Windows, macOS and Linux and attach
`Readora-v1.0.0-jar.zip` (runnable jar + libs) to a new Release.

## 3. Build installers (run each on its own OS)

First `./build.sh` (or `build.bat`) so `dist/` exists. Then:

**Windows** (needs [WiX Toolset](https://wixtoolset.org) for `exe`/`msi`):

```bat
jpackage --type exe --name Readora --app-version 1.0.0 --input dist --main-jar Readora.jar ^
  --main-class app.Main --icon resources\logo.ico --win-menu --win-shortcut --dest installers
jpackage --type msi --name Readora --app-version 1.0.0 --input dist --main-jar Readora.jar ^
  --main-class app.Main --icon resources\logo.ico --win-menu --win-shortcut --dest installers
jpackage --type app-image --name Readora --app-version 1.0.0 --input dist --main-jar Readora.jar ^
  --main-class app.Main --icon resources\logo.ico --dest portable
```

Zip `portable\Readora` as `Readora-1.0.0-portable-windows.zip`.

**Linux** (`deb` needs `fakeroot`, `rpm` needs `rpm-build`; Linux wants a PNG icon):

```bash
jpackage --type deb --name readora --app-version 1.0.0 --input dist --main-jar Readora.jar \
  --main-class app.Main --icon resources/logo.png --dest installers
jpackage --type rpm --name readora --app-version 1.0.0 --input dist --main-jar Readora.jar \
  --main-class app.Main --icon resources/logo.png --dest installers
```

## 4. Attach to the Release

On the GitHub Releases page, edit the release for the tag and drag in the `.exe`, `.msi`, `.deb`, `.rpm`
and portable zip.
