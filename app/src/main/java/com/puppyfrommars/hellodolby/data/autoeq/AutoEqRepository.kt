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
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

/**
 * UI-only AutoEQ repository backed by the bundled offline index and a dummy curve preview.
 * Live network profile fetching is intentionally omitted until core backend work begins.
 */
class AutoEqRepository(private val context: Context) {

    companion object {
        private const val TAG = "AutoEqRepository"
        private const val ASSET_INDEX = "autoeq_index.json.gz"
        private val DUMMY_BANDS = "GraphicEQ: 32 1.5; 64 2.0; 125 0.5; 250 -1.0; 500 0.0; 1000 0.5; 2000 1.5; 4000 2.0; 8000 1.0; 16000 0.5"
        private val TARGET_FREQUENCIES = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    }

    private var inMemoryIndex: List<AutoEqIndexEntry>? = null

    suspend fun getIndex(): List<AutoEqIndexEntry> = withContext(Dispatchers.IO) {
        inMemoryIndex?.let { return@withContext it }

        return@withContext try {
            val jsonStr = context.assets.open(ASSET_INDEX).use { stream ->
                GZIPInputStream(stream).bufferedReader().use { it.readText() }
            }
            parseIndexJson(jsonStr).also { inMemoryIndex = it }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading bundled AutoEQ asset index", e)
            emptyList()
        }
    }

    suspend fun search(query: String, limit: Int = 60): List<AutoEqIndexEntry> = withContext(Dispatchers.Default) {
        val index = getIndex()
        if (query.isBlank()) return@withContext index.take(limit)

        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        index.filter { entry ->
            val name = entry.name.lowercase()
            val key = entry.searchKey.lowercase()
            val source = entry.source.lowercase()
            tokens.all { t -> name.contains(t) || key.contains(t) || source.contains(t) }
        }.take(limit)
    }

    suspend fun fetchProfile(id: String): AutoEqProfile? = withContext(Dispatchers.Default) {
        val entry = getIndex().firstOrNull { it.id == id }
        AutoEqProfile(
            id = id,
            name = entry?.name ?: id,
            graphicEq = DUMMY_BANDS
        )
    }

    fun parseGraphicEqTo10Bands(graphicEq: String): List<Float> {
        val freqMap = mutableMapOf<Int, Float>()
        graphicEq.removePrefix("GraphicEQ:").trim().split(";").forEach { pair ->
            val parts = pair.trim().split(Regex("\\s+"))
            if (parts.size == 2) {
                val hz = parts[0].toIntOrNull()
                val db = parts[1].toFloatOrNull()
                if (hz != null && db != null) {
                    freqMap[hz] = db
                }
            }
        }
        return TARGET_FREQUENCIES.map { hz ->
            val raw = freqMap[hz] ?: 0f
            ((raw * 10f).roundToInt() / 10f).coerceIn(-10f, 10f)
        }
    }

    private fun parseIndexJson(json: String): List<AutoEqIndexEntry> {
        val result = mutableListOf<AutoEqIndexEntry>()
        val array = JSONObject(json).getJSONArray("profiles")
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
