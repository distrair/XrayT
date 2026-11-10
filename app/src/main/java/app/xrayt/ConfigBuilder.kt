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
        val keep = JSONArray()
        keep.put(tunInbound())
        cfg.put("inbounds", keep)
        cfg.remove("inbound"); cfg.remove("inboundDetour") // legacy v2ray keys

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
