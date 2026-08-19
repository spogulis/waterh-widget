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
