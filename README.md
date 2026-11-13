# ScreenVoice Reader (Android)

An Android floating-bubble app that reads English text aloud from **Google Classroom PDFs**, **Gallery photos**, or **any app on your screen** using **Google ML Kit OCR** and **Android Text-to-Speech (TTS)**.

## Features
- **Floating Bubble Overlay:** Stays on top of Google Classroom, PDF viewers, Gallery, or any app.
- **Tap to Read:** Tap the floating bubble to scan the current screen and read English text aloud.
- **Stop / Control:** Tap the stop button on the floating bar to stop reading anytime, or drag the bubble anywhere on screen.
- **100% Offline OCR & TTS:** Uses on-device ML Kit English Text Recognition and Android TTS.

## How to Build the APK Without Android Studio (Free via GitHub)
1. Create a free account on [GitHub](https://github.com) and create a new repository (e.g., `ScreenVoiceReader`).
2. Upload all files from this folder into that repository (keep the `.github/workflows/build-apk.yml` file).
3. Go to the **Actions** tab in your GitHub repository.
4. Click **Build Android APK** -> **Run workflow** (or it runs automatically on upload).
5. Once finished (about 2-3 minutes), download **`ScreenVoiceReader-debug-apk`** from the **Artifacts** section, unzip it, and install `app-debug.apk` on your Android phone.

## How to Use on Your Phone
1. Open **ScreenVoice Reader**.
2. Grant **Display over other apps** permission.
3. Tap **Start Floating Reader** and allow **Screen Recording/Capture** permission.
4. Open any PDF in **Google Classroom** or any **Photo** in your Gallery.
5. Tap the floating **Read** bubble to listen!
