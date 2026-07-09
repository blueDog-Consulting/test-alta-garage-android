# Android Auto DHU Testing — Garage Unlock

Manual steps to regression-test the in-car unlock using the **Desktop Head Unit (DHU)**, Google's
Android Auto simulator. This is the check that can't be fully automated headlessly (see
[Why not automated](#why-this-isnt-scripted) at the bottom).

Budget ~15 min the first time (mostly Android Auto setup), ~3 min on repeat runs.

---

## What you need

- **A device with the *full* Android Auto app**, set up and signed in:
  - **Best: a real Android phone** (Android Auto is preinstalled and functional). Recommended.
  - An emulator works **only** if it uses a **Google Play** system image AND you sign into a Google
    account and let the Play Store update Android Auto from the shipped `-stub` to the full app.
    The stub cannot project (no head-unit server) — this is the blocker that stops a headless run.
- **The DHU installed** (one-time):
  ```bash
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "extras;google;auto"
  # installs to: $ANDROID_HOME/extras/google/auto/desktop-head-unit
  ```
- **Garage Unlock installed** on that device (debug or release APK):
  ```bash
  adb install -r app/build/outputs/apk/release/app-release.apk
  ```
- USB debugging enabled and `adb devices` showing the device as `device`.

---

## One-time: enable Android Auto developer mode + head-unit server

1. Open **Android Auto** settings on the phone:
   - Android 10+: **Settings → Connected devices → Connection preferences → Android Auto**.
   - Or launch the Android Auto app directly if present.
2. Scroll to the bottom and tap the **Version** (e.g. "Version 12.x…") **~10 times** until you see
   "You are now a developer".
3. Tap the **⋮ (three-dot) menu → Developer settings**.
4. Enable **"Add new cars to Android Auto"** (a.k.a. *Unknown sources*) so the DHU is accepted.
5. Back in the **⋮ menu**, tap **Start head unit server**. Leave this running.

You only redo step 5 (Start head unit server) on future sessions; steps 1–4 are once per device.

---

## Each run: connect the DHU

```bash
# 1. Forward the head-unit server port from the device to your machine
adb forward tcp:5277 tcp:5277

# 2. Launch the DHU
cd "$ANDROID_HOME/extras/google/auto"
./desktop-head-unit            # add: -c config/default.ini  to pick a specific layout
```

A car-display window opens and mirrors the Android Auto experience. If it prints
`Connecting over ADB to localhost:5277... connected.` but no UI appears, the head-unit server
isn't running (redo the step above) — see [Troubleshooting](#troubleshooting).

---

## The regression test

**Setup (on the phone, before/while connected):**

1. Open Garage Unlock on the phone and save a **real, current** Alta guest pass (paste the link or
   share it in). Confirm the status chip shows a green **"Valid until … · N days left"**.
   - A fake short code is fine for verifying the *flow* and error handling, but the door won't
     actually open — use a real pass for a true end-to-end unlock.

**In the DHU:**

2. From the DHU launcher (the app grid / "phone-screen" apps row), open **Garage Unlock**
   (category IoT). It should render a single grid item titled **"Garage North Coiling Door"** with
   the garage icon and status **"Ready"**.
3. **Click the grid item.** Expect:
   - Title switches to **"Unlocking…"** and the item shows a loading spinner.
   - On success (real pass): status becomes **"Garage North Coiling Door unlocked"** and the door
     actually opens (HTTP 204 from Openpath).
   - On an expired/invalid pass (HTTP 4xx): status shows **"Guest pass expired. Open Garage Unlock
     on your phone to update it."**
   - On no network: a network error string — **not** a crash.

**Critical regression checks (what this feature could have broken):**

4. After any failed unlock in the car, switch to the phone and confirm the **saved pass is still
   there** (green chip unchanged). An unlock error must **never** clear the pass.
5. Confirm the car app never shows the phone-only editing UI (it's read-only in the car by design).

### Expected results

| Step | Expected |
|------|----------|
| Open app in DHU | Single IoT grid item "Garage North Coiling Door", status "Ready" |
| Click (valid pass) | "Unlocking…" → door opens → "…unlocked" |
| Click (expired pass) | 4xx → "Guest pass expired. Open Garage Unlock on your phone to update it." |
| Click (no network) | Graceful error message, no crash |
| After any failure | Pass still saved on phone (no auto-clear) |

---

## Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| DHU: `connected.` then blank / exits | Head-unit server not running, or Android Auto is a **`-stub`** build. On an emulator, update Android Auto via the Play Store (needs Google sign-in); the stub has no head-unit server. Prefer a real phone. |
| `Activity class …carsetup… does not exist` | Same stub problem — the full projection host isn't installed. |
| DHU can't find device | `adb devices` must show one authorized device; re-run `adb forward tcp:5277 tcp:5277`. |
| Garage Unlock missing from car launcher | In Android Auto Developer settings enable **"Add new cars"/unknown sources**; confirm the app is installed and its `CarAppService` has the `androidx.car.app.category.IOT` intent filter. |
| Door doesn't open but no error | You saved a fake/expired short code — use a real, current Alta pass. |

---

## Why this isn't scripted

The DHU is an interactive REPL that needs a live terminal, and projection requires the **full**
Android Auto app (not the `-stub` shipped on emulator images) plus a completed on-device Android
Auto setup with a signed-in Google account. On a headless CI/agent emulator none of that is
available, so this test stays manual. The car's unlock logic itself
(`GarageScreen` → `UnlockConfigStore.getShortCode()` → `AltaUnlockClient.unlockDoor()` →
`formatUnlockResult()`) is identical to the phone's unlock button, so day-to-day regressions in the
unlock/error/no-clear behavior can be caught there without the DHU.
