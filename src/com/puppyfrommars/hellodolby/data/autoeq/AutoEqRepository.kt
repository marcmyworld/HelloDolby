/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.data.autoeq

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

class AutoEqRepository(private val context: Context) {

    companion object {
        private const val TAG = "AutoEqRepository"
        private const val BASE_URL = "https://raw.githubusercontent.com/Pong-Development/DolbyProfiles/main"
        private const val INDEX_URL = "$BASE_URL/index.json.gz"
        private const val CACHE_FILE = "autoeq_index.json"
        private const val ASSET_INDEX = "autoeq_index.json.gz"
        val TARGET_FREQUENCIES = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    }

    @Volatile
    private var inMemoryIndex: List<AutoEqIndexEntry>? = null

    suspend fun getIndex(): List<AutoEqIndexEntry> = withContext(Dispatchers.IO) {
        inMemoryIndex?.let { return@withContext it }

        // 1. Check local cache file in cacheDir
        val cacheFile = File(context.cacheDir, CACHE_FILE)
        if (cacheFile.exists() && cacheFile.length() > 1024) {
            runCatching {
                val list = parseIndexJson(cacheFile.readText())
                if (list.isNotEmpty()) {
                    inMemoryIndex = list
                    Log.d(TAG, "Loaded ${list.size} AutoEQ profiles from local cache")
                    return@withContext list
                }
            }.onFailure { Log.w(TAG, "Failed reading AutoEQ cache file", it) }
        }

        // 2. Load from bundled assets (instant & offline)
        runCatching {
            val jsonStr = runCatching {
                context.assets.open("autoeq_index.json").bufferedReader().use { it.readText() }
            }.getOrElse {
                GZIPInputStream(context.assets.open(ASSET_INDEX)).bufferedReader().use { it.readText() }
            }
            val list = parseIndexJson(jsonStr)
            if (list.isNotEmpty()) {
                inMemoryIndex = list
                Log.d(TAG, "Loaded ${list.size} AutoEQ profiles from bundled assets")
                return@withContext list
            }
        }.onFailure { Log.d(TAG, "Assets index not available, falling back to network", it) }

        // 3. Fetch live index from GitHub
        runCatching {
            Log.d(TAG, "Fetching AutoEQ index from $INDEX_URL")
            val conn = (URL(INDEX_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "HelloDolby")
            }
            try {
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val decompressed = GZIPInputStream(conn.inputStream).bufferedReader().use { it.readText() }
                    val list = parseIndexJson(decompressed)
                    if (list.isNotEmpty()) {
                        inMemoryIndex = list
                        runCatching { cacheFile.writeText(decompressed) }
                        Log.d(TAG, "Downloaded ${list.size} AutoEQ profiles")
                        return@withContext list
                    }
                } else {
                    Log.w(TAG, "HTTP error fetching index: ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.e(TAG, "Network error fetching AutoEQ index", it) }

        emptyList()
    }

    suspend fun search(query: String, limit: Int = 60): List<AutoEqIndexEntry> = withContext(Dispatchers.Default) {
        val index = getIndex()
        if (query.isBlank()) return@withContext index.take(limit)

        val queryNorm = query.trim().lowercase()
            .replace(" ii", " 2")
            .replace(" iii", " 3")
            .replace(" iv", " 4")
        val tokens = queryNorm.split(Regex("\\s+")).filter { it.isNotBlank() }

        index.filter { entry ->
            val nameNorm = entry.name.lowercase()
                .replace(" ii", " 2")
                .replace(" iii", " 3")
                .replace(" iv", " 4")
            val searchKey = entry.searchKey.lowercase()
            val source = entry.source.lowercase()
            tokens.all { t -> nameNorm.contains(t) || searchKey.contains(t) || source.contains(t) }
        }.take(limit)
    }

    suspend fun fetchProfile(id: String): AutoEqProfile? = withContext(Dispatchers.IO) {
        val profileCacheDir = File(context.cacheDir, "profiles").apply { mkdirs() }
        val cachedFile = File(profileCacheDir, "$id.json")
        if (cachedFile.exists() && cachedFile.length() > 50) {
            runCatching {
                val json = JSONObject(cachedFile.readText())
                return@withContext AutoEqProfile(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    graphicEq = json.getString("graphicEq")
                )
            }.onFailure { Log.w(TAG, "Failed reading cached profile $id", it) }
        }

        runCatching {
            val profileUrl = "$BASE_URL/profiles/$id.json.gz"
            Log.d(TAG, "Fetching AutoEQ profile from $profileUrl")
            val conn = (URL(profileUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "HelloDolby")
            }
            try {
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val decompressed = GZIPInputStream(conn.inputStream).bufferedReader().use { it.readText() }
                    val json = JSONObject(decompressed)
                    runCatching { cachedFile.writeText(decompressed) }
                    return@withContext AutoEqProfile(
                        id = json.getString("id"),
                        name = json.getString("name"),
                        graphicEq = json.getString("graphicEq")
                    )
                } else {
                    Log.w(TAG, "HTTP error fetching profile $id: ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.e(TAG, "Error fetching AutoEQ profile $id", it) }

        null
    }

    fun parseGraphicEqTo10Bands(graphicEq: String): List<Float> {
        val freqMap = mutableMapOf<Int, Float>()
        val clean = graphicEq.removePrefix("GraphicEQ:").trim()
        for (pair in clean.split(";")) {
            val parts = pair.trim().split(Regex("\\s+"))
            if (parts.size == 2) {
                val hz = parts[0].toIntOrNull()
                val db = parts[1].toFloatOrNull()
                if (hz != null && db != null) {
                    freqMap[hz] = db
                }
            }
        }
        if (freqMap.isEmpty()) return List(10) { 0f }

        val sortedFreqs = freqMap.keys.sorted()
        return TARGET_FREQUENCIES.map { targetHz ->
            val gain = freqMap[targetHz] ?: run {
                val lower = sortedFreqs.lastOrNull { it < targetHz }
                val upper = sortedFreqs.firstOrNull { it > targetHz }
                when {
                    lower == null && upper != null -> freqMap[upper] ?: 0f
                    upper == null && lower != null -> freqMap[lower] ?: 0f
                    lower != null && upper != null -> {
                        val ratio = (targetHz - lower).toFloat() / (upper - lower)
                        val lGain = freqMap[lower] ?: 0f
                        val uGain = freqMap[upper] ?: 0f
                        lGain + ratio * (uGain - lGain)
                    }
                    else -> 0f
                }
            }
            ((gain * 10f).roundToInt() / 10f).coerceIn(-10f, 10f)
        }
    }

    private fun parseIndexJson(json: String): List<AutoEqIndexEntry> {
        val result = mutableListOf<AutoEqIndexEntry>()
        val root = JSONObject(json)
        val array = root.getJSONArray("profiles")
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            result.add(
                AutoEqIndexEntry(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    source = item.optString("source", "Unknown"),
                    measurementRig = item.optString("measurementRig", "Unknown"),
                    searchKey = item.getString("searchKey")
                )
            )
        }
        return result
    }
}
