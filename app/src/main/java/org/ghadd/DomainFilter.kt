package org.ghadd

import android.content.Context
import android.util.Log

/**
 * Decides whether a DNS name belongs to a porn site.
 *
 * Layers: (1) bundled blocklist in assets/blocklist.txt (matches the domain and any parent
 * domain), (2) a few strong keywords, (3) adult TLDs. Everything is local.
 */
object DomainFilter {
    private const val TAG = "DomainFilter"

    @Volatile
    private var domains: HashSet<String> = HashSet()

    private val keywords = listOf(
        "porn", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "hentai", "brazzers"
    )
    private val adultTlds = listOf(".xxx", ".porn", ".adult", ".sex")

    @Synchronized
    fun load(ctx: Context) {
        if (domains.isNotEmpty()) return
        val set = HashSet<String>(200_000)
        try {
            ctx.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val d = line.trim().lowercase()
                    if (d.isNotEmpty() && !d.startsWith("#")) set.add(d)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No blocklist.txt in assets - keyword and TLD rules only", e)
        }
        domains = set
        Log.i(TAG, "Loaded ${set.size} blocked domains")
    }

    fun isBlocked(rawName: String): Boolean {
        val name = rawName.trim().trimEnd('.').lowercase()
        if (name.isEmpty()) return false

        // exact domain or any parent domain: a.b.example.com -> b.example.com -> example.com
        var d = name
        val set = domains
        while (true) {
            if (set.contains(d)) return true
            val dot = d.indexOf('.')
            if (dot < 0) break
            d = d.substring(dot + 1)
        }

        if (adultTlds.any { name.endsWith(it) }) return true
        return keywords.any { name.contains(it) }
    }
}
