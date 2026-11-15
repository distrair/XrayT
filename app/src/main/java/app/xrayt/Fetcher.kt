package app.xrayt

import android.util.Base64
import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Fetcher {
    const val MAX_BODY_BYTES = 8 * 1024 * 1024 // 8 MB

    val defaultUserAgent: String
        get() = "v2rayNG/${BuildConfig.VERSION_NAME.removePrefix("v")}"

    /** Blocking; call from a background thread. Returns raw Xray JSON configs. */
    fun fetch(s: Subscription): List<String> {
        val url = runCatching { URL(s.url) }.getOrElse { error("Invalid subscription URL: ${s.url}") }
        val proto = url.protocol.lowercase()
        if (proto !in listOf("https", "http")) {
            error("Unsupported URL protocol '$proto'. Only HTTPS and HTTP are supported.")
        }
        val c = url.openConnection() as HttpURLConnection
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
            val body = readBodyWithLimit(c, MAX_BODY_BYTES)
            return parse(body)
        } finally { c.disconnect() }
    }

    private fun readBodyWithLimit(c: HttpURLConnection, maxBytes: Int): String {
        val stream = c.inputStream
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) {
                error("Subscription response exceeded maximum size limit (${maxBytes / (1024 * 1024)}MB)")
            }
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    fun parse(body: String): List<String> {
        var text = body.trim().removePrefix("\uFEFF").trim()
        if (!isJson(text) && !isShareLinkList(text)) {
            val decoded = listOf(Base64.DEFAULT, Base64.URL_SAFE).firstNotNullOfOrNull { flags ->
                runCatching {
                    String(Base64.decode(text, flags), Charsets.UTF_8)
                        .trim().removePrefix("\uFEFF").trim()
                }.getOrNull()?.takeIf { isJson(it) || isShareLinkList(it) }
            }
            if (decoded != null) text = decoded
        }

        return when {
            text.startsWith("[") -> JSONArray(text).let { a ->
                List(a.length()) { a.getJSONObject(it).toString() }
            }
            text.startsWith("{") -> listOf(text)
            isShareLinkList(text) -> convertShareLinks(text)
            else -> error("Unsupported subscription format or no supported profiles found.")
        }
    }

    private fun isJson(text: String) = text.startsWith("{") || text.startsWith("[")

    private val shareLinkPattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*://.+$")

    private fun isShareLinkList(text: String): Boolean {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty)
            .filterNot { it.startsWith("#") }.toList()
        return lines.isNotEmpty() && lines.all(shareLinkPattern::matches)
    }

    /** Converts supported share links with libXray and stores one outbound per selectable profile. */
    private fun convertShareLinks(text: String): List<String> {
        val links = text.lineSequence().map(String::trim).filter(String::isNotEmpty)
            .filterNot { it.startsWith("#") }.joinToString("\n")
        val request = JSONObject().apply {
            put("apiVersion", LibXray.LibXrayAPIVersion)
            put("method", "convertShareLinksToXrayJson")
            put("payload", JSONObject().put("text", links))
        }
        val response = JSONObject(LibXray.invoke(request.toString()))
        check(response.optBoolean("success")) {
            response.optString("error").ifBlank { "No supported profiles found" }
        }
        val outbounds = response.optJSONObject("data")?.optJSONArray("outbounds")
            ?: error("Xray returned no profiles")
        check(outbounds.length() > 0) { "No supported profiles found" }

        return List(outbounds.length()) { index ->
            val outbound = outbounds.getJSONObject(index)
            val name = outbound.optString("tag").trim().ifBlank { "Profile ${index + 1}" }
            JSONObject()
                .put("remarks", name)
                .put("outbounds", JSONArray().put(outbound))
                .toString()
        }
    }
}
