# Notes

A simple, fast notes app for Android.

## Features

- Notes and checklists
- Reminders
- Categories, pinning and search
- Sort by recently modified, A–Z or category
- Light, dark and pure black themes
- Works offline
- Updates itself when a new version is released

## Install

Download the latest APK from [Releases](https://github.com/faisal9645/cherish-notes/releases/latest) and open it on your phone. Android will ask you to allow installs from your browser or files app the first time.

After that, the app checks for new versions on its own.

## Build

Requirements: Android Studio (JDK 21) and Android SDK 35.

```
./gradlew :app:assembleRelease
```

## Releasing an update

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Run `python scripts/updater/upload.py` (needs a GitHub token with `repo` access in `github_token.txt`, which is not committed).

The script builds the release APK and publishes it as a GitHub release; installed apps pick it up as an update.
