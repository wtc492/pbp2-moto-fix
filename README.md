# Bud Fix

A tiny Android app that does one thing: notices when your earbuds show "Connected" but aren't actually playing audio, and fixes it by restarting Bluetooth (or asks you first).

No internet permission, no analytics, no accounts. Everything stays on the phone.

## How it works

1. When the buds connect (or their media connection changes), Android wakes the app.
2. It waits 8 seconds so a normal connection can finish.
3. It checks three things:
   - **Link**: is the base Bluetooth connection up? This is what makes Settings say "Connected".
   - **Media profile (A2DP)** and **calls profile (HFP)**: are they connected?
   - **Audio route**: does Android's audio system actually list the buds as a media output?
4. If the link is up but there's no audio route, that's the stuck state. Depending on your setting, it either:
   - shows a notification with a **Restart Bluetooth** button (default), or
   - restarts Bluetooth on its own (at most twice per 10 minutes, then falls back to notifying).

After any restart, Bud Fix makes sure the buds actually come back:

1. It waits 10 seconds for the phone to reconnect them on its own.
2. If they're not back, it asks Android to connect them (up to 3 tries). If they connect but audio stays on the phone, it points media audio at the buds.
3. If Android blocks those requests (some phones only allow them for system apps), the optional **tap fallback** opens Bluetooth settings, taps the buds, and goes back, the same as you would by hand.
4. You get a notification either way: fixed, or didn't reconnect.

There's also a **Fix buds** Quick Settings tile for a one-tap restart, any time.

## Build it

**Option A: phone only (GitHub Actions)**
1. Get this folder into a private GitHub repo with its folder structure intact. Phone browsers and the GitHub app can't upload a folder tree, so either push it with git from a computer, or create an empty repo and have Claude push it for you.
2. Open the repo's **Actions** tab. The "Build APK" workflow runs on every push; you can also start it with "Run workflow".
3. When it finishes, open the run and download the **budfix-apk** artifact (a zip containing `app-debug.apk`).
4. Unzip it on the phone and install. Android will ask you to allow installs from your browser or file manager.

**Option B: Android Studio**
Open this folder in Android Studio, let it sync, then Run onto your phone (USB or wireless debugging).

## Set it up

1. Open Bud Fix and allow **Nearby devices** (needed to see the buds and restart Bluetooth). Allow notifications too.
2. Pick your Pixel Buds Pro 2 from the list.
3. Optional: turn on automatic restarts.
4. Optional, recommended: turn on **Tap the buds in Bluetooth settings for me**, then enable **Bud Fix reconnect tapper** in Accessibility settings. If Android says it's a "restricted setting", open Settings > Apps > Bud Fix, tap the ⋮ menu, choose **Allow restricted settings**, and try again.
5. Add the tile: pull down Quick Settings, tap the edit (pencil) button, and drag **Fix buds** in.

## Check that the detection matches your bug

The stuck-state rule is an educated guess. Before trusting auto-fix, leave it on notify-only and look at the log after the next time it happens. You'll see lines like:

```
10-31 14:32:10  [connected] link=up media=on calls=on audioRoute=yes
10-31 18:05:44  [connected] link=up media=off calls=on audioRoute=NO  <- STUCK
```

- If a stuck episode shows `audioRoute=NO`, detection works.
- If a stuck episode looks identical to a good one, the phone is reporting everything as fine and this method can't see the bug. Let me know what the log shows and the check can be adjusted.
- `link=up(inferred)` means the phone wouldn't report the link directly, so the app is using the last connect/disconnect event it saw instead.

## Tradeoffs and limitations

- **Old target SDK on purpose.** Since Android 13, apps that target Android 13+ can't turn Bluetooth off and on, and the hidden "connect this device" calls are only reachable by apps targeting Android 11 (SDK 30) or lower. So this app targets 30. Side effects: Android may warn that the app was "built for an older version of Android" at install, and Play Protect may ask you to confirm the install. It also means it can never go on the Play Store, which doesn't matter for personal use.
- **Could stop working.** A future Android or Motorola update could block old-target apps from restarting Bluetooth. The log will say "Restart refused" if that happens; the notification and detection still work.
- **Reconnect requests may be blocked.** The log shows each attempt as "accepted" or "blocked". If they're blocked on your phone, the tap fallback is what reconnects the buds.
- **The tap fallback is brittle and needs the phone unlocked.** It looks for the buds by name in Bluetooth settings, so a Settings redesign could break it. While it runs, your screen briefly switches to Bluetooth settings and back. It can only see the Settings app.
- **A restart drops every Bluetooth connection** for a few seconds (watch, car, etc.). That's why auto-fix is off by default and rate-limited.
- **It only checks after connection events and when you tap Check now.** If audio silently stops mid-session without any connection change, it won't notice.
- **Not tested on your phone.** It was written without access to a device or the Android SDK, so the first build may need small fixes.
