package app.xrayt

import org.json.JSONArray
import org.json.JSONObject

object ConfigBuilder {
    private fun tunInbound() = JSONObject(
        """{"tag":"tun-in","protocol":"tun","settings":{"name":"xray0"},
            "sniffing":{"enabled":true,"destOverride":["http","tls","quic"]}}"""
    )

    /** Imported inbounds are always dropped; only our TUN inbound remains. */
    fun build(raw: String, tunFd: Int = -1, datDir: String = ""): String {
        val cfg = JSONObject(raw)

        // Collect old inbound tags so we can rewrite routing rules.
        val oldTags = mutableSetOf<String>()
        cfg.optJSONArray("inbounds")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("tag")?.takeIf { it.isNotEmpty() }?.let(oldTags::add)
            }
        }

        val keep = JSONArray()
        keep.put(tunInbound())
        cfg.put("inbounds", keep)
        cfg.remove("inbound"); cfg.remove("inboundDetour") // legacy v2ray keys

        // Rewrite routing rules that referenced removed inbound tags.
        if (oldTags.isNotEmpty()) {
            cfg.optJSONObject("routing")?.optJSONArray("rules")?.let { rules ->
                for (i in 0 until rules.length()) {
                    val rule = rules.optJSONObject(i) ?: continue
                    val tags = rule.optJSONArray("inboundTag") ?: continue
                    val rewritten = JSONArray()
                    for (j in 0 until tags.length()) {
                        val t = tags.optString(j)
                        rewritten.put(if (t in oldTags) "tun-in" else t)
                    }
                    rule.put("inboundTag", rewritten)
                }
            }
        }

        val env = cfg.optJSONObject("env") ?: JSONObject().also { cfg.put("env", it) }
        if (tunFd >= 0) {
            env.put("xray.tun.fd", tunFd.toString())
            env.put("XRAY_TUN_FD", tunFd.toString())
        }
        if (datDir.isNotBlank()) {
            env.put("xray.location.asset", datDir)
            env.put("XRAY_LOCATION_ASSET", datDir)
        }

        return cfg.toString()
    }
}
