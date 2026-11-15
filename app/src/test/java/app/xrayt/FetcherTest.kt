package app.xrayt

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class FetcherTest {

    @Test
    fun parseJsonArray() {
        val input = """[{"remarks":"Profile 1"},{"remarks":"Profile 2"}]"""
        val configs = Fetcher.parse(input)
        assertEquals(2, configs.size)
        assertEquals("Profile 1", configName(configs[0]))
        assertEquals("Profile 2", configName(configs[1]))
    }

    @Test
    fun parseSingleJsonObject() {
        val input = """{"remarks":"Standalone Profile"}"""
        val configs = Fetcher.parse(input)
        assertEquals(1, configs.size)
        assertEquals("Standalone Profile", configName(configs[0]))
    }

    @Test
    fun parseBase64Json() {
        val raw = """[{"remarks":"Encoded Profile"}]"""
        val base64 = Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
        val configs = Fetcher.parse(base64)
        assertEquals(1, configs.size)
        assertEquals("Encoded Profile", configName(configs[0]))
    }

    @Test
    fun parseUrlSafeBase64Json() {
        val raw = """[{"remarks":"UrlSafe Profile"}]"""
        val base64 = Base64.getUrlEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
        val configs = Fetcher.parse(base64)
        assertEquals(1, configs.size)
        assertEquals("UrlSafe Profile", configName(configs[0]))
    }

    @Test
    fun parseRejectsMalformedOrEmpty() {
        assertThrows(IllegalStateException::class.java) {
            Fetcher.parse("not valid json and not valid share links")
        }
    }
}
