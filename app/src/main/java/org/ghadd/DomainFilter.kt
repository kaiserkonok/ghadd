package org.ghadd

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer

/**
 * Decides what to do with a DNS name.
 *
 *  PORN   - blocked, and the Quran screen is shown.
 *  BYPASS - blocked silently: encrypted-DNS services that browsers/apps use to skip our filter.
 *  ALLOW  - looked up normally.
 *
 * Porn rules: (1) bundled list of >1M domains from several blocklists (matches the domain and
 * any parent domain), (2) strong keywords incl. desi/Bangla terms, (3) adult TLDs.
 * The list is stored as sorted 64-bit hashes so it needs ~9 MB of memory instead of ~100 MB.
 */
object DomainFilter {
    private const val TAG = "DomainFilter"

    enum class Verdict { ALLOW, PORN, BYPASS }

    @Volatile
    private var hashes: LongArray = LongArray(0)

    val size: Int get() = hashes.size

    private val keywords = listOf(
        "porn", "xxx", "xvideo", "xnxx", "xhamster", "redtube", "youporn", "spankbang", "eporner",
        "hentai", "brazzers", "nsfw", "milf", "nudes", "onlyfans", "fansly", "chaturbate", "stripchat",
        "camgirl", "sexcam", "sextube", "sexvid", "sexyvid", "sexvideo", "sexyvideo", "sexstor",
        "rule34", "fapello", "fapping", "nhentai", "jav-", "javhd",
        // desi / Bangla / Hindi terms
        "chudai", "chodachudi", "choda-", "antarvasna", "desisex", "desixxx", "xxxdesi", "masahub",
        "bhabhisex", "kamababa", "aagmaal", "mmsbee", "fsiblog"
    )
    private val adultTlds = listOf(".xxx", ".porn", ".adult", ".sex", ".sexy")

    /** Encrypted DNS (DoH/DoT) endpoints. Blocking their names makes apps fall back to us. */
    private val bypass = setOf(
        "use-application-dns.net", // Firefox canary: "does not exist" = turn DoH off
        "dns.google", "dns.google.com", "8888.google", "cloudflare-dns.com", "one.one.one.one",
        "dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net",
        "doh.opendns.com", "doh.familyshield.opendns.com", "dns.adguard.com", "dns.adguard-dns.com",
        "dns.nextdns.io", "doh.cleanbrowsing.org", "doh.dns.sb", "dns.alidns.com", "doh.pub",
        "dns.mullvad.net", "doh.mullvad.net", "freedns.controld.com", "dns.controld.com",
        "doh.libredns.gr", "dns.twnic.tw", "doh.360.cn", "dns.switch.ch"
    )

    @Synchronized
    fun load(ctx: Context) {
        if (hashes.isNotEmpty()) return
        try {
            val bytes = ctx.assets.open("blocklist.bin").use { it.readBytes() }
            val arr = LongArray(bytes.size / 8)
            ByteBuffer.wrap(bytes).asLongBuffer().get(arr) // big-endian, already sorted
            hashes = arr
            Log.i(TAG, "Loaded ${arr.size} blocked domains")
        } catch (e: Exception) {
            Log.w(TAG, "No blocklist.bin in assets - keyword and TLD rules only", e)
        }
    }

    fun verdict(rawName: String): Verdict {
        val name = rawName.trim().trimEnd('.').lowercase()
        if (name.isEmpty()) return Verdict.ALLOW

        // Walk the name and its parents: a.b.example.com -> b.example.com -> example.com
        val set = hashes
        var d = name
        while (true) {
            if (d in bypass) return Verdict.BYPASS
            if (set.isNotEmpty() && java.util.Arrays.binarySearch(set, hash(d)) >= 0) return Verdict.PORN
            val dot = d.indexOf('.')
            if (dot < 0) break
            d = d.substring(dot + 1)
        }

        if (adultTlds.any { name.endsWith(it) }) return Verdict.PORN
        if (keywords.any { name.contains(it) }) return Verdict.PORN
        return Verdict.ALLOW
    }

    fun isBlocked(rawName: String): Boolean = verdict(rawName) == Verdict.PORN

    /** FNV-1a 64-bit over UTF-8 bytes. Must match fnv1a64() in tools/fetch_content.py. */
    internal fun hash(s: String): Long {
        var h = -3750763034362895579L // 0xcbf29ce484222325
        for (b in s.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xFF)
            h *= 0x100000001b3L
        }
        return h
    }
}
