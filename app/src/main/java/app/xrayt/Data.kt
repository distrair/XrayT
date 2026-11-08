package app.xrayt

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AppMode { PROXY_SELECTED, BYPASS_SELECTED }

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val hwid: String = "",            // sent as "x-hwid" header
    val userAgent: String = "",       // custom User-Agent
    val extraHeaders: String = "",    // "Name: value" per line (the "specific request" part)
    val replaceInbounds: Boolean = true,
    val configs: List<String> = emptyList(),
    val updatedAt: Long = 0
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("url", url)
        .put("hwid", hwid).put("ua", userAgent).put("hdr", extraHeaders)
        .put("replace", replaceInbounds).put("configs", JSONArray(configs)).put("t", updatedAt)

    companion object {
        fun fromJson(o: JSONObject): Subscription {
            val a = o.getJSONArray("configs")
            return Subscription(
                o.getString("id"), o.optString("name"), o.optString("url"), o.optString("hwid"),
                o.optString("ua"), o.optString("hdr"), o.optBoolean("replace", true),
                List(a.length()) { a.getString(it) }, o.optLong("t")
            )
        }
    }
}

fun configName(raw: String): String =
    try { JSONObject(raw).optString("remarks").ifBlank { "Config" } } catch (e: Exception) { "Config" }

class Store(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("xray", Context.MODE_PRIVATE)

    fun loadSubs(): List<Subscription> {
        val a = JSONArray(sp.getString("subs", "[]"))
        return List(a.length()) { Subscription.fromJson(a.getJSONObject(it)) }
    }
    fun saveSubs(l: List<Subscription>) =
        sp.edit().putString("subs", JSONArray(l.map { it.toJson() }).toString()).apply()

    var mode: AppMode
        get() = AppMode.valueOf(sp.getString("mode", AppMode.PROXY_SELECTED.name)!!)
        set(v) = sp.edit().putString("mode", v.name).apply()
    var apps: Set<String>
        get() = sp.getStringSet("apps", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("apps", v).apply()
    var activeSub: String?
        get() = sp.getString("activeSub", null)
        set(v) = sp.edit().putString("activeSub", v).apply()
    var activeIdx: Int
        get() = sp.getInt("activeIdx", 0)
        set(v) = sp.edit().putInt("activeIdx", v).apply()
    var geoAuto: Boolean
        get() = sp.getBoolean("geoAuto", true)
        set(v) = sp.edit().putBoolean("geoAuto", v).apply()
    var geoUpdated: Long
        get() = sp.getLong("geoUpdated", 0)
        set(v) = sp.edit().putLong("geoUpdated", v).apply()
}
