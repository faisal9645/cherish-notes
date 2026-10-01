# Cherish — Private Messenger for Two ❤️

Cherish is a luxury, modern private chat application built with Kotlin and Jetpack Compose, designed exclusively for two people: you and your partner.

Unlike public social networks, Cherish eliminates strangers, public discovery, and algorithms in favor of a private, intimate space with real-time messaging, voice notes, shared memories, milestone countdowns, and biometric privacy protection.

---

## 🌟 Key Features

### 1. Private Two-Person Architecture
- **Exclusive Pairing**: Only User A and User B can access their private room.
- **Passcode & Email Authentication**: Secured with Firebase Authentication and an optional shared couple secret key.
- **Strict Isolation**: No strangers, no group chats, no public discovery.

### 2. Real-Time Chat & Intimate Experience
- **Live Firestore Messaging**: Instant delivery, read receipts (sent/delivered/read double-checkmarks).
- **Online & Typing Presence**: Real-time partner status with animated typing dots.
- **Message Interactions**:
  - Reply with quote context
  - Emoji reactions (❤️, 🥰, 😘, 🔥, 🥺, 👍)
  - Edit sent messages
  - Delete messages
  - Star / favorite cherished messages
  - In-chat keyword search
  - Date separators and scroll-to-latest button

### 3. Media & Voice Notes
- **Photos & Attachments**: Image compression before upload to conserve bandwidth, full-screen interactive viewer.
- **Voice Messages**:
  - Live amplitude audio waveform recording
  - Duration timer
  - Interactive playback waveform with progress scrubbing
  - Cancel / discard recording support

### 4. Privacy & Security
- **4-Digit Secret App Lock**: Automatically locks when returning from background.
- **Biometric Authentication**: Fingerprint and Face Unlock support via Android BiometricPrompt.
- **Screenshot Protection**: Blocks in-app screenshots and screen recording (`FLAG_SECURE`).
- **Notification Privacy**: Masks intimate message text on lock screen notifications.
- **Firebase Security Rules**: Complete `firestore.rules` and `storage.rules` included.

### 5. Couple Milestones & Shared World
- **Shared Memories**: Photo timeline of special dates, vacations, and milestones.
- **Important Dates & Countdowns**: Anniversaries, birthdays, first date, with countdown days and days-in-love counter.
- **Shared Notes**: Real-time synced love letters, wishlists, and bucket lists with pinning.
- **Shared Gallery**: Fast media browser for all exchanged photos and voice notes.

---

## 🛠️ Architecture & Tech Stack

- **UI Framework**: Android Jetpack Compose with Material 3
- **Design Aesthetic**: Deep Midnight Wine & Rose Gold romantic palette
- **Architecture**: MVVM (Model-View-ViewModel) + Repository Pattern
- **Backend**: Firebase Authentication, Firestore Database, Cloud Storage, Cloud Messaging
- **Audio**: Android MediaRecorder + MediaPlayer
- **Images**: Coil Compose with bitmap compression pipeline
- **Security**: AndroidX Biometric + SharedPreferences with SHA-256

---

## 🚀 Firebase Setup Instructions

1. **Create a Firebase Project**:
   - Go to [Firebase Console](https://console.firebase.google.com/).
   - Add an Android App with package name: `com.aistudio.cherish.pxrtmv`.

2. **Add `google-services.json`**:
   - Download `google-services.json` from the Firebase Console.
   - Place it in the `app/` folder of the project.
   *(Note: The Gradle setup includes `missingGoogleServicesStrategy = WARN`, so the app compiles cleanly even before adding the file).*

3. **Enable Firebase Services**:
   - **Authentication**: Enable Email/Password in Firebase Auth.
   - **Firestore Database**: Create database in production mode. Deploy rules from `firestore.rules`.
   - **Cloud Storage**: Create storage bucket. Deploy rules from `storage.rules`.
   - **Cloud Messaging**: Enable FCM for push notifications.

4. **Pairing User A and User B**:
   - First user registers with their email, enters their name and partner's email.
   - Second user registers with their email and the same Couple Secret Passcode (e.g. `CHERISH-2026`).
   - The two accounts are linked directly in Firestore!

---

## 🔄 Dual-Environment Workflow (Antigravity & AI Studio + CI/CD)

This repository is optimized to switch seamlessly between **Google AI Studio** and your local **Antigravity / Android Studio** environment via Git and GitHub Actions:

### 1. Seamless Git Syncing
- **Keystores**: The debug keystore template is encoded in `debug.keystore.base64` in source control. Whenever Gradle runs (in Antigravity, Android Studio, or CI), `build.gradle.kts` automatically restores `debug.keystore` if absent. No manual keystore generation or broken builds.
- **Environment & Secrets**: `.env.example` is tracked in git. When pulling fresh on Antigravity, Gradle automatically seeds `.env` from `.env.example`.
- **Git Hygiene**: `.gitignore` excludes temporary IDE artifacts (`.idea/shelf`, `.idea/caches`), dynamic output APKs, `.build-outputs/`, and local credentials.

### 2. Live Run in Antigravity / Android Studio
- **Windows**: Double-click `live_run.bat` to detect your connected phone/emulator, build, install, and hot-reload.
- **macOS / Linux**: Run `./live_run.sh` from your terminal.

### 3. Automated CI/CD Pipeline
- **GitHub Actions Workflow**: Configured at `.github/workflows/android.yml`.
- Runs automatically on every `push` and `pull_request` to `main`/`master`.
- Executes `./gradlew testDebugUnitTest` and builds `./gradlew assembleDebug`.
- Publishes debug APK artifacts automatically to GitHub Actions runs for direct downloading and testing.

