package app.xrayt

import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject

data class ProfilePing(
    val delayMs: Long?,
    val timedOut: Boolean = false,
    val error: String? = null
)

object XrayPinger {
    private const val MAX_BATCH_SIZE = 5
    private const val TIMEOUT_SECONDS = 5
    private const val TEST_URL = "https://www.gstatic.com/generate_204"

    /** Blocking; call from a background thread. Results keep the input profile order. */
    fun ping(configs: List<String>): List<ProfilePing> {
        return configs.chunked(MAX_BATCH_SIZE).flatMap { batch ->
            val parsed = batch.map { raw ->
                runCatching {
                    val config = JSONObject(raw)
                    val outbounds = config.getJSONArray("outbounds")
                    check(outbounds.length() > 0) { "No outbounds in profile" }
                    JSONObject().put("outbounds", outbounds).toString()
                }
            }

            val validConfigs = parsed.mapNotNull { it.getOrNull() }
            val pingResults = if (validConfigs.isNotEmpty()) {
                val request = JSONObject().apply {
                    put("apiVersion", LibXray.LibXrayAPIVersion)
                    put("method", "pingBatch")
                    put("payload", JSONObject().apply {
                        put("configs", JSONArray().apply {
                            validConfigs.forEach { pingConfig ->
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
                check(results.length() == validConfigs.size) { "Xray returned incomplete ping results" }

                List(results.length()) { index ->
                    val result = results.getJSONObject(index)
                    val delay = result.optLong("delay", -1L)
                    if (result.optBoolean("success") && delay >= 0L) {
                        ProfilePing(delayMs = delay)
                    } else {
                        ProfilePing(delayMs = null, timedOut = delay == 11000L)
                    }
                }
            } else {
                emptyList()
            }

            var validIndex = 0
            parsed.map { res ->
                if (res.isSuccess) {
                    pingResults[validIndex++]
                } else {
                    ProfilePing(delayMs = null, error = "Invalid")
                }
            }
        }
    }
}
