package app.xrayt

import org.json.JSONArray
import org.json.JSONObject

object ConfigBuilder {
    private fun tunInbound() = JSONObject(
        """{"tag":"tun-in","protocol":"tun","settings":{"name":"xray0"},
            "sniffing":{"enabled":true,"destOverride":["http","tls","quic"]}}"""
    )

    /**
     * replace = true : every inbound of the imported config is dropped, only our TUN inbound remains.
     * replace = false: the config's own "tun" inbounds are kept untouched (custom settings preserved);
     *                  all port-listening inbounds (socks/http/mixed/dokodemo/...) are still removed,
     *                  and our TUN inbound is added if the config has none.
     */
    fun build(raw: String, replace: Boolean, tunFd: Int = -1, datDir: String = ""): String {
        val cfg = JSONObject(raw)
        val keep = JSONArray()
        if (!replace) {
            cfg.optJSONArray("inbounds")?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    if (o.optString("protocol") == "tun") keep.put(o)
                }
            }
        }
        if (keep.length() == 0) keep.put(tunInbound())
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
