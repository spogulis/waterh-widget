package com.spogulis.waterhwidget

import android.content.Context

/** Server settings + last-fetched status, stored in plain SharedPreferences. */
object Config {
    private const val PREFS = "waterh_widget"

    data class Server(val baseUrl: String, val key: String)

    data class Status(
        val date: String,
        val intakeMl: Int,
        val goalMl: Int,
        val goalBaseMl: Int,
        val sweatLossMl: Int,
        val percent: Int,
        val fetchedAt: Long,
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Trim, drop trailing slashes, and default to http:// when no scheme given. */
    fun normalizeUrl(raw: String): String {
        val u = raw.trim().trimEnd('/')
        return if (u.isEmpty() || u.contains("://")) u else "http://$u"
    }

    fun loadServer(ctx: Context): Server? {
        val p = prefs(ctx)
        val url = normalizeUrl(p.getString("baseUrl", "")!!)
        val key = p.getString("key", "")!!.trim()
        return if (url.isEmpty() || key.isEmpty()) null else Server(url, key)
    }

    fun saveServer(ctx: Context, baseUrl: String, key: String) {
        prefs(ctx).edit()
            .putString("baseUrl", normalizeUrl(baseUrl))
            .putString("key", key.trim())
            .apply()
    }

    // --- "open WaterH app when syncing" option -------------------------------

    const val DEFAULT_WATERH_PACKAGE = "com.waterh"
    const val DEFAULT_SYNC_DELAY_SEC = 40

    fun openWaterhEnabled(ctx: Context) = prefs(ctx).getBoolean("openWaterh", false)

    fun waterhPackage(ctx: Context): String =
        prefs(ctx).getString("waterhPackage", "")!!.trim().ifEmpty { DEFAULT_WATERH_PACKAGE }

    fun syncDelaySec(ctx: Context): Int = prefs(ctx).getInt("syncDelaySec", DEFAULT_SYNC_DELAY_SEC)

    fun saveOpenWaterh(ctx: Context, enabled: Boolean, pkg: String, delaySec: Int) {
        prefs(ctx).edit()
            .putBoolean("openWaterh", enabled)
            .putString("waterhPackage", pkg.trim())
            .putInt("syncDelaySec", delaySec.coerceIn(5, 600))
            .apply()
    }

    // --- coffee quick-add buttons --------------------------------------------

    enum class Coffee(val key: String, val defaultMl: Int) {
        BLACK("black", 200),
        WHITE("white", 250),
        CAPPUCCINO("capp", 150),
    }

    fun coffeeEnabled(ctx: Context, c: Coffee): Boolean =
        prefs(ctx).getBoolean("coffee_${c.key}_on", true)

    fun coffeeMl(ctx: Context, c: Coffee): Int =
        prefs(ctx).getInt("coffee_${c.key}_ml", c.defaultMl)

    fun saveCoffee(ctx: Context, c: Coffee, enabled: Boolean, ml: Int) {
        prefs(ctx).edit()
            .putBoolean("coffee_${c.key}_on", enabled)
            .putInt("coffee_${c.key}_ml", ml.coerceIn(1, 2000))
            .apply()
    }

    fun saveStatus(ctx: Context, s: Status) {
        prefs(ctx).edit()
            .putString("st_date", s.date)
            .putInt("st_intake", s.intakeMl)
            .putInt("st_goal", s.goalMl)
            .putInt("st_goal_base", s.goalBaseMl)
            .putInt("st_sweat", s.sweatLossMl)
            .putInt("st_percent", s.percent)
            .putLong("st_fetched", s.fetchedAt)
            .remove("st_error")
            .apply()
    }

    fun loadStatus(ctx: Context): Status? {
        val p = prefs(ctx)
        val date = p.getString("st_date", null) ?: return null
        return Status(
            date = date,
            intakeMl = p.getInt("st_intake", 0),
            goalMl = p.getInt("st_goal", 0),
            goalBaseMl = p.getInt("st_goal_base", 0),
            sweatLossMl = p.getInt("st_sweat", 0),
            percent = p.getInt("st_percent", 0),
            fetchedAt = p.getLong("st_fetched", 0L),
        )
    }

    fun saveError(ctx: Context, message: String) {
        prefs(ctx).edit().putString("st_error", message.take(120)).apply()
    }

    fun lastError(ctx: Context): String? = prefs(ctx).getString("st_error", null)
}
