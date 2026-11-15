package app.xrayt

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DataTest {

    @Test
    fun subscriptionRoundTripSerialization() {
        val sub = Subscription(
            id = "test-id-123",
            name = "My Server",
            url = "https://example.com/sub",
            hwid = "my-hwid-abc",
            userAgent = "CustomUA/1.0",
            extraHeaders = "Authorization: Bearer token123",
            configs = listOf("""{"remarks":"Server 1"}""", """{"remarks":"Server 2"}"""),
            updatedAt = 1700000000000L
        )

        val json = sub.toJson()
        val restored = Subscription.fromJson(json)

        assertNotNull(restored)
        assertEquals("test-id-123", restored!!.id)
        assertEquals("My Server", restored.name)
        assertEquals("https://example.com/sub", restored.url)
        assertEquals("my-hwid-abc", restored.hwid)
        assertEquals("CustomUA/1.0", restored.userAgent)
        assertEquals("Authorization: Bearer token123", restored.extraHeaders)
        assertEquals(2, restored.configs.size)
        assertEquals(1700000000000L, restored.updatedAt)
    }

    @Test
    fun subscriptionFromJsonRecoversFromCorruptedData() {
        val corrupted = JSONObject().apply {
            put("id", "")
            put("configs", JSONArray().apply {
                put("valid-config")
                put("") // empty config
            })
        }

        val restored = Subscription.fromJson(corrupted)
        assertNotNull(restored)
        assertTrue(restored!!.id.isNotBlank()) // Auto-generates a UUID
        assertEquals(1, restored.configs.size)
        assertEquals("valid-config", restored.configs[0])
    }

    @Test
    fun configNameExtractsRemarksOrDefault() {
        assertEquals("Server Alpha", configName("""{"remarks":"Server Alpha"}"""))
        assertEquals("Config", configName("""{"remarks":""}"""))
        assertEquals("Config", configName("not valid json"))
    }
}
