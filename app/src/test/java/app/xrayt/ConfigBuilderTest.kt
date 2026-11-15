package app.xrayt

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConfigBuilderTest {

    @Test
    fun stripsPortListeningInboundsAndAddsTun() {
        val raw = JSONObject().apply {
            put("inbounds", JSONArray().apply {
                put(JSONObject("""{"protocol":"socks","port":10808,"listen":"127.0.0.1"}"""))
                put(JSONObject("""{"protocol":"http","port":10809,"listen":"127.0.0.1"}"""))
            })
            put("outbounds", JSONArray().apply {
                put(JSONObject("""{"protocol":"vless","tag":"proxy"}"""))
            })
        }.toString()

        val built = ConfigBuilder.build(raw, tunFd = 42, datDir = "/data/user/0/app.xrayt/files")
        val json = JSONObject(built)

        val inbounds = json.getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        val tun = inbounds.getJSONObject(0)
        assertEquals("tun", tun.getString("protocol"))
        assertEquals("tun-in", tun.getString("tag"))
        assertEquals("xray0", tun.getJSONObject("settings").getString("name"))

        val env = json.getJSONObject("env")
        assertEquals("42", env.getString("xray.tun.fd"))
        assertEquals("42", env.getString("XRAY_TUN_FD"))
        assertEquals("/data/user/0/app.xrayt/files", env.getString("xray.location.asset"))
    }

    @Test
    fun rewritesInboundTagInRoutingRules() {
        val raw = JSONObject().apply {
            put("inbounds", JSONArray().apply {
                put(JSONObject("""{"protocol":"socks","tag":"socks-in","port":10808}"""))
            })
            put("routing", JSONObject().apply {
                put("rules", JSONArray().apply {
                    put(JSONObject("""{"inboundTag":["socks-in"],"outboundTag":"direct"}"""))
                })
            })
        }.toString()

        val built = ConfigBuilder.build(raw, tunFd = 10, datDir = "/tmp")
        val json = JSONObject(built)
        val rules = json.getJSONObject("routing").getJSONArray("rules")
        val rule = rules.getJSONObject(0)
        val inboundTags = rule.getJSONArray("inboundTag")
        assertEquals(1, inboundTags.length())
        assertEquals("tun-in", inboundTags.getString(0))
    }
}
