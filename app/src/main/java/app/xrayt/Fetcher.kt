package app.xrayt

import android.util.Base64
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object Fetcher {
    val defaultUserAgent: String
        get() = "v2rayNG/${BuildConfig.VERSION_NAME.removePrefix("v")}"

    /** Blocking; call from a background thread. Returns raw Xray JSON configs. */
    fun fetch(s: Subscription): List<String> {
        val c = URL(s.url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", s.userAgent.ifBlank { defaultUserAgent })
        if (s.hwid.isNotBlank()) c.setRequestProperty("x-hwid", s.hwid)
        s.extraHeaders.lines().forEach { l ->
            val i = l.indexOf(':')
            if (i > 0) c.setRequestProperty(l.substring(0, i).trim(), l.substring(i + 1).trim())
        }
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return parse(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }

    fun parse(body: String): List<String> {
        var t = body.trim()
        if (!t.startsWith("{") && !t.startsWith("[")) {
            t = try { String(Base64.decode(t, Base64.DEFAULT)).trim() } catch (e: Exception) { t }
        }
        return when {
            t.startsWith("[") -> JSONArray(t).let { a -> List(a.length()) { a.getJSONObject(it).toString() } }
            t.startsWith("{") -> listOf(t)
            else -> error("Unsupported subscription format (JSON configs only). Try another User-Agent.")
        }
    }
}
