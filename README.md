# SplitFlow

**Split bills, trips, dinners, and shared expenses in seconds — with multilingual Edge AI voice, ML Kit receipt scanning, Gemini 3 smart fallback, and your own free API keys.**

SplitFlow is a free, open-source Android expense splitter built with Kotlin and Jetpack Compose. Speak an expense naturally in English, Hindi, Marathi, or Tamil (*"Tanmay owes me 500 rupees"* or *"Hey SplitFlow, split 2000 with Tanmay 70 30"*), snap a restaurant bill, or divide Veg / Non-Veg food and drinks by the glass.

---

## Key Features

- **Multilingual Voice Assistant & Hands-Free Wake Word**: Built on Android's native `SpeechRecognizer` + on-device `TextToSpeech` with **English (India)**, **Hindi**, **Marathi**, and **Tamil** support, 4 selectable voice personas (**Diya, Aarav, Riya, Kabir**), Soundbox toggle, and hands-free **"Hey SplitFlow"** foreground standby.
- **Smart Splitting Suite**:
  - **Direct IOU Presets**: Instant 1-tap `Friend Owes Full (100%)` and `You Owe Friend Full` (fixes 2-way splits on IOUs like *"Tanmay owes me 500 rupees"*).
  - **Split by Percentage**: Live 100% progress bar, quick presets (`50/50`, `60/40`, `70/30`, `80/20`), and `"Auto-fill remaining %"`.
  - **Split by Specific Items**: Line-item composer with `Everyone` & member chips plus prorated Tax, Tip, and Discount.
  - **Table & Bar Split Studio**: Separate Veg, Non-Veg, Mocktail, and Alcohol buckets with distinct Food GST vs. Bar VAT calculations.
- **Edge-First Receipt Lens & Batch Import**: Fast on-device **ML Kit Spatial OCR** (~200ms) paired with optional **Cloud AI Enhance** (Gemini 3 Vision) for crumpled, handwritten, or multi-column bills.
- **BYOK Multi-Provider Round-Robin AI Vault**: Add your own free-tier API keys from **Groq**, **OpenRouter**, **Cerebras**, **Mistral**, **Google AI Studio**, or any **Custom HTTPS OpenAI-compatible** endpoint. Keys are encrypted on-device with Android Keystore AES-GCM, rotated round-robin across active providers with automatic cooldown failover on HTTP `429`/`503`, and backed by built-in **Firebase AI Logic** (`gemini-3.8-flash` → `gemini-3.5-flash-lite` auto-fallback).
- **Full Transaction Editing & Undo**: Direct Edit and Delete controls (with one-tap Undo) on every Expense and Settlement.
- **Smart Debt Simplification & UPI QR**: Collapses tangled group debts into the fewest transfers possible with personal UPI QR payments and WhatsApp/SMS nudges.
- **Offline Guest Mode + Optional Google Cloud Sync**: Use SplitFlow completely offline on your phone, or sign in with Google to sync your ledger securely across devices via Firebase Firestore.

---

## Download Latest APK

1. Open the **[Releases](../../releases/latest)** page on GitHub.
2. Under **Assets**, download the latest `SplitFlow-vX.Y.Z-release.apk` file directly to your Android phone (Android 8.0 / API 26 or higher).
3. *(Optional)* Verify the APK integrity against the `SHA-256` checksum listed in the release notes:
   ```bash
   sha256sum SplitFlow-v1.2.0-release.apk
   ```

---

## How to Install (3 Simple Steps)

1. **Download the APK**: Tap `SplitFlow-vX.Y.Z-release.apk` from the latest GitHub Release in your browser.
2. **Allow Your Browser to Install Apps**: When Android prompts *"Your phone isn't allowed to install unknown apps from this source"*, tap **Settings** → toggle **Allow from this source** ON → press Back.
3. **Tap Install**: Confirm **Install** and open **SplitFlow**.

---

## Publishing Public Releases & Fixing Google Sign-In on GitHub APKs

If Google Sign-In fails on APKs built by GitHub Actions, it is almost always because Firebase does not recognize the release APK's signing certificate fingerprint or the OAuth consent screen is still in Testing mode. Complete these 3 steps once:

### 1. Create a Permanent Upload Keystore & Store It in GitHub Secrets
Never commit your keystore or passwords to the repository. Generate a keystore locally and base64-encode it:
```bash
keytool -genkeypair -v -keystore splitflow-release.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 splitflow-release.jks
```
Add these **GitHub Repository Secrets** (`Settings → Secrets and variables → Actions`):
- `KEYSTORE_BASE64`: Base64 string of `splitflow-release.jks`
- `STORE_PASSWORD`: Your keystore password
- `KEY_PASSWORD`: Your key password
- `KEY_ALIAS`: `upload` (or your chosen alias)

### 2. Register Your Release Keystore's SHA-1 & SHA-256 in Firebase Console
Print your keystore fingerprints (also printed automatically in every GitHub Actions build log):
```bash
keytool -list -v -keystore splitflow-release.jks -alias upload | grep -E "SHA1:|SHA256:"
```
1. Open **Firebase Console → Project Settings → Your Apps → Android App (`com.aistudio.splitflow.kvnxqr`)**.
2. Click **Add fingerprint** and paste **both SHA-1 and SHA-256**.
3. Download the refreshed `google-services.json`, base64-encode it (`base64 -w 0 google-services.json`), and save it as the GitHub Secret **`GOOGLE_SERVICES_JSON`**.
4. Confirm `Google` sign-in provider is enabled under **Firebase Console → Authentication → Sign-in method**.

### 3. Publish the OAuth Consent Screen for Public Users
1. Open **Google Cloud Console → APIs & Services → OAuth consent screen → Audience**.
2. If Publishing Status is **Testing** (limited to 100 listed test emails), click **Publish App** to move it to **In Production** so any Android user can sign in with Google.

---

## Adding Your Own Free API Keys (BYOK Round-Robin)

Open **Profile → Voice & AI Providers → API Keys** (or tap **API Keys** inside the Voice Assistant):
1. Pick a free-tier provider preset (**Groq**, **OpenRouter**, **Cerebras**, **Mistral**, **Google AI Studio**, or **Custom HTTPS**).
2. Paste your API key and tap **Add to Key Pool**, then tap **Test** to verify live connectivity.
3. Add multiple keys/providers — SplitFlow rotates requests round-robin and automatically skips rate-limited (`429`) or busy (`503`) keys with temporary cooldowns, falling back seamlessly to built-in Gemini 3 or local Edge parsing.

> All user API keys are encrypted locally on-device via Android Keystore AES-GCM and are **never synced to Firestore or logged**.

---

## Privacy & Permissions

| Permission | Why It Is Requested | Optional? |
| :--- | :--- | :--- |
| **Internet & Network State** | Syncs your ledger when signed into Google and routes optional Cloud AI / BYOK requests. | Core for sync / cloud AI |
| **Microphone (`RECORD_AUDIO`)** | Used only when tapping the mic or enabling hands-free **"Hey SplitFlow"** foreground standby. | Yes — type commands instead |
| **Contacts (`READ_CONTACTS`)** | Used only when you tap **Import Contacts** to pick friends by name without typing. | Yes — add friends manually |
| **Photos / Camera** | Uses Android's zero-permission **Photo Picker** (`PickVisualMedia`) and camera preview for receipts or UPI QR. | Yes |

---

## Build from Source

```bash
git clone <your-repo-url>
cd splitflow
gradle :app:testDebugUnitTest
gradle :app:assembleRelease
```
