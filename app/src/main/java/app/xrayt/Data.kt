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
    val configs: List<String> = emptyList(),
    val updatedAt: Long = 0
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("url", url)
        .put("hwid", hwid).put("ua", userAgent).put("hdr", extraHeaders)
        .put("configs", JSONArray(configs)).put("t", updatedAt)

    companion object {
        fun fromJson(o: JSONObject): Subscription? {
            return try {
                val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
                val a = o.optJSONArray("configs") ?: JSONArray()
                val configs = (0 until a.length()).mapNotNull { idx ->
                    a.optString(idx)?.takeIf { it.isNotBlank() }
                }
                Subscription(
                    id = id,
                    name = o.optString("name"),
                    url = o.optString("url"),
                    hwid = o.optString("hwid"),
                    userAgent = o.optString("ua"),
                    extraHeaders = o.optString("hdr"),
                    configs = configs,
                    updatedAt = o.optLong("t", 0L)
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

fun configName(raw: String): String =
    try { JSONObject(raw).optString("remarks").ifBlank { "Config" } } catch (e: Exception) { "Config" }

class Store(ctx: Context) {
    companion object {
        const val SCHEMA_VERSION = 1
    }

    private val sp = ctx.applicationContext.getSharedPreferences("xray", Context.MODE_PRIVATE)

    fun loadSubs(): List<Subscription> {
        val raw = sp.getString("subs", "[]") ?: "[]"
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { idx ->
                val obj = a.optJSONObject(idx) ?: return@mapNotNull null
                Subscription.fromJson(obj)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSubs(l: List<Subscription>) {
        sp.edit()
            .putInt("schema_version", SCHEMA_VERSION)
            .putString("subs", JSONArray(l.map { it.toJson() }).toString())
            .apply()
    }

    var mode: AppMode
        get() = runCatching { AppMode.valueOf(sp.getString("mode", AppMode.BYPASS_SELECTED.name)!!) }
            .getOrDefault(AppMode.BYPASS_SELECTED)
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
