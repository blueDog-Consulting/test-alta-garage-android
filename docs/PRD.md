# Product Requirements Document: Garage Access for Android Auto

**Document version:** 1.0
**Based on:** Garage Unlock prototype (`dev.bluedog.garagedoor`)
**Author context:** Personal apartment garage access via Avigilon Alta guest pass
**Status:** Prototype shipped to Play internal testing; foundation for expanded product
**License (prototype):** MIT

---

## 1. Executive summary

### 1.1 Problem

The user needs to open an apartment garage door (**Garage North Coiling Door** at River Market West) from **Android Auto** while driving, without launching the Alta Open app on the phone, finding the guest pass, and tapping through UI.

The official Alta Open app is phone-centric. Android Auto does not expose a simple, reliable path for residents to trigger a specific door from the car launcher using a guest pass link.

### 1.2 Prototype solution

A minimal **Android Auto IoT car app** with one button that calls the same **OpenPath/Avigilon Alta HTTP APIs** used by the guest pass web link. Configuration happens on the phone; execution happens in the car.

### 1.3 Key discovery

**Sideloaded Android Auto apps do not appear in Customize launcher on modern Android**, even with developer mode and installer spoofing. The only reliable path validated in this project is **installation via Google Play** (internal testing is sufficient for personal use).

### 1.4 Purpose of this document

Capture everything learned building the prototype so a **more expansive application** can be designed with correct constraints, integration details, and a realistic feature roadmap.

---

## 2. Goals and non-goals

### 2.1 Goals (prototype — achieved or partially achieved)

| ID | Goal | Status |
|----|------|--------|
| G1 | One-tap garage unlock from Android Auto | Achieved (after Play install) |
| G2 | Use Alta guest pass without Alta Open in the car | Achieved |
| G3 | Update guest pass when it rotates (3–6 months) without rebuilding app | Achieved (phone settings + share) |
| G4 | Sideload for personal use without Play Console | **Not achievable** on Android 11+ for car launcher |
| G5 | Play Console internal testing distribution | Achieved (workflow documented) |
| G6 | Safe for public GitHub (no secrets in repo) | Achieved |

### 2.2 Non-goals (prototype)

- Multi-door management UI in the car
- User accounts / Alta resident API authentication
- Production Play Store public release
- iOS / CarPlay support
- Offline unlock
- Home Assistant / SmartThings integration
- Enterprise multi-tenant management

### 2.3 Goals for expanded application (recommended)

| ID | Goal | Rationale |
|----|------|-----------|
| E1 | Support multiple doors and multiple sites | Real buildings have many entries per pass |
| E2 | Resident OAuth/API login (if available) | Eliminate guest pass rotation pain |
| E3 | Widget, shortcut, and Assistant integrations | Fallback when car app unavailable |
| E4 | Pass expiry reminders + credential durability | Guest passes expire predictably; don't lose a valid pass before its expiry |
| E5 | Audit log (local) | Know when/where unlock was attempted |
| E6 | Optional backend for remote pass sync | Family members, multiple devices |
| E7 | DHU + real-car test matrix | Regression across Android Auto versions |

---

## 3. User personas and context

### 3.1 Primary persona: Resident driver

- Lives in a building using **Avigilon Alta** (OpenPath backend)
- Has a **guest pass link** emailed/shared periodically
- Drives with **Android Auto** (Pixel, Android 17 validated)
- Wants **minimal distraction** — one tap, no phone interaction in car
- Technical enough to use Play internal testing or follow setup docs

### 3.2 Secondary persona: Building admin (future)

- Issues guest passes to residents
- May want branded app or centralized access policy
- Not in scope for prototype; relevant for expanded product

### 3.3 Environment

| Attribute | Value |
|-----------|-------|
| Door label | `Garage North Coiling Door` |
| Building | River Market West (example deployment) |
| Guest pass URL pattern | `https://access.alta.avigilon.com/cloudKeyUnlock?shortCode={code}` |
| Pass validity | Typically ~6 months (e.g. Jul 1 – Dec 30, 2026) |
| Phone | Pixel, Android 17 |
| Package name (prototype) | `dev.bluedog.garagedoor` |

---

## 4. User stories

### 4.1 Core (prototype)

**US-1: Car unlock**
As a driver, I want to tap one button in Android Auto so the garage door opens without using Alta Open.

**Acceptance criteria:**
- Button visible in Android Auto launcher after app enabled in Customize launcher
- Tap triggers unlock API; success feedback on car screen
- Failure shows actionable message (expired pass, network error, wrong door label)

---

**US-2: Configure guest pass on phone**
As a resident, I want to paste or share my new Alta guest pass link so the app works after rotation.

**Acceptance criteria:**
- Paste full URL or share from Alta Open
- App extracts only the URL from multi-line share text (name, door, validity stripped)
- Saved pass used by both phone test button and Android Auto
- No app rebuild or Play upload required for pass rotation

---

**US-3: Test on phone before driving**
As a user, I want to test unlock on the phone so I know the pass works before I get to the car.

**Acceptance criteria:**
- Phone screen has Save + Unlock buttons
- Status messages for save success/failure and unlock result

---

**US-4: Distribute via Play internal testing**
As the developer, I want to install from Play Store so Android Auto lists the app.

**Acceptance criteria:**
- Signed AAB uploaded to internal testing track
- Install via opt-in link shows `installerPackageName=com.android.vending`
- App appears in Customize launcher (validated against Home Assistant pattern)

### 4.2 Expanded application (recommended backlog)

**US-5:** Select which door to unlock when pass grants multiple entries
**US-6:** Notification 7 days before guest pass expires
**US-7:** Android home screen widget for unlock
**US-8:** Google Assistant routine / App Action integration
**US-9:** Multiple saved passes (home, office, guest)
**US-10:** Authenticate with Alta resident credentials (if API permits)
**US-11:** Family sync via optional encrypted backend
**US-12:** Unlock history with timestamp and result

---

## 5. Functional requirements

### 5.1 Unlock flow (core)

**FR-1: Resolve short code to token**

```
GET https://helium.prod.openpath.com/shortUrl/{shortCode}
```

Response: JSON with `data.fullUrl` containing JWT token as query param `token=...`

**FR-2: Parse JWT payload**

- Decode JWT segment 2 (base64url)
- Read `entryData` array
- Match `uiLabel` to configured door label → `entryId` (e.g. `947325` for Garage North Coiling Door)

**FR-3: Execute unlock**

```
POST https://api.openpath.com/tokens/cloudKeyUnlockTokens/{token}/use
Content-Type: application/json
Body: { "entryId": <id> }
```

Success: **HTTP 204 No Content**

**FR-4: Door scoping**

- Guest pass may list multiple doors
- App must unlock **only** the door matching configured label
- Wrong or missing label → error: "Door not found: {label}"

### 5.2 Guest pass configuration

**FR-5: Input formats**

| Input | Accepted |
|-------|----------|
| Full Alta URL | `https://access.alta.avigilon.com/cloudKeyUnlock?shortCode=...` |
| Raw short code | Alphanumeric, 8–32 chars |
| Alta Open share text | Extract URL via regex; ignore surrounding text |

**FR-6: Storage**

- Persist `short_code` in `SharedPreferences` (app-private)
- Alongside the code, persist expiry lifecycle metadata: `saved_at`, `expires_at` (epoch ms; absent = unknown), `expiry_source` (`parsed` | `manual` | `unknown`), and `last_notified_threshold` (reminder dedup)
- `allowBackup=false` — do not cloud-backup credentials
- No hardcoded live short code in source (prototype final state)

**FR-6a: Credential durability — don't lose the pass before it expires**

The credential with a real lifetime is the guest pass (valid up to ~6 months, §2.1); the re-resolved JWT is only a short-lived unlock token, so it is *not* a source for the pass's true expiry. Three failure modes must be prevented:

1. **Expiry capture & display** — on save, parse validity dates from the raw Alta share text (ranges take the end date, e.g. "Jul 1 – Dec 30, 2026"); fall back to a manual date picker when none is present. The phone shows a live "Valid until … · N days left" status that turns amber ≤ 7 days and red once expired. A failed date parse never clears a previously known expiry for the same short code.
2. **Device-migration durability** — because `allowBackup=false`, a reinstall / cleared data / new phone silently loses the pass while it is still valid. Mitigation: a user-controlled **Export / copy pass** action that reconstructs the guest-pass link (`…/cloudKeyUnlock?shortCode=<code>`) for the clipboard or share sheet, so the credential is never trapped solely in app-private storage. *(Rejected alternative: flipping `allowBackup=true` with a scoped backup rule — it would place the plaintext short code in cloud backup, reversing the deliberate no-cloud-credential decision. Revisit only alongside EncryptedSharedPreferences, §10.2.5.)*
3. **Destructive-action guards** — removing the saved pass requires an explicit confirmation dialog (which points the user at Export first); no unlock/error path ever clears the pass, and saving an already-expired pass warns but still saves rather than silently discarding it.

**FR-7: Share intent**

- Register `ACTION_SEND` `text/plain` on phone activity
- Auto-normalize and save on share from Alta Open

**FR-8: Unconfigured state**

- If no pass saved, unlock blocked with message: "No guest pass saved…"
- Car UI shows same guidance

### 5.3 Android Auto UI

**FR-9: Car app category**

- `CarAppService` with `androidx.car.app.category.IOT`
- `automotive_app_desc.xml`: `<uses name="template" />`
- UI: `GridTemplate` with single `GridItem` (Google IoT pattern)

**FR-10: Car interaction model**

- Single tap → unlock
- Loading state while request in flight
- Status text on grid item after completion
- No text input in car (configuration is phone-only)

**FR-11: Host validation**

- Prototype uses `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` for dev/sideload experiments
- Expanded app: evaluate stricter validation for Play-distributed production

### 5.4 Phone UI

**FR-12: Layout**

- Guest pass input field
- Save button
- Pass status (saved short code or not configured)
- Divider
- Unlock test button
- Status area for unlock results

**FR-13: System UI**

- Content must respect status bar (fitsSystemWindows / `WindowCompat.setDecorFitsSystemWindows`)
- Dark theme aligned with app palette (`#121826` background)

### 5.5 Release and versioning

**FR-14: Version management**

- `version.properties` is source of truth
- Release script auto-increments `versionCode` before each Play upload
- `versionName` updated manually for user-facing releases

**FR-15: Release artifacts**

- Build signed AAB + APK
- Archive to `releases/` as `garagedoor-v{versionName}-{versionCode}-{timestamp}.aab`
- Keep all prior builds in folder

**FR-16: Signing**

- Upload keystore (local, gitignored)
- Play App Signing (Google-managed app signing key)
- Passwords via `keystore.properties`, env vars, or `--prompt`

---

## 6. Non-functional requirements

### 6.1 Performance

| Requirement | Target |
|-------------|--------|
| Unlock API round-trip | < 5s typical (15s timeouts in prototype) |
| Car UI feedback | Loading indicator within 1 tap |
| Cold start (car) | CarAppService session init < 3s |

### 6.2 Reliability

- Network required for unlock (no offline cache of token beyond session)
- Graceful handling of HTTP 4xx (expired pass, invalid short code)
- Car and phone read same `SharedPreferences` store

### 6.3 Security

| Topic | Requirement |
|-------|-------------|
| Guest pass | Equivalent to sharing unlock link; treat as secret |
| Repo | No live short codes, keystores, or passwords in git |
| Door scope | Only configured label unlocked |
| Backup | Disabled for app data |
| MIT license | Open source code; secrets remain user-managed |

### 6.4 Compatibility

| Layer | Minimum |
|-------|---------|
| Android | API 23 (Android 6) |
| Target SDK | 35 |
| Android Auto | Car App Library 1.4.0, min car API level 1 |
| JDK | 17 |

### 6.5 Accessibility (expanded app)

- Car: large touch target, clear status text (prototype minimal)
- Phone: TalkBack labels on inputs and buttons
- High contrast maintained in dark theme

---

## 7. Technical architecture (prototype)

### 7.1 Component diagram

```text
┌─────────────────────────────────────────────────────────────┐
│                        Android Phone                         │
│  ┌──────────────┐    ┌──────────────────┐    ┌────────────┐ │
│  │ MainActivity │───▶│ UnlockConfigStore │◀───│ Share Intent│ │
│  │ (settings +  │    │ (SharedPreferences)│    │ Alta Open │ │
│  │  test unlock)│    └────────┬─────────┘    └────────────┘ │
└───────────────┼───────────────┼─────────────────────────────┘
                │               │
                │               │ same store
┌───────────────┼───────────────┼─────────────────────────────┐
│               ▼               ▼         Android Auto         │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ GarageCarAppService → GarageSession → GarageScreen  │    │
│  │              GridTemplate (IoT)                      │    │
│  └──────────────────────┬──────────────────────────────┘    │
└─────────────────────────┼──────────────────────────────────┘
                          ▼
              ┌───────────────────────┐
              │   AltaUnlockClient    │
              │  shortUrl → JWT → POST │
              └───────────┬───────────┘
                          ▼
              ┌───────────────────────┐
              │ OpenPath / Alta APIs  │
              └───────────────────────┘
```

### 7.2 Module inventory

| Component | Responsibility |
|-----------|----------------|
| `AltaConfig` | Door label, API URL builders |
| `AltaUnlockClient` | HTTP: resolve token, find entryId, POST unlock |
| `UnlockConfigStore` | Parse, normalize, persist short code |
| `MainActivity` | Phone UI, share target, test unlock |
| `GarageCarAppService` | Android Auto entry, host validator |
| `GarageSession` | Car session lifecycle |
| `GarageScreen` | Car GridTemplate UI |

### 7.3 Dependencies (prototype)

- `androidx.core:core-ktx`
- `androidx.appcompat:appcompat`
- `androidx.car.app:app:1.4.0`

No Material Components, no networking library (raw `HttpURLConnection`), no DI framework.

### 7.4 Manifest essentials

```xml
<uses-permission android:name="android.permission.INTERNET" />
<queries><package android:name="com.google.android.projection.gearhead" /></queries>

<!-- CarAppService: IOT category -->
<!-- meta-data: com.google.android.gms.car.application → automotive_app_desc -->
<!-- meta-data: androidx.car.app.minCarApiLevel = 1 -->
```

---

## 8. Android Auto platform constraints (critical)

### 8.1 Validated behavior

When opening **Customize launcher**, Android Auto runs `CAR.VALIDATOR`:

```text
W/CAR.VALIDATOR: isPackageInstalledByPlayCheck service not connected
W/CAR.VALIDATOR: Package DENIED; failed all other checks [dev.bluedog.garagedoor]
```

Despite:

- `allow_unknown_sources: true` in Android Auto developer settings
- `adb install -i com.android.vending` (PM shows Play installer; Play Protect still sees `com.android.shell`)
- Correct `CarAppService`, IOT category, `template` capability
- App visible to system package query

**Home Assistant** (`io.homeassistant.companion.android`) appears in same session — likely real Play install.

### 8.2 Implications for expanded product

| Approach | Car launcher visibility | Recommendation |
|----------|-------------------------|----------------|
| Play internal testing | Yes | **Required** for car app |
| Play production | Yes | For public distribution |
| ADB sideload + installer spoof | No | Do not rely on |
| KingInstaller / AAAD | No / N/A for custom APK | Not viable for custom app |
| Root + phenotype patch | Maybe | Out of scope for consumer product |
| Assistant shortcut | N/A (not car app) | Good fallback feature |

### 8.3 Play Console requirements (personal developer account)

- $25 one-time developer registration
- Package name fixed at app creation: `dev.bluedog.garagedoor`
- Android Auto form factor declaration + Android for Cars ToS
- Privacy policy, Data safety, content rating
- Internal testing: up to 100 testers, no 14-day closed test required
- Production (new personal accounts post-Nov 2023): 12 closed testers × 14 days before production access

---

## 9. Configuration model

### 9.1 Current (prototype)

| Setting | Where | Rotates? |
|---------|-------|----------|
| Guest pass short code | Phone app / SharedPreferences | Every 3–6 months |
| Door label | `AltaConfig.DOOR_LABEL` (compile-time) | Rarely |
| App version | `version.properties` | Each Play upload |

### 9.2 Recommended (expanded app)

| Setting | Where | Notes |
|---------|-------|-------|
| Short code or resident auth token | Secure storage (EncryptedSharedPreferences) | |
| Door label(s) | User-selectable from JWT `entryData` | Parse on save |
| Default door | User preference | For one-tap car UX |
| Pass expiry date | Parsed from share text or API | Reminder scheduling |
| Site name | Display only | From share text |

### 9.3 Guest pass share text format (Alta Open)

```text
{name} has shared a guest pass with you to unlock {door} at {site}.

https://access.alta.avigilon.com/cloudKeyUnlock?shortCode={code}

This link will be valid from {start} to {end}.
```

Parser must extract URL only; optionally parse validity dates for expiry UX.

---

## 10. Feature specification: prototype vs. expanded

### 10.1 Feature matrix

| Feature | Prototype | Expanded v1 | Expanded v2 |
|---------|-----------|-------------|-------------|
| One-tap car unlock | ✅ | ✅ | ✅ |
| Phone test unlock | ✅ | ✅ | ✅ |
| Guest pass paste/share | ✅ | ✅ | ✅ |
| Multi-door select | ❌ | ✅ | ✅ |
| Pass expiry reminder | ❌ | ✅ | ✅ |
| Resident API login | ❌ | ⚠️ Research | ✅ |
| Widget | ❌ | ✅ | ✅ |
| Assistant integration | ❌ | ✅ | ✅ |
| Unlock history | ❌ | Local | Cloud sync |
| Multiple sites | ❌ | ❌ | ✅ |
| iOS / CarPlay | ❌ | ❌ | ⚠️ Research |

### 10.2 Expanded v1 — recommended scope

**Theme:** "Garage & door access for Android Auto, done right"

1. **Onboarding**
   - Explain Play install requirement for Android Auto
   - Guide: share pass from Alta Open → save → test → enable in car

2. **Door discovery**
   - On save, call shortUrl API and list all `entryData.uiLabel` values
   - User picks default door; car button uses default

3. **Expiry awareness & credential durability** (see FR-6a)
   - Capture `expires_at` on save: parse validity from raw share text, manual date-picker fallback
   - In-app "valid until · N days left" status chip (amber ≤ 7 days, red when expired)
   - WorkManager daily check → notification at 7 days and 1 day before expiry (dedup via `last_notified_threshold`)
   - Export / copy pass for device migration; confirmation guard on remove

4. **Fallbacks**
   - Home screen widget (unlock default door)
   - Assistant App Action: "Open garage"

5. **Polish**
   - EncryptedSharedPreferences for short code
   - Better error taxonomy (expired, network, wrong door, Play not installed)
   - DHU screenshot automation for Play listing

### 10.3 Expanded v2 — platform play

- Resident OAuth if Alta exposes it to third parties
- Optional lightweight backend (Cloudflare Worker) for:
  - Pass sync across family devices (encrypted)
  - Admin audit dashboard for buildings (B2B pivot)
- Wear OS complication (stretch)
- Integration with building apps (Home Assistant, SmartThings)

---

## 11. API reference (integration spec)

### 11.1 Endpoints

| Step | Method | URL | Success |
|------|--------|-----|---------|
| Resolve | GET | `https://helium.prod.openpath.com/shortUrl/{shortCode}` | 200 + JSON |
| Unlock | POST | `https://api.openpath.com/tokens/cloudKeyUnlockTokens/{jwt}/use` | 204 |

### 11.2 JWT payload (relevant fields)

```json
{
  "entryData": [
    {
      "entryId": 947325,
      "uiLabel": "Garage North Coiling Door"
    }
  ]
}
```

### 11.3 Error handling matrix

| Condition | User message (car) | User message (phone) |
|-----------|-------------------|----------------------|
| No pass saved | Open app on phone to configure | Paste guest pass above |
| HTTP 4xx on shortUrl | Pass expired; update on phone | Same + share flow hint |
| Door label not in JWT | Door not found | Check door name in settings |
| Network timeout | Unlock failed; try again | Same |
| HTTP 204 | {door} unlocked | Same |

---

## 12. Distribution and DevOps

### 12.1 Build commands

```bash
./gradlew assembleDebug              # local dev
./scripts/build-release-aab.sh --prompt   # release + version bump + archive
```

### 12.2 Artifact layout

```text
releases/
  garagedoor-v1.0.0-3-20260701-094723.aab
  garagedoor-v1.0.0-3-20260701-094723.apk
app/build/outputs/...                  # Gradle default (ephemeral)
```

### 12.3 Scripts

| Script | Purpose |
|--------|---------|
| `build-release-aab.sh` | Sign, bump version, archive |
| `install-for-android-auto.sh` | Debug install with Play flag (unreliable) |
| `diagnose-android-auto.sh` | Logcat during Customize launcher |
| `generate-play-store-assets.py` | Play listing images |

### 12.4 Git hygiene

**Ignored:** `keystore.properties`, `*.jks`, `local.properties`, `releases/`, `*.apk`, `*.aab`, logs
**Committed:** source, `version.properties`, `keystore.properties.example`, `play-store/` assets, `LICENSE`

---

## 13. Privacy and compliance

### 13.1 Data collection (prototype)

| Data | Collected | Transmitted |
|------|-----------|-------------|
| Guest pass short code | On-device only | To Alta/OpenPath on unlock |
| Personal info | None | None |
| Analytics | None | None |
| Location | None | None |

### 13.2 Play Console Data safety (prototype answers)

- Does not collect user data for developer
- Data encrypted in transit (HTTPS)
- No account creation

### 13.3 Privacy policy (minimal)

> App does not collect, store, or share personal data. Unlock requests go directly to Avigilon Alta/OpenPath. Guest pass is stored only on device.

### 13.4 Expanded app considerations

- If adding backend: GDPR/CCPA disclosure, data retention, deletion
- If adding analytics: crash reporting only, no pass content in logs
- Building B2B: SOC2, access logs, tenant isolation

---

## 14. Testing strategy

### 14.1 Prototype test plan

| # | Test | Pass criteria |
|---|------|---------------|
| T1 | Save URL on phone | Short code extracted and shown |
| T2 | Share from Alta Open | URL only in field; auto-save |
| T3 | Phone unlock | HTTP 204; success message |
| T4 | Play install | `installerPackageName=com.android.vending` |
| T5 | Customize launcher | App listed and enableable |
| T6 | Car unlock | Door opens; car shows success |
| T7 | Expired pass | Clear error on phone and car |
| T8 | Release build | versionCode increments; AAB uploads to Play |

### 14.2 Expanded app test matrix

- Multiple doors on one pass
- Pass rotation without app update
- Android Auto on wireless vs USB
- Android 14, 15, 16, 17
- Different head units (DHU + 2 real vehicles)
- Play internal vs production track
- Offline / airplane mode
- Encrypted prefs migration

### 14.3 Diagnostic tooling

`diagnose-android-auto.sh` — capture `CAR.VALIDATOR` lines while on Customize launcher screen.

Key log patterns:

- `Package DENIED; failed all other checks` → not Play installed
- `isPackageInstalledByPlayCheck service not connected` → Play check failed

---

## 15. Workarounds documented

### 15.1 Assistant shortcut (no custom car app)

Android Auto → Customize launcher → Add shortcut → Assistant action → Routine that opens guest URL or triggers automation.

**Pros:** Works today, no Play fight
**Cons:** Not native car UI; depends on Assistant/Home

### 15.2 Home Assistant

If resident already uses HA with Alta integration, car access via HA companion Android Auto app (Play installed).

**Insight for expanded product:** HA proves Play-installed car apps work; consider HA webhook integration as optional feature.

---

## 16. Risks and mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Alta changes API | App breaks | Version API client; monitor JWT shape |
| Guest pass rotation | User locked out | Share UX; expiry capture + reminders (FR-6a); resident auth long-term |
| Valid pass lost early (reinstall / new phone / accidental clear) | User locked out before real expiry | Export/copy pass for backup; confirmation guard on remove; no auto-clear on errors (FR-6a) |
| Play policy on car apps | Rejection | Follow Android for Cars guidelines; IoT template only |
| Google tightens sideload | Already blocked | Play-only distribution |
| Short code in leaked APK | Unauthorized access | No hardcoded codes; EncryptedSharedPreferences |
| OpenPath rate limits | Unlock fails | Retry with backoff; user messaging |
| Wrong door label | Silent wrong-door risk | Door picker from JWT; confirm UI on first save |

---

## 17. Open questions for expanded product

1. **Does Avigilon Alta offer resident OAuth/API** for third-party apps, or only guest passes?
2. **B2C vs B2B:** Personal tool vs. building-wide product?
3. **CarPlay:** Is Apple CarPlay API available for custom IoT apps?
4. **Branding:** White-label for property managers?
5. **Backend:** Is local-only sufficient for family, or sync required?
6. **Monetization:** Free OSS, paid Play app, or building subscription?

---

## 18. Recommended expanded product vision

### 18.1 Product name (working)

**OpenDoor** / **AltaDrive** / **GaragePass** — neutral name not tied to one door

### 18.2 One-liner

> The Android Auto app for Avigilon Alta doors — configure once on your phone, unlock from your car.

### 18.3 MVP for expanded v1 (8–12 weeks)

1. Fork prototype architecture
2. Door picker from JWT
3. Encrypted storage + expiry notifications
4. Widget + Assistant action
5. Play internal → closed testing → production
6. Play listing with generated assets
7. Privacy policy site
8. In-app onboarding for Play install requirement

### 18.4 Success metrics

| Metric | Target |
|--------|--------|
| Car unlock success rate | > 95% when network available |
| Time to configure new pass | < 30 seconds via share |
| Customize launcher visibility | 100% after Play install |
| Pass rotation support | Zero app updates required |
| Crash-free sessions | > 99.5% |

---

## 19. Appendix: prototype repository state

| Item | Value |
|------|-------|
| Repo | `garage-android-app` |
| Package | `dev.bluedog.garagedoor` |
| versionName | `1.0.0` |
| versionCode | `3` (as of last release script run) |
| License | MIT |
| Kotlin package | `dev.bluedog.garagedoor` |
| Car category | IOT + GridTemplate |
| Default pass in source | Removed (must user-save) |

---

## 20. Appendix: conversation timeline (decisions log)

| Phase | Decision |
|-------|----------|
| Initial build | Alta guest pass API reverse-engineered from web link |
| Android Auto UI | IOT + GridTemplate (Google garage-door pattern) |
| Sideload path | ADB Play flag, KingInstaller explored — **failed validation** |
| AAAD | Confirmed cannot install custom APKs |
| Diagnose logs | `CAR.VALIDATOR` Play install check is root cause |
| Distribution | Play Console internal testing is correct path |
| Guest pass rotation | Phone settings + share; no rebuild |
| Alta share text | Strip boilerplate; save URL only |
| Package rename | `com.garage.unlock` → `dev.bluedog.garagedoor` for Play |
| Release tooling | `version.properties` + auto-increment + `releases/` archive |
| Security | Remove hardcoded short code before GitHub |
| License | MIT + SPDX headers |
| Git | Initial commit + license commit |

---

This PRD captures the prototype as-built and frames a credible path to a fuller product. For the expanded app, prioritize **Play distribution**, **guest pass lifecycle UX**, and **multi-door support** before investing in backend or B2B features.
