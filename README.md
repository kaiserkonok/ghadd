# Ghadd

*Ghadd* (غضّ) means to lower or restrain, most famously in *ghadd al-basar*, lowering the gaze (Qur'an 24:30).

An open-source Android app to help you guard yourself from lust and build self-control.
Everything runs on your phone. No accounts, no server, no analytics, no cost.

## What it does

- **Porn filter** – a local, on-device DNS filter (Android `VpnService`). It blocks porn sites in every
  browser and app, including incognito. When a blocked site is hit, Quran recitation plays with a
  reminder. There is no unblock option and no off switch in the app.
- **App blocking** – pick apps to block. Opening one (or trying to remove it from the list) starts the
  unblock flow: Quran plays, then you must choose either
  - *"I'm choosing my desire over Allah's rule"* – a long wait, logged, streak resets; or
  - *"I genuinely need it"* – type the pledge *"I'm not going to look at any haram things, Allah sees me."*,
    wait, then pick a limited time (15/30/60 min) after which it blocks again.
- **Streak and log** – days since you last chose desire, sites blocked, recent unblocks.

## Build

Needs JDK 17 and the Android SDK (platform 34).

```sh
python3 tools/fetch_content.py   # downloads blocklists, verses and audio into the app
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
```

Install the APK on your phone (`adb install -r app/build/outputs/apk/debug/app-debug.apk`), then follow
the three steps on the home screen.

## How the filter works

The app creates a VPN that only claims a fake DNS server address. Phone DNS lookups come to the app,
which answers "does not exist" for porn domains and forwards everything else to a real DNS server.
No traffic is routed through anyone. Well-known public DNS / DoH addresses are routed into the tunnel
and dropped so apps can't bypass the filter. For best results enable *Always-on VPN* and
*Block connections without VPN* for Ghadd in Android's VPN settings.

## Notes

- Verse selection lives in `tools/fetch_content.py`; please have it reviewed by someone knowledgeable.
- Audio: Mishary Alafasy via everyayah.com. Translation: Sahih International via api.alquran.cloud.
- Blocklists: StevenBlack/hosts and Sinfonietta/hostfiles.
