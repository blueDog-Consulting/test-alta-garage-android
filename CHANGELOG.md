# Changelog

All notable changes to Garage Unlock are documented here. This project adheres to
[Keep a Changelog](https://keepachangelog.com/) and uses an incrementing Play `versionCode`
with `versionName` following semantic-ish versioning.

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
  1 day before the pass expires, each firing once.
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

### Play Console "What's new" (v4)

```
Never get caught by an expired garage pass. Garage Unlock now tracks your Alta guest
pass expiry, shows how many days are left, and reminds you 7 days and 1 day before it
lapses. You can also export/back up your pass so a new phone won't lose it, and removing
a pass now asks for confirmation.
```

## [1.0.0] – versionCode 1–3 – 2026-07-01

- Initial internal-testing releases: one-tap Android Auto unlock for a single Avigilon Alta
  door, phone-side guest-pass configuration (paste or share-in), and shared pass storage
  between the car app and phone.
