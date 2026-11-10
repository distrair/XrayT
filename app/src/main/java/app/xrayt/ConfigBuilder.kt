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
        val replacedInboundTags = collectInboundTags(cfg)
        val keep = JSONArray()
        keep.put(tunInbound())
        cfg.put("inbounds", keep)
        cfg.remove("inbound"); cfg.remove("inboundDetour") // legacy v2ray keys
        rewriteInboundTags(cfg, replacedInboundTags)

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

    private fun collectInboundTags(cfg: JSONObject): Set<String> = buildSet {
        cfg.optJSONArray("inbounds")?.let { inbounds ->
            for (i in 0 until inbounds.length()) inbounds.optJSONObject(i)?.optString("tag")
                ?.takeIf(String::isNotBlank)?.let(::add)
        }
        cfg.optJSONObject("inbound")?.optString("tag")
            ?.takeIf(String::isNotBlank)?.let(::add)
        when (val legacy = cfg.opt("inboundDetour")) {
            is JSONObject -> legacy.optString("tag").takeIf(String::isNotBlank)?.let(::add)
            is JSONArray -> for (i in 0 until legacy.length()) {
                legacy.optJSONObject(i)?.optString("tag")
                    ?.takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun rewriteInboundTags(cfg: JSONObject, replacedTags: Set<String>) {
        if (replacedTags.isEmpty()) return
        val rules = cfg.optJSONObject("routing")?.optJSONArray("rules") ?: return
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val inboundTags = rule.optJSONArray("inboundTag") ?: continue
            val rewritten = JSONArray()
            val seen = mutableSetOf<String>()
            for (j in 0 until inboundTags.length()) {
                val tag = inboundTags.optString(j)
                val newTag = if (tag in replacedTags) "tun-in" else tag
                if (seen.add(newTag)) rewritten.put(newTag)
            }
            rule.put("inboundTag", rewritten)
        }
    }
}
