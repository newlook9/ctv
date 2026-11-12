package com.livetv.app

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

// Remembers which stream links worked (positive time) or failed (negative time) on this device.
object Health {
    private val map = ConcurrentHashMap<String, Long>()
    private var file: File? = null
    private const val OK_TTL = 21L * 24 * 3600 * 1000
    private const val FAIL_TTL = 14L * 24 * 3600 * 1000
    private const val KEEP = 30L * 24 * 3600 * 1000

    fun load(ctx: Context) {
        val f = File(ctx.filesDir, "health.txt")
        file = f
        try {
            if (f.exists()) {
                f.forEachLine { line ->
                    val i = line.lastIndexOf(' ')
                    if (i > 0) {
                        val ts = line.substring(i + 1).toLongOrNull()
                        if (ts != null) map[line.substring(0, i)] = ts
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun save() {
        try {
            val now = System.currentTimeMillis()
            val sb = StringBuilder()
            for ((k, v) in map) {
                if (now - abs(v) < KEEP) {
                    sb.append(k).append(' ').append(v).append('\n')
                }
            }
            file?.writeText(sb.toString())
        } catch (e: Exception) {
        }
    }

    // 1 = worked recently, -1 = failed recently, 0 = unknown
    fun state(url: String): Int {
        val v = map[url] ?: return 0
        val age = System.currentTimeMillis() - abs(v)
        return if (v > 0) {
            if (age < OK_TTL) 1 else 0
        } else {
            if (age < FAIL_TTL) -1 else 0
        }
    }

    fun mark(url: String, ok: Boolean) {
        val t = System.currentTimeMillis()
        map[url] = if (ok) t else -t
    }

    fun clear() {
        map.clear()
        save()
    }

    fun probe(url: String): Boolean {
        var c: HttpURLConnection? = null
        try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 6000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", Config.UA)
            val code = c.responseCode
            if (code !in 200..299) return false
            val ct = (c.contentType ?: "").lowercase()
            if (ct.contains("text/html")) return false
            val buf = ByteArray(1024)
            val n = c.inputStream.read(buf)
            if (n <= 0) return false
            val isHls = url.lowercase().contains(".m3u8") || ct.contains("mpegurl")
            if (isHls) {
                val head = String(buf, 0, n, Charsets.ISO_8859_1).trimStart()
                return head.startsWith("#EXTM3U")
            }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            try {
                c?.disconnect()
            } catch (e: Exception) {
            }
        }
    }
}

class Checker(threads: Int) {
    private val pool: ExecutorService = Executors.newFixedThreadPool(threads)
    @Volatile
    private var stopped = false
    private val okN = AtomicInteger(0)
    private val done = AtomicInteger(0)
    private val pending = ConcurrentLinkedQueue<String>()

    fun start(urls: List<String>, onTick: (Int, Int) -> Unit) {
        val total = urls.size
        for (u in urls) {
            pool.execute {
                if (!stopped) {
                    val ok = Health.probe(u)
                    if (ok) {
                        okN.incrementAndGet()
                        Health.mark(u, true)
                        flushPending()
                    } else {
                        pending.add(u)
                        if (okN.get() > 0) flushPending()
                    }
                }
                val d = done.incrementAndGet()
                if (d == total && okN.get() == 0 && total <= 10) flushPending()
                if (!stopped) onTick(d, total)
            }
        }
    }

    // Failures are only recorded once at least one link worked in this run,
    // so a dropped internet connection does not hide every channel.
    private fun flushPending() {
        while (true) {
            val u = pending.poll() ?: break
            Health.mark(u, false)
        }
    }

    fun stop() {
        stopped = true
        pool.shutdownNow()
    }
}
