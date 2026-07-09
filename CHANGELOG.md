# Changelog

All notable changes to Garage Unlock are documented here. This project adheres to
[Keep a Changelog](https://keepachangelog.com/) and uses an incrementing Play `versionCode`
with `versionName` following semantic-ish versioning.

## [1.0.0] – versionCode 5 – 2026-07-09

Focus: **show the doors the pass actually grants.** Ships together with the versionCode 4
expiry work below (v4 was never published to Play).

### Fixed
- The unlock UI no longer shows a hard-coded "Garage North Coiling Door" button. The app now
  discovers **every** door the guest pass grants (from the token's `entryData`) and lists them
  all. Doors whose label mentions "garage" or "parking" sort to the top, then the rest — each
  group alphabetical.
- Nothing is shown in the unlock area when no pass is saved (previously a door button appeared
  even with no stored pass).

### Added
- **Door discovery (PRD E1).** `AltaUnlockClient.fetchPassInfo()` resolves the pass and returns its
  `entryData`; the list is cached in `UnlockConfigStore` (`doors_json`) and re-fetched when a
  different pass is saved. Both the phone and the Android Auto grid list all doors.
- **Expiry from the token (`exp`).** The same token resolve now reads the JWT `exp` claim — the
  authoritative pass expiry — and stores it (`expiry_source = "token"`). Share-text date parsing
  and the manual date picker remain as fallbacks; a user-set manual date is never overwritten.
  A URL-only pass (no visible dates) now populates "Valid until … · N days left" automatically.

### Changed
- Unlock targets a door by its `entryId` (`unlockDoor(shortCode, entryId, label)`) instead of
  matching a hard-coded `uiLabel`; the `AltaConfig.DOOR_LABEL` constant is removed.
- Android Auto shows one grid item per door (per-door loading/result) and a "set it up on your
  phone" message when no pass is saved.

## [1.0.0] – versionCode 4 – 2026-07-08

Focus: **don't lose the guest pass before it expires.** The app now tracks the pass's real
expiry, warns before it lapses, and makes the credential recoverable across devices.

### Added
- **Pass expiry tracking.** On save, validity dates are parsed from the pasted/shared Alta
  text (ISO, `MM/DD/YYYY`, and month-name formats, including ranges like
  `Jul 1 – Dec 30, 2026` where the end date wins). When no date is present, a date picker lets
  you set it manually.
- **Expiry status chip.** The phone shows `Valid until <date> · N days left`, turning amber at
  ≤ 7 days and red once expired.
- **Expiry reminders.** A daily background check (WorkManager) posts a notification 7 days and
  2 days before the pass expires, **and once it has expired**, each firing once. Saving or
  changing a pass also runs an immediate check so a near-expiry or expired pass notifies right
  away instead of waiting for the next daily run.
- **Export / copy pass.** Reconstructs your Alta guest-pass link to the clipboard and share
  sheet so the pass can be backed up and restored on a new device (it otherwise lives only on
  this device).
- **Guarded removal.** Removing the saved pass now requires a confirmation dialog.

### Changed
- `UnlockConfigStore` persists expiry lifecycle metadata (`saved_at`, `expires_at`,
  `expiry_source`, `last_notified_threshold`) alongside the short code.
- A failed re-save never overwrites a previously known expiry for the same pass; an
  already-expired pass warns but is still saved rather than silently discarded.
- PRD updated: E4 expanded into pass-expiry reminders **and** credential durability (FR-6a),
  plus a new risk-register entry for losing a valid pass early.

### Notes
- Storage remains plaintext app-private `SharedPreferences` with `allowBackup="false"`;
  migrating to `EncryptedSharedPreferences` and cloud-backup options are tracked for a later
  release.
- Adds `POST_NOTIFICATIONS` (Android 13+) for expiry reminders and the
  `androidx.work:work-runtime-ktx` dependency.

---

### Play Console "What's new" (next release)

See [`distribution/whatsnew/whatsnew-en-US`](distribution/whatsnew/whatsnew-en-US) for the
current, ready-to-paste blurb (covers door discovery + expiry, since production is v3).

## [1.0.0] – versionCode 1–3 – 2026-07-01

- Initial internal-testing releases: one-tap Android Auto unlock for a single Avigilon Alta
  door, phone-side guest-pass configuration (paste or share-in), and shared pass storage
  between the car app and phone.
