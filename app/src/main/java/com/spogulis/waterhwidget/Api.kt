package com.spogulis.waterhwidget

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder

/** Talks to the waterh_to_garmin Flask service over the WireGuard tunnel. */
object Api {
    class ApiException(message: String) : Exception(message)

    fun fetchStatus(server: Config.Server): Config.Status {
        val body = get("${server.baseUrl}/status?key=${enc(server.key)}")
        val json = JSONObject(body)
        if (json.has("error")) throw ApiException(json.getString("error"))
        return Config.Status(
            date = json.optString("date", ""),
            intakeMl = json.optInt("intake_ml", 0),
            goalMl = json.optInt("goal_ml", 0),
            goalBaseMl = json.optInt("goal_base_ml", 0),
            sweatLossMl = json.optInt("sweat_loss_ml", 0),
            percent = json.optInt("percent", 0),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /** Triggers a WaterH → Garmin sync; throws on failure. */
    fun runSync(server: Config.Server) {
        val body = get("${server.baseUrl}/sync?key=${enc(server.key)}&format=json")
        val json = JSONObject(body)
        if (!json.optBoolean("ok", false)) {
            throw ApiException(json.optJSONArray("result")?.optString(0) ?: "sync failed")
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String {
        val parsed = try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw ApiException("Server URL must start with http:// or https://")
        }
        val conn = parsed.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            // /sync logs in to WaterH and writes to Garmin; give it time.
            conn.readTimeout = 60_000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299 && !body.trimStart().startsWith("{")) {
                throw ApiException("HTTP $code: ${body.take(80)}")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }
}
