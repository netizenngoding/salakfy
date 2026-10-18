# Salakfy — Build Guide

Salakfy is a fork / rebrand of **ArchiveTune** (https://github.com/rukamori/ArchiveTune), licensed under **GPL-3.0**.

| Item            | Value             |
|-----------------|-------------------|
| App name        | Salakfy           |
| Package (appId) | `com.dev.salakfy` |
| Version         | 15.1.0 (142)      |
| Min Android     | 8.0 (API 26)      |

> The internal Kotlin namespace stays `moe.rukamori.archivetune` (the source folder layout).
> This is NORMAL — the install ID is `applicationId = com.dev.salakfy`, so it does not conflict
> with the original ArchiveTune and can be installed side by side.

---

## What was changed
1. `applicationId` -> `com.dev.salakfy` (app/build.gradle.kts)
2. App name & all UI strings "ArchiveTune" -> "Salakfy" (all languages)
3. Launcher icon (adaptive + legacy + round + monochrome), in-app logo (`drawable-nodpi/app_icon_small.png`), fastlane icon
4. Icon background color `#5B2C1A` (salak brown) -> `res/values/ic_launcher_background.xml`
5. App shortcuts (`xml-v25/shortcuts.xml`) -> targetPackage `com.dev.salakfy`
6. Auto-updater to the original ArchiveTune GitHub is **disabled** (`UPDATER_AVAILABLE=false`) so the app doesn't prompt to "update" to ArchiveTune
7. Download folder `ArchiveTune` -> `Salakfy`
8. The original developer's keystore (`Koiverse.jks`) was removed; our own keystore was created
9. GitHub Actions workflow replaced with a simple Salakfy build
10. Release builds always signed (fallback to debug key if no keystore)

---

## Requirements
- **JDK 21** (required — Temurin/OpenJDK 21)
- **Android SDK** with **compileSdk 37** (Android Studio latest / Canary – SDK Manager -> install "Android API 37")
- NDK version `30.0.16248370` (Gradle downloads it automatically if SDK licenses are accepted)
- RAM >= 8 GB (16 GB recommended)
- Internet (first build downloads ~1–2 GB of dependencies)

---

## OPTION A — Build via GitHub Actions (easiest, no powerful PC needed)
1. Create a new repo on GitHub (e.g. `salakfy`), **private** recommended.
2. Extract the zip, then:
   ```bash
   cd Salakfy
   git init && git add . && git commit -m "Salakfy initial"
   git branch -M main
   git remote add origin https://github.com/USERNAME/salakfy.git
   git push -u origin main
   ```
   (Submodules `core`, `lyrics`, `IconPack`, `morideobfuscator` are already included as regular folders.
   If git complains about submodules, delete the `.gitmodules` file first.)
3. (Optional, so the APK is signed with your OWN key — important for updates)
   Repo -> Settings -> Secrets and variables -> Actions -> New secret:
   - `KEYSTORE` = base64 content of `app/keystore/release.keystore`
     (`base64 -w0 app/keystore/release.keystore` on Linux/Mac, or
      `certutil -encode release.keystore out.txt` on Windows and copy the contents without the header lines)
   - `KEYSTORE_PASSWORD` = see `KEYSTORE_INFO.txt`
   - `KEY_ALIAS` = `salakfy`
   - `KEY_PASSWORD` = see `KEYSTORE_INFO.txt`
4. Tab **Actions** -> "Build Salakfy APK" -> **Run workflow**.
5. Wait ~15–25 minutes -> download APK from **Artifacts**.

> WARNING: Do NOT push `app/keystore/` & `local.properties` to a public repo (already in .gitignore).

---

## OPTION B — Build on PC (Android Studio)
1. Install Android Studio (latest), JDK 21.
2. Extract zip -> Open folder `Salakfy` in Android Studio.
3. SDK Manager: install **Android SDK Platform 37** + NDK (side by side) `30.0.16248370`.
4. Edit `local.properties` -> set `sdk.dir` to your SDK path, e.g.:
   - Windows: `sdk.dir=C\:\\Users\\NAME\\AppData\\Local\\Android\\Sdk`
   - Mac: `sdk.dir=/Users/NAME/Library/Android/sdk`
   - Linux: `sdk.dir=/home/NAME/Android/Sdk`
   (Signing passwords are already in `local.properties`.)
5. Gradle sync, then build from the terminal:

   ```bash
   # Linux / Mac
   ./gradlew assembleGmsMobileArm64Release

   # Windows
   gradlew.bat assembleGmsMobileArm64Release
   ```
6. APK output:
   `app/build/outputs/apk/gmsMobileArm64/release/` (file `*.apk`)

### Available build variants
| Task | For |
|---|---|
| `assembleGmsMobileArm64Release` | Modern phones (recommended, smallest) |
| `assembleGmsMobileArmeabiRelease` | Old 32-bit phones |
| `assembleGmsMobileUniversalRelease` | All CPUs (larger) |
| `assembleFossMobileUniversalRelease` | No Google services (FOSS) |
| `assembleGmsTvUniversalRelease` | Android TV |
| `assembleGmsMobileArm64Debug` | Debug (package `com.dev.salakfy.debug`) |

---

## Optional API keys (in `local.properties`)
```
LASTFM_API_KEY=...
LASTFM_SECRET=...
DISCORD_APPLICATION_ID=...   # your own Discord app ID for Rich Presence
```
Without them, the app still works (only Last.fm scrobble / custom Discord app are off).

---

## Changing the icon later
Replace the files in `app/src/main/res/mipmap-*/ic_launcher*.png` and
`app/src/main/res/drawable-nodpi/app_icon_small.png`, and the background color in
`app/src/main/res/values/ic_launcher_background.xml`.

## Legal (GPL-3.0) — MUST follow
- Salakfy must also be **GPL-3.0** (the LICENSE file is kept as is).
- If you distribute the APK, you **must also share the source code** (e.g. a public GitHub repo).
- Keep credits for the original author (rukamori / ArchiveTune) — the About page still shows the credits.
- Do not claim it was built from scratch; write "Based on ArchiveTune".
