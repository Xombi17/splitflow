# SplitFlow

**Split bills, trips, dinners, and shared expenses in seconds — with 100% on-device AI voice & receipt scanning, zero paywalls, and no cloud AI rate limits.**

SplitFlow is a free, open-source Android expense splitter built with Kotlin and Jetpack Compose. Speak an expense naturally (*"Tanmay owes me 500 rupees"*), snap a photo of a restaurant bill, or divide Veg / Non-Veg food and drinks by the glass — all processed locally on your phone.

---

## Screenshots & Demo

<!-- Replace these placeholders with real screenshots or a short GIF from your phone -->
| Home & Net Position | Local Voice Assistant | Receipt Lens OCR | Table & Bar Split |
| :---: | :---: | :---: | :---: |
| ![Home Dashboard](docs/screenshots/home.png) | ![Voice Assistant](docs/screenshots/voice.png) | ![Receipt Lens](docs/screenshots/receipt_lens.png) | ![Table & Bar Split](docs/screenshots/dining_split.png) |

---

## Key Features

- **100% On-Device Voice Assistant**: Powered by **Cactus Compute Whistle** (local neural STT) with automatic fallback to Android's on-device `SpeechRecognizer` and a fast natural-language parser. Say *"Tanmay owes me 500 rupees"* or *"Paid 1,800 for dinner with Parth and Shravani"* to update your accounts immediately — with one-tap Undo.
- **On-Device Receipt Lens & Batch Import**: Powered by **Cactus Mirage** + on-device **ML Kit Text Recognition** to itemize bills, prorate tax/GST and service charges, and let you tap avatars to assign each item.
- **Table & Bar Split Studio**: Separate Veg, Non-Veg, Mocktail, and Alcohol buckets with distinct Food GST vs. Bar VAT calculations.
- **Smart Debt Simplification**: Minimizes the number of transfers across trips and groups, with one-tap **UPI QR** payments and WhatsApp/SMS payment nudges.
- **Offline Guest Mode + Optional Cloud Sync**: Use SplitFlow completely offline on your phone, or sign in with Google to sync your ledger securely across devices via Firebase Firestore.
- **Live Engine Diagnostics**: Both Voice Assistant and Receipt Lens include a live status badge showing whether inference ran on Cactus Whistle/Mirage or an on-device fallback, plus clear error messages if a model download fails.

---

## Download Latest APK

1. Open the **[Releases](../../releases/latest)** page on GitHub.
2. Under **Assets**, download the latest `SplitFlow-vX.Y.Z-release.apk` file directly to your Android phone (Android 8.0 / API 26 or higher).
3. *(Optional)* Verify the APK integrity against the `SHA-256` checksum listed in the release notes:
   ```bash
   sha256sum SplitFlow-v1.0.0-release.apk
   ```

---

## How to Install (3 Simple Steps)

Because SplitFlow is distributed for free directly through GitHub Releases instead of the Google Play Store, Android will ask you to confirm installation once:

1. **Download the APK**: Tap `SplitFlow-vX.Y.Z-release.apk` from the latest GitHub Release in Chrome (or your browser).
2. **Allow Your Browser to Install Apps**: When Android prompts *"For your security, your phone currently isn't allowed to install unknown apps from this source"*, tap **Settings** → toggle **Allow from this source** ON → press Back.
3. **Tap Install**: Confirm **Install** and open **SplitFlow**. *(Tip: You can toggle "Allow from this source" back OFF in Settings afterward if you prefer.)*

---

## Privacy & Permissions

SplitFlow follows least-privilege principles and never sells your data or sends voice recordings or receipt photos to third-party cloud LLM APIs.

| Permission | Why It Is Requested | Optional? |
| :--- | :--- | :--- |
| **Internet & Network State** | Syncs your ledger when signed into Google, checks offline status, and downloads optional on-device Cactus AI weights. | Core for sync / downloads |
| **Microphone (`RECORD_AUDIO`)** | Used only while you actively tap the mic in Voice Assistant for local speech-to-text transcription. | Yes — you can type commands instead |
| **Contacts (`READ_CONTACTS`)** | Used only when you tap **Import Contacts** so you can pick friends by name without typing. Contacts stay on your ledger. | Yes — you can add friends manually |
| **Photos / Camera** | Uses Android's zero-permission **Photo Picker** (`PickVisualMedia`) and camera preview only when you choose a receipt or UPI QR image. | Yes |

---

## Build from Source

```bash
git clone <your-repo-url>
cd splitflow
gradle :app:assembleRelease
```

To generate a SHA-256 checksum for your GitHub Release notes:

```bash
sha256sum app/build/outputs/apk/release/app-release.apk
```
