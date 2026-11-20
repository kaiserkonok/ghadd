package org.ghadd

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** All app state lives here, on the device only. Nothing is ever sent anywhere. */
object Prefs {
    private lateinit var sp: SharedPreferences

    const val KIND_DESIRE = "desire"
    const val KIND_NEED = "need"

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("ghadd", Context.MODE_PRIVATE)
        if (!sp.contains("first_run")) {
            sp.edit().putLong("first_run", System.currentTimeMillis()).apply()
        }
    }

    /** True once the user has turned the porn filter on; used to restart it after reboot/update. */
    var filterWanted: Boolean
        get() = sp.getBoolean("filter_wanted", false)
        set(value) {
            sp.edit().putBoolean("filter_wanted", value).apply()
        }

    /** Setup steps that Android can't verify (phone-maker screens): done once the user visited them. */
    fun setupDone(step: String): Boolean = sp.getBoolean("setup_$step", false)
    fun markSetupDone(step: String) {
        sp.edit().putBoolean("setup_$step", true).apply()
    }

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(value) {
            sp.edit().putLong("last_update_check", value).apply()
        }

    // ---- blocked apps -------------------------------------------------------------------

    var blockedApps: Set<String>
        get() = sp.getStringSet("blocked", emptySet())!!.toSet()
        set(value) {
            sp.edit().putStringSet("blocked", HashSet(value)).apply()
        }

    // ---- temporary unlocks --------------------------------------------------------------

    fun unlockUntil(pkg: String): Long = sp.getLong("unlock_$pkg", 0L)

    fun isUnlocked(pkg: String): Boolean = System.currentTimeMillis() < unlockUntil(pkg)

    fun unlock(pkg: String, minutes: Int) {
        sp.edit().putLong("unlock_$pkg", System.currentTimeMillis() + minutes * 60_000L).apply()
    }

    // ---- unblock log --------------------------------------------------------------------

    data class Entry(val time: Long, val kind: String, val app: String, val minutes: Int)

    /** [minutes] == 0 means the app was removed from the blocked list for good. */
    @Synchronized
    fun addLog(kind: String, app: String, minutes: Int) {
        val now = System.currentTimeMillis()
        val arr = JSONArray(sp.getString("log", "[]"))
        arr.put(
            JSONObject()
                .put("t", now)
                .put("k", kind)
                .put("a", app)
                .put("m", minutes)
        )
        // keep the newest 200 entries
        val trimmed = JSONArray()
        for (i in maxOf(0, arr.length() - 200) until arr.length()) trimmed.put(arr.get(i))
        val edit = sp.edit().putString("log", trimmed.toString())
        if (kind == KIND_DESIRE) edit.putLong("last_desire", now)
        edit.apply()
    }

    /** Newest first. */
    @Synchronized
    fun log(): List<Entry> {
        val arr = JSONArray(sp.getString("log", "[]"))
        val out = ArrayList<Entry>(arr.length())
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            out.add(Entry(o.getLong("t"), o.getString("k"), o.getString("a"), o.getInt("m")))
        }
        return out
    }

    /** Whole days since the last time you chose "desire" (or since install). */
    fun streakDays(): Int {
        val since = sp.getLong("last_desire", sp.getLong("first_run", System.currentTimeMillis()))
        return ((System.currentTimeMillis() - since) / 86_400_000L).toInt()
    }

    // ---- blocked-site counters ----------------------------------------------------------

    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return "sb_${c.get(Calendar.YEAR)}_${c.get(Calendar.DAY_OF_YEAR)}"
    }

    @Synchronized
    fun countSiteBlock() {
        val key = todayKey()
        sp.edit()
            .putInt("sb_total", sp.getInt("sb_total", 0) + 1)
            .putInt(key, sp.getInt(key, 0) + 1)
            .apply()
    }

    fun siteBlocksToday(): Int = sp.getInt(todayKey(), 0)

    fun siteBlocksTotal(): Int = sp.getInt("sb_total", 0)
}
