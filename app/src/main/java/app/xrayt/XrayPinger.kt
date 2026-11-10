package app.xrayt

import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject

data class ProfilePing(val delayMs: Long?, val timedOut: Boolean = false)

object XrayPinger {
    private const val MAX_BATCH_SIZE = 5
    private const val TIMEOUT_SECONDS = 5
    private const val TEST_URL = "https://www.gstatic.com/generate_204"

    /** Blocking; call from a background thread. Results keep the input profile order. */
    fun ping(configs: List<String>): List<ProfilePing> {
        return configs.chunked(MAX_BATCH_SIZE).flatMap { batch ->
            val request = JSONObject().apply {
                put("apiVersion", LibXray.LibXrayAPIVersion)
                put("method", "pingBatch")
                put("payload", JSONObject().apply {
                    put("configs", JSONArray().apply {
                        batch.forEach { raw ->
                            val pingConfig = runCatching {
                                val config = JSONObject(raw)
                                JSONObject().put("outbounds", config.getJSONArray("outbounds")).toString()
                            }.getOrElse { raw }
                            put(JSONObject().put("xrayJson", pingConfig))
                        }
                    })
                    put("timeout", TIMEOUT_SECONDS)
                    put("url", TEST_URL)
                })
            }

            val response = JSONObject(LibXray.invoke(request.toString()))
            check(response.optBoolean("success")) {
                response.optString("error").ifBlank { "Ping failed" }
            }
            val results = response.optJSONObject("data")?.optJSONArray("results")
                ?: error("Xray returned no ping results")
            check(results.length() == batch.size) { "Xray returned incomplete ping results" }

            List(results.length()) { index ->
                val result = results.getJSONObject(index)
                val delay = result.optLong("delay", -1L)
                if (result.optBoolean("success") && delay >= 0L) {
                    ProfilePing(delayMs = delay)
                } else {
                    ProfilePing(delayMs = null, timedOut = delay == 11000L)
                }
            }
        }
    }
}
