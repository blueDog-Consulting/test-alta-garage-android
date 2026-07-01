# Garage Unlock

Android Auto app with one button to open **Garage North Coiling Door** via an [Avigilon Alta](https://www.avigilon.com/) guest pass — without opening Alta Open on your phone.

**PRD:** [PRD.md](docs/PRD.md)

**Package:** `dev.bluedog.garagedoor`

## How it works

1. Resolve the guest pass short code: `GET https://helium.prod.openpath.com/shortUrl/{shortCode}`
2. Decode the JWT unlock token and find the entry ID for the configured door label
3. Unlock: `POST https://api.openpath.com/tokens/cloudKeyUnlockTokens/{token}/use` with `{ "entryId": … }`

The phone app and Android Auto car UI share the same saved guest pass.

## Requirements

- Android SDK + JDK 17 (Android Studio recommended)
- Phone on Android 6+ (`minSdk` 23), tested on Pixel / Android Auto
- Valid Alta guest pass link (rotates every few months)
- **Play Console internal testing** install for Android Auto visibility (see below)

## Quick start (development)

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open **Garage Unlock** on the phone, paste or share your guest pass link, tap **Save guest pass**, then **Open Garage North Coiling Door** to test.

## Guest pass setup

Guest passes are stored on-device in `SharedPreferences` — not hardcoded for day-to-day use.

1. Open **Garage Unlock** on your phone
2. Paste the Alta guest pass URL, or **Share** from Alta Open → Garage Unlock
3. Tap **Save guest pass**
4. Test unlock on the phone before using Android Auto

When Alta rotates your pass, repeat the steps above. No app rebuild required.

The app extracts only the URL from Alta share text (name, door, validity dates are stripped automatically).

**Door name** is configured in code for this build:

```kotlin
// app/src/main/java/dev/bluedog/garagedoor/AltaConfig.kt
const val DOOR_LABEL = "Garage North Coiling Door"
```

Change `DOOR_LABEL` if your door label in Alta differs. Fork and adjust for your own door.

## Release builds (Play Console)

Android Auto blocks sideloaded car apps on modern Android unless installed from Google Play. Use **internal testing**:

### One-time signing setup

```bash
# Create upload keystore (back up garage-upload.jks somewhere safe)
keytool -genkey -v \
  -keystore garage-upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload

cp keystore.properties.example keystore.properties
# Edit keystore.properties — or use --prompt when building
```

### Build and archive

```bash
./scripts/build-release-aab.sh --prompt
```

This script:

1. Verifies your keystore
2. **Auto-increments `versionCode`** in `version.properties` (required for each Play upload)
3. Builds signed release AAB + APK
4. Archives versioned copies under `releases/`, e.g. `garagedoor-v1.0.0-3-20260701-120000.aab`

Upload the latest `.aab` from `releases/` to **Play Console → Testing → Internal testing**.

### Play Console checklist

- Create app with package `dev.bluedog.garagedoor`
- Enable **Android Auto** form factor (Setup → Advanced settings)
- Privacy policy URL, Data safety, Content rating
- Add yourself as internal tester → install from Play Store opt-in link
- Uninstall any sideloaded build before installing from Play

## Android Auto

1. Connect phone to car (USB or wireless)
2. **Settings → Connected devices → Android Auto → Customize launcher**
3. Enable **Garage Unlock**
4. Tap **Garage North Coiling Door** in the car launcher

### Developer mode (optional)

Android Auto app → tap version **10 times** → Developer settings → enable **Unknown sources** (helps for some sideload experiments; Play install is still the reliable path).

### Desktop Head Unit (optional)

```bash
adb forward tcp:5277 tcp:5277
cd "$ANDROID_HOME/extras/google/auto"
./desktop-head-unit
```

Enable **Start head unit server** in Android Auto developer settings first.

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/build-release-aab.sh` | Signed release AAB/APK; bumps `versionCode`; archives to `releases/` |
| `scripts/install-for-android-auto.sh` | Debug install with Play Store installer flag (unreliable on Android 11+) |
| `scripts/diagnose-android-auto.sh` | Capture logcat while opening Customize launcher |
| `scripts/generate-play-store-assets.py` | Generate Play listing images into `play-store/` |

## Project layout

```
app/src/main/java/dev/bluedog/garagedoor/
  AltaConfig.kt           # Door label, API URL helpers
  AltaUnlockClient.kt     # HTTP client (shortUrl → JWT → unlock POST)
  UnlockConfigStore.kt    # Guest pass persistence + URL parsing
  MainActivity.kt         # Phone UI: save pass, test unlock, share target
  GarageCarAppService.kt  # Android Auto CarAppService entry point
  GarageScreen.kt         # Car UI (GridTemplate / IOT)
  GarageSession.kt        # Car session
version.properties        # versionCode / versionName (committed; bumped on release)
keystore.properties.example
scripts/
```

## Versioning

`version.properties` is the source of truth. `app/build.gradle.kts` reads it at build time.

- Edit `versionName` manually when you want a new user-facing version string
- `versionCode` is incremented automatically by `build-release-aab.sh` before each release build
- Play Console requires every upload to have a higher `versionCode` than the last

## Security

- Guest pass short codes grant access to doors on the pass. This app unlocks only the door matching `DOOR_LABEL`.
- Do **not** commit `keystore.properties`, `*.jks`, or live guest pass URLs to a public repo.
- Guest pass short codes are stored on-device only after the user saves them in the app.
- `allowBackup="false"` — guest pass prefs are not backed up to cloud.

## Troubleshooting

### App not in Customize launcher

**Confirmed cause on Android 11+:** Android Auto validates Play Store install (`CAR.VALIDATOR: Package DENIED; failed all other checks`). ADB installer spoofing (`-i com.android.vending`) does not pass Play Protect’s real installer check.

**Fix:** Install from **Play Console internal testing**. After a real Play install, the app appears like other car apps (e.g. Home Assistant).

Run diagnostics while on the Customize launcher screen:

```bash
./scripts/diagnose-android-auto.sh
```

Look for `Package DENIED` vs successful listing.

### Unlock failed

- Guest pass may have expired — share a new link from Alta Open into Garage Unlock
- Door label must match Alta exactly (`AltaConfig.DOOR_LABEL`)
- Phone needs internet when unlocking

### Release build signing errors

Passwords come from `keystore.properties` or env vars — not an interactive `keytool` prompt during Gradle:

```bash
./scripts/build-release-aab.sh --prompt
# or
KEYSTORE_PASSWORD='…' KEY_PASSWORD='…' ./scripts/build-release-aab.sh
```

### Workaround without a custom car app

Android Auto → Customize launcher → **Add shortcut** → **Assistant action** → routine that opens your guest pass URL.

## License

MIT License. See [LICENSE](LICENSE).

Not affiliated with Avigilon or Motorola Solutions.
