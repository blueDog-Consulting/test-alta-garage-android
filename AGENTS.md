# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project summary

**Garage Unlock** (`dev.bluedog.garagedoor`) is a personal Android app with an Android Auto car UI. One button unlocks a specific garage door via Avigilon Alta / OpenPath guest-pass HTTP APIs.

Primary use case: install from **Google Play internal testing** so Android Auto lists the app; configure the rotating guest pass on the phone; tap unlock in the car.

## Architecture

```
Phone (MainActivity)
  └─ UnlockConfigStore (SharedPreferences) ← guest pass short code
  └─ AltaUnlockClient.unlockDoor(shortCode, doorLabel)

Android Auto (GarageCarAppService → GarageSession → GarageScreen)
  └─ reads same UnlockConfigStore
  └─ GridTemplate + IOT category, single grid item
```

### Unlock API flow (`AltaUnlockClient`)

1. `GET https://helium.prod.openpath.com/shortUrl/{shortCode}` → JWT in `data.fullUrl`
2. Parse JWT payload `entryData[]` for `uiLabel` → `entryId`
3. `POST https://api.openpath.com/tokens/cloudKeyUnlockTokens/{token}/use` body `{ entryId }` → HTTP 204 = success

### Android Auto integration

- `CarAppService` with `androidx.car.app.category.IOT`
- `res/xml/automotive_app_desc.xml` declares `template` capability
- `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` in `GarageCarAppService` (required for sideload/dev; Play install is still required for launcher visibility on modern Android)
- Car UI cannot accept text input — all guest pass configuration happens in `MainActivity`

## Key files

| File | Role |
|------|------|
| `app/build.gradle.kts` | `applicationId`, reads `version.properties` + `keystore.properties` |
| `version.properties` | `versionCode`, `versionName` — bumped by release script |
| `AltaConfig.kt` | `DOOR_LABEL`, API URL helpers |
| `UnlockConfigStore.kt` | Persist pass; `extractGuestPassLink()` strips Alta share boilerplate |
| `MainActivity.kt` | Settings UI, share target (`ACTION_SEND`), status bar insets |
| `GarageScreen.kt` | Car unlock button + status message |
| `scripts/build-release-aab.sh` | Release pipeline: keystore verify → bump version → AAB/APK → `releases/` |

## Build commands

```bash
./gradlew assembleDebug          # local dev APK
./gradlew bundleRelease          # release AAB (needs keystore.properties)
./scripts/build-release-aab.sh --prompt   # preferred release path
```

Release script auto-increments `versionCode` in `version.properties` before Gradle runs. Play Console rejects duplicate version codes.

## Configuration rules

- **Guest pass:** runtime only via `UnlockConfigStore`. No hardcoded short code in source. Users paste URL or share from Alta Open. Do not require rebuild when pass rotates.
- **Door label:** compile-time in `AltaConfig.DOOR_LABEL`. Change for different doors/builds.
- **Package name:** `dev.bluedog.garagedoor` — must match Play Console app; Kotlin package path mirrors this.

## Android Auto constraints (do not “fix” blindly)

- Sideloaded APKs are **filtered out** of Customize launcher on Android 11+ despite `allow_unknown_sources` and ADB `-i com.android.vending` spoofing.
- Log signature: `CAR.VALIDATOR: Package DENIED; failed all other checks [dev.bluedog.garagedoor]`
- **Play Store install** (internal testing is enough) is the supported path for this app to appear in the car launcher.
- AAAD does not install custom APKs. KingInstaller / phenotype patching are user workarounds, not repo goals.

## Code conventions

- Kotlin, JDK 17, `minSdk` 23, `compileSdk` 35
- Minimal dependencies: AndroidX Core, AppCompat, Car App Library 1.4.0
- No Material Components unless justified — plain `EditText` / `Button` on phone UI
- Keep car UI to `GridTemplate` IOT pattern; avoid navigation/media categories
- Network on background thread (`Executors`); car screen uses `invalidate()` after async unlock
- Prefer small, focused diffs; match existing naming and structure

## Secrets and gitignore

**Never commit:**

- `keystore.properties`, `*.jks`, `*.keystore`
- `local.properties`
- `releases/` artifacts
- `android-auto-*.log`

**Safe to commit:**

- `keystore.properties.example`
- `version.properties` (version codes are not secrets)

## Testing checklist

1. Phone: save guest pass URL → test unlock → confirm short code in status
2. Share from Alta Open → only URL in field, save succeeds
3. Android Auto Customize launcher (after Play install): app visible and enabled
4. Car / DHU: tap grid item → HTTP 204 → success message
5. Release: `./scripts/build-release-aab.sh --prompt` → `versionCode` increments → AAB in `releases/`

## Common agent tasks

| Task | Approach |
|------|----------|
| New guest pass handling | `UnlockConfigStore` parsing; keep car + phone in sync via same store |
| Play upload rejected (version) | Ensure `build-release-aab.sh` ran (bumps `versionCode`); never hardcode version in `build.gradle.kts` |
| Car app not visible | Document Play internal testing; do not chase sideload-only manifest hacks |
| Door name change | `AltaConfig.DOOR_LABEL` only |
| UI overlap status bar | `fitsSystemWindows` + `WindowCompat.setDecorFitsSystemWindows(window, true)` |

## Do not

- Revert to hardcoded-only short codes without user request
- Add heavy frameworks, analytics, or cloud backends for this personal app
- Commit signing keys or live guest pass URLs
- Change `applicationId` without explicit user instruction (locked to Play Console app)
