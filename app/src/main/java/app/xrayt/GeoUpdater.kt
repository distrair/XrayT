package app.xrayt

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object GeoUpdater {
    private const val BASE = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/"
    private val FILES = listOf("geoip.dat", "geosite.dat")

    /** Blocking; call from a background thread. Both files are replaced together or not at all. */
    @Synchronized
    fun update(ctx: Context) {
        val dir = ctx.filesDir
        val tmps = FILES.map { File(dir, "$it.tmp") }
        try {
            FILES.forEachIndexed { i, f ->
                val got = download(BASE + f, tmps[i])
                val want = expectedHash("$BASE$f.sha256sum") // best effort, skipped if not published
                if (want != null && want != got) error("Checksum mismatch for $f")
                if (tmps[i].length() < 100_000) error("$f looks too small, aborting")
            }
            FILES.forEachIndexed { i, f ->
                val dst = File(dir, f)
                if (!tmps[i].renameTo(dst)) tmps[i].copyTo(dst, overwrite = true)
            }
            Store(ctx).geoUpdated = System.currentTimeMillis()
        } finally {
            tmps.forEach { it.delete() }
        }
    }

    private fun download(url: String, out: File): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode} for $url")
            val md = MessageDigest.getInstance("SHA-256")
            c.inputStream.use { i ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = i.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n); md.update(buf, 0, n)
                    }
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        } finally { c.disconnect() }
    }

    private fun expectedHash(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 15_000
        try {
            if (c.responseCode !in 200..299) null
            else c.inputStream.bufferedReader().readText().trim().split(Regex("\\s+"))[0].lowercase()
        } finally { c.disconnect() }
    } catch (e: Exception) { null }

    fun schedule(ctx: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) { wm.cancelUniqueWork("geo"); return }
        val req = PeriodicWorkRequestBuilder<GeoWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork("geo", ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class GeoWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result = try {
        GeoUpdater.update(applicationContext); Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
