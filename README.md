<div align="center">

# غضّ Ghadd

**Guard your gaze. Build self-control.**

A free Android app that blocks porn on your whole phone and helps you stay away from the apps that pull you in, with Quran recitation as a reminder.

[![Latest release](https://img.shields.io/github/v/release/kaiserkonok/ghadd?style=for-the-badge&color=2e7d5b)](https://github.com/kaiserkonok/ghadd/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/kaiserkonok/ghadd/total?style=for-the-badge&color=2e7d5b)](https://github.com/kaiserkonok/ghadd/releases)
![Android 8+](https://img.shields.io/badge/Android-8.0%2B-2e7d5b?style=for-the-badge&logo=android&logoColor=white)

### [⬇ Download Ghadd for Android](https://github.com/kaiserkonok/ghadd/releases/latest/download/ghadd.apk)

No account · No ads · No tracking · Free forever

</div>

---

*Ghadd* (غضّ) means "to lower" or "to restrain", as in *ghadd al-basar*, lowering the gaze (Qur'an 24:30).

## Features

🛡️ **Porn filter for the whole phone**
Blocks over a million adult websites in every browser and app, including incognito mode. There is no off switch, so a weak moment can't undo it.

📖 **Quran instead of temptation**
When a blocked site is opened, Ghadd shows a verse with its translation and plays the recitation.

📵 **Block any app**
Choose the apps that lead you astray. Opening one plays Quran and asks you to pause and think. You have to be honest about why you want in:
- **"I'm choosing my desire over Allah's rule"**: a long wait, and your streak resets.
- **"I genuinely need it"**: type a short pledge, then wait.

Either way, the app opens for that one visit and locks again as soon as you leave it.

🔥 **Streak and history**
See how many days you've stayed clean, how many sites were blocked, and every time an app was unblocked.

🔒 **Private by design**
Everything happens on your phone. Ghadd has no servers and no accounts, and it never sends your browsing anywhere.

🔄 **Updates itself**
New versions and new blocked sites install automatically.

## Install

1. **[Download `ghadd.apk`](https://github.com/kaiserkonok/ghadd/releases/latest/download/ghadd.apk)** on your Android phone.
2. Open the downloaded file. If your phone asks, allow your browser to install apps.
3. Open **Ghadd** and follow the setup. It takes about two minutes and tells you exactly what to tap on each screen.

That's it. Ghadd keeps itself up to date from now on.

> **Accessibility switch greyed out?** Android 13 and newer restrict this for apps installed from a file. Ghadd's setup shows you how to allow it in a few taps.

## How it works

Ghadd uses Android's VPN feature to run a **local DNS filter**. When any app looks up a website, the lookup goes to Ghadd on your phone. Ghadd refuses adult domains and passes everything else to a normal DNS server. Your traffic itself never goes through Ghadd or anyone else.

Known ways to get around the filter, such as encrypted DNS services, are blocked too.

App blocking uses Android's accessibility service. Ghadd only sees **which app** is opened, never what's on the screen.

## Build from source

Requires JDK 17 and the Android SDK (platform 34).

```sh
git clone https://github.com/kaiserkonok/ghadd.git
cd ghadd
python3 tools/fetch_content.py   # downloads blocklists, verses and recitation audio
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
```

### Releasing

Bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit, then run:

```sh
tools/release.sh "What changed"
```

Installed copies update themselves from the latest GitHub Release. Every release must be signed with the same key.

## Contributing

Issues and pull requests are welcome, especially:
- **Blocked sites:** missing sites, or sites blocked by mistake
- **Phone support:** devices where Ghadd gets closed in the background
- **Translations** of the app and reminders

The verse selection lives in `tools/fetch_content.py`. Changes there should be reviewed by someone with knowledge of the Qur'an.

## Credits

- Recitation: Mishary Rashid Alafasy, via [everyayah.com](https://everyayah.com)
- Translation: Sahih International, via [alquran.cloud](https://alquran.cloud)
- Blocklists: [StevenBlack/hosts](https://github.com/StevenBlack/hosts) and [Sinfonietta/hostfiles](https://github.com/Sinfonietta/hostfiles)

<div align="center">

*"Tell the believing men to lower their gaze and guard their chastity. That is purer for them."* (Qur'an 24:30)

</div>
