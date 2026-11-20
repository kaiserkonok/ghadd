#!/usr/bin/env python3
"""Downloads the content bundled into the app. Run once (or to refresh), then rebuild.

  1. Porn domain blocklists  -> app/src/main/assets/blocklist.txt
  2. Verses (Arabic + Sahih International) -> app/src/main/assets/verses.json
  3. Recitation audio (Mishary Alafasy) -> app/src/main/res/raw/v_SSS_AAA.mp3

Verse selection: have someone knowledgeable review VERSES below before you release.
Sources: StevenBlack/hosts, Sinfonietta/hostfiles, api.alquran.cloud, everyayah.com.
"""
import json
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
RAW = ROOT / "app/src/main/res/raw"

BLOCKLISTS = [
    "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
    "https://raw.githubusercontent.com/Sinfonietta/hostfiles/master/pornography-hosts",
]

# (surah, ayah) - lowering the gaze, nearing zina, Allah sees, accountability, repentance
VERSES = [
    (24, 30), (17, 32), (96, 14), (40, 19), (99, 7), (99, 8),
    (50, 16), (3, 135), (39, 53),
]


def get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "ghadd-fetch"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def fetch_blocklist() -> None:
    domains: set[str] = set()
    for url in BLOCKLISTS:
        try:
            text = get(url).decode("utf-8", "ignore")
        except Exception as e:  # noqa: BLE001
            print(f"  ! skipped {url}: {e}")
            continue
        before = len(domains)
        for line in text.splitlines():
            line = line.split("#", 1)[0].strip()
            parts = line.split()
            if len(parts) >= 2 and parts[0] in ("0.0.0.0", "127.0.0.1"):
                d = parts[1].lower()
                if re.fullmatch(r"[a-z0-9.-]+\.[a-z]{2,}", d):
                    domains.add(d)
        print(f"  {url.split('/')[-4]}: +{len(domains) - before}")
    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "blocklist.txt").write_text("\n".join(sorted(domains)) + "\n")
    print(f"blocklist.txt: {len(domains)} domains")


def fetch_verses() -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    RAW.mkdir(parents=True, exist_ok=True)
    out = []
    for s, a in VERSES:
        api = f"https://api.alquran.cloud/v1/ayah/{s}:{a}/editions/quran-uthmani,en.sahih"
        data = json.loads(get(api))["data"]
        arabic = next(d["text"] for d in data if d["edition"]["identifier"] == "quran-uthmani")
        english = next(d["text"] for d in data if d["edition"]["identifier"] == "en.sahih")
        raw = f"v_{s:03d}_{a:03d}"
        mp3 = RAW / f"{raw}.mp3"
        if not mp3.exists():
            mp3.write_bytes(get(f"https://everyayah.com/data/Alafasy_128kbps/{s:03d}{a:03d}.mp3"))
        out.append({"ref": f"{s}:{a}", "arabic": arabic, "translation": english, "raw": raw})
        print(f"  {s}:{a} ok")
    (ASSETS / "verses.json").write_text(json.dumps(out, ensure_ascii=False, indent=2))
    print(f"verses.json: {len(out)} verses")


if __name__ == "__main__":
    print("Blocklists…")
    fetch_blocklist()
    print("Verses + audio…")
    fetch_verses()
