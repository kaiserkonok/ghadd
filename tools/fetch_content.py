#!/usr/bin/env python3
"""Downloads the content bundled into the app. Run once (or to refresh), then rebuild.

  1. Porn domain blocklists  -> app/src/main/assets/blocklist.bin (sorted FNV-1a 64-bit hashes)
  2. Verses (Arabic + Sahih International) -> app/src/main/assets/verses.json
  3. Recitation audio (Mishary Alafasy) -> app/src/main/res/raw/v_SSS_AAA.mp3

Verse selection: have someone knowledgeable review VERSES below before you release.
Sources: StevenBlack/hosts, Sinfonietta/hostfiles, api.alquran.cloud, everyayah.com.
"""
import json
import re
import struct
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
RAW = ROOT / "app/src/main/res/raw"

BLOCKLISTS = [
    "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
    # Much broader coverage, including South Asian / desi sites the list above misses:
    "https://blocklistproject.github.io/Lists/porn.txt",
    "https://nsfw.oisd.nl/domainswild2",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/nsfw-onlydomains.txt",
]

# Never blocked, whatever a list says (protects against bad entries in third-party lists).
ALLOW = {
    "google.com", "youtube.com", "googlevideo.com", "ytimg.com", "gstatic.com", "googleapis.com",
    "facebook.com", "fbcdn.net", "instagram.com", "cdninstagram.com", "whatsapp.com", "whatsapp.net",
    "twitter.com", "x.com", "twimg.com", "reddit.com", "tiktok.com", "wikipedia.org", "bing.com",
    "microsoft.com", "github.com", "githubusercontent.com", "cloudflare.com", "cloudfront.net",
    "amazonaws.com", "akamaihd.net", "akamaized.net", "telegram.org", "imgur.com", "apple.com",
}

DOMAIN = re.compile(r"^[a-z0-9.-]+\.[a-z0-9-]{2,}$")


def fnv1a64(s: str) -> int:
    """Must match DomainFilter.hash() in the app. Returned as a signed 64-bit int."""
    h = 0xCBF29CE484222325
    for b in s.encode():
        h ^= b
        h = (h * 0x100000001B3) & 0xFFFFFFFFFFFFFFFF
    return h - (1 << 64) if h >= 1 << 63 else h

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
            line = line.split("#", 1)[0].strip().lower()
            if not line:
                continue
            parts = line.split()
            # hosts format ("0.0.0.0 domain") or plain/wildcard domain format
            d = parts[1] if len(parts) >= 2 and parts[0] in ("0.0.0.0", "127.0.0.1") else parts[0]
            d = d.lstrip("*.").strip("|^")
            if DOMAIN.match(d):
                domains.add(d)
        print(f"  {url}: +{len(domains) - before}")
    domains -= ALLOW
    # Shipped as sorted 64-bit hashes: ~9 MB in memory for >1M domains instead of ~100 MB.
    hashes = sorted({fnv1a64(d) for d in domains})
    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "blocklist.bin").write_bytes(struct.pack(f">{len(hashes)}q", *hashes))
    old = ASSETS / "blocklist.txt"
    if old.exists():
        old.unlink()
    print(f"blocklist.bin: {len(hashes)} domains")


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
    if "--blocklist-only" not in sys.argv:
        print("Verses + audio…")
        fetch_verses()
