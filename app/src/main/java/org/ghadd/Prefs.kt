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
        // People who set Ghadd up before the guided setup existed already have the filter on;
        // don't send them through it again. Decided once, so quitting mid-setup can't skip it.
        if (!sp.contains("setup_finished")) {
            sp.edit().putBoolean("setup_finished", filterWanted).apply()
        }
    }

    /** True once the user has turned the porn filter on; used to restart it after reboot/update. */
    var filterWanted: Boolean
        get() = sp.getBoolean("filter_wanted", false)
        set(value) {
            sp.edit().putBoolean("filter_wanted", value).apply()
        }

    /** True once the guided setup has been finished (or walked through). */
    var setupFinished: Boolean
        get() = sp.getBoolean("setup_finished", false)
        set(value) {
            sp.edit().putBoolean("setup_finished", value).apply()
        }

    /** Setup steps the user chose to skip during the guided setup. */
    val skippedSteps: Set<String>
        get() = sp.getStringSet("setup_skipped", emptySet())!!.toSet()

    fun skipStep(id: String) {
        sp.edit().putStringSet("setup_skipped", HashSet(skippedSteps + id)).apply()
    }

    /** Android only shows the notification prompt twice; after that we open settings instead. */
    var notificationsAsked: Int
        get() = sp.getInt("notif_asked", 0)
        set(value) {
            sp.edit().putInt("notif_asked", value).apply()
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

    // ---- unblock log --------------------------------------------------------------------

    /**
     * [minutes]: 0 = removed from the blocked list for good, [OPENED_ONCE] = opened for one
     * visit, anything else = an old timed unlock (from before v0.6).
     */
    data class Entry(val time: Long, val kind: String, val app: String, val minutes: Int)

    const val OPENED_ONCE = -1

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
