# OTA Update Rule

## IMPORTANT — Only run OTA when explicitly asked

Only run the OTA build+upload sequence when the user **explicitly** says one of these:
- "build and push OTA"
- "push OTA"
- "build OTA"
- "do OTA"
- "release OTA"
- "upload to GitHub"

**Do NOT run OTA automatically** during regular builds, code changes, or any other task.
**Do NOT run upload.py** unless the user specifically asks for an OTA push.

A regular build request like "build the app", "build apk", "compile", etc. should only run:
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; ./gradlew assembleDebug --no-daemon
```

---

## OTA Command (only when explicitly requested)

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; ./gradlew assembleDebug --no-daemon; python scripts\updater\upload.py
```

## How it works
1. **Gradle** builds the debug APK (`assembleDebug`)
2. **upload.py** reads the version from `app/build.gradle.kts`
3. Creates a **GitHub Release** at `https://github.com/faisal9645/cherish-notes/releases`
4. Uploads the APK as a release asset (free, public download link)
5. Updates **Firestore** `app_config/version` with the download URL

## Prerequisites (already set up)
- `github_token.txt` — GitHub PAT with `repo` scope (in project root, gitignored)
- `firebase_admin_key.json` — Firebase service account key (in project root, gitignored)
- Python packages: `firebase-admin`, `requests`

## Version bumping
Before each OTA push, increment `versionCode` and `versionName` in `app/build.gradle.kts`.
