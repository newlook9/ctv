package com.livetv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class Store(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("livetv", Context.MODE_PRIVATE)

    fun getString(k: String, d: String): String = sp.getString(k, d) ?: d
    fun putString(k: String, v: String) {
        sp.edit().putString(k, v).apply()
    }
    fun getInt(k: String, d: Int): Int = sp.getInt(k, d)
    fun putInt(k: String, v: Int) {
        sp.edit().putInt(k, v).apply()
    }
    fun getBool(k: String, d: Boolean): Boolean = sp.getBoolean(k, d)
    fun putBool(k: String, v: Boolean) {
        sp.edit().putBoolean(k, v).apply()
    }

    fun getSet(k: String): MutableSet<String> {
        val out = HashSet<String>()
        try {
            val arr = JSONArray(getString(k, "[]"))
            for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (e: Exception) {
        }
        return out
    }

    fun putSet(k: String, set: Set<String>) {
        putString(k, JSONArray(set.toList()).toString())
    }

    private fun toJson(c: Channel): JSONObject {
        val o = JSONObject()
        o.put("n", c.name)
        o.put("u", c.url)
        o.put("l", c.logo)
        if (c.alts.isNotEmpty()) o.put("a", JSONArray(c.alts))
        if (c.radio) o.put("r", true)
        return o
    }

    private fun fromJson(o: JSONObject): Channel {
        val alts = ArrayList<String>()
        val a = o.optJSONArray("a")
        if (a != null) {
            for (i in 0 until a.length()) alts.add(a.getString(i))
        }
        return Channel(
            o.getString("n"), o.getString("u"), o.optString("l", ""),
            alts, o.optBoolean("r", false)
        )
    }

    private fun toJsonArray(list: List<Channel>): JSONArray {
        val arr = JSONArray()
        for (c in list) arr.put(toJson(c))
        return arr
    }

    fun getList(key: String): MutableList<Channel> {
        val out = ArrayList<Channel>()
        try {
            val arr = JSONArray(getString(key, "[]"))
            for (i in 0 until arr.length()) out.add(fromJson(arr.getJSONObject(i)))
        } catch (e: Exception) {
        }
        return out
    }

    fun saveList(key: String, list: List<Channel>) {
        putString(key, toJsonArray(list).toString())
    }

    fun getPlaylists(): MutableList<Playlist> {
        val out = ArrayList<Playlist>()
        try {
            val arr = JSONArray(getString("playlists", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val items = ArrayList<Channel>()
                val ia = o.getJSONArray("items")
                for (j in 0 until ia.length()) items.add(fromJson(ia.getJSONObject(j)))
                out.add(Playlist(o.getString("name"), items))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun savePlaylists(list: List<Playlist>) {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("name", p.name)
            o.put("items", toJsonArray(p.items))
            arr.put(o)
        }
        putString("playlists", arr.toString())
    }

    private fun cacheFile(key: String): File =
        File(ctx.filesDir, "list_" + key.replace("/", "_") + ".txt")

    fun readCache(key: String): String? {
        return try {
            val f = cacheFile(key)
            if (f.exists()) f.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    fun writeCache(key: String, text: String) {
        try {
            cacheFile(key).writeText(text)
        } catch (e: Exception) {
        }
    }

    fun clearCache() {
        try {
            ctx.filesDir.listFiles()?.forEach {
                if (it.name.startsWith("list_")) it.delete()
            }
        } catch (e: Exception) {
        }
    }

    fun download(url: String): String? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "LiveTV/1.0 (Android)")
            if (c.responseCode != 200) null
            else c.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
}

fun normName(n: String): String {
    val s = n.lowercase()
        .replace(Regex("\\(.*?\\)|\\[.*?]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (s.isEmpty()) n.lowercase() else s
}

// Merges entries with the same channel name into one channel with alternate links.
fun groupChannels(flat: List<Channel>): List<Channel> {
    val map = LinkedHashMap<String, Channel>()
    for (c in flat) {
        val k = normName(c.name)
        val ex = map[k]
        if (ex == null) {
            map[k] = c
        } else if (ex.url != c.url && !ex.alts.contains(c.url)) {
            map[k] = ex.copy(
                alts = ex.alts + c.url,
                logo = if (ex.logo.isEmpty()) c.logo else ex.logo
            )
        }
    }
    return map.values.toList()
}

fun parseM3u(text: String): List<Channel> {
    val out = ArrayList<Channel>()
    var name: String? = null
    var logo = ""
    val logoRe = Regex("tvg-logo=\"([^\"]*)\"")
    for (raw in text.lines()) {
        val line = raw.trim()
        if (line.startsWith("#EXTINF")) {
            name = line.substringAfterLast(",").trim()
            logo = logoRe.find(line)?.groupValues?.get(1) ?: ""
        } else if (line.isNotEmpty() && !line.startsWith("#") && name != null) {
            out.add(Channel(name, line, logo))
            name = null
            logo = ""
        }
    }
    return groupChannels(out)
}

fun parseRadio(text: String): List<Channel> {
    val out = ArrayList<Channel>()
    try {
        val arr = JSONArray(text)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val n = o.optString("name").trim()
            val u = o.optString("url_resolved").trim()
            var l = o.optString("favicon").trim()
            if (!l.startsWith("http")) l = ""
            if (n.isNotEmpty() && u.isNotEmpty()) out.add(Channel(n, u, l, emptyList(), true))
        }
    } catch (e: Exception) {
    }
    return out
}
