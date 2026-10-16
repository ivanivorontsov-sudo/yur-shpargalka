package org.ivanivorontsov.yur.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.ivanivorontsov.yur.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class LegalRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cacheFile: File get() = File(context.filesDir, "legal.json")
    private val prefs get() = context.getSharedPreferences("yur", Context.MODE_PRIVATE)

    fun favoriteIds(): Set<String> = prefs.getStringSet("fav", emptySet()) ?: emptySet()

    fun toggleFavorite(id: String): Set<String> {
        val next = favoriteIds().toMutableSet()
        if (!next.add(id)) next.remove(id)
        prefs.edit().putStringSet("fav", next).apply()
        return next
    }

    fun loadCachedOrBundled(): LegalDoc {
        val cached = cacheFile.takeIf { it.exists() }?.readText(Charsets.UTF_8)
        if (cached != null) {
            runCatching { return json.decodeFromString<LegalDoc>(cached) }
        }
        return loadBundled()
    }

    fun loadBundled(): LegalDoc {
        context.assets.open("legal.json").bufferedReader().use {
            return json.decodeFromString(it.readText())
        }
    }

    suspend fun refreshFromNetwork(): Result<Pair<LegalDoc, String?>> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(BuildConfig.DATA_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "YurShpargalka/1.0")
            }
            try {
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                val body = conn.inputStream.bufferedReader().readText()
                val doc = json.decodeFromString<LegalDoc>(body)
                val oldUpdated = runCatching { loadCachedOrBundled().updated }.getOrDefault("")
                cacheFile.writeText(body, Charsets.UTF_8)
                prefs.edit().putLong("last_fetch", System.currentTimeMillis()).apply()
                val change = when {
                    oldUpdated.isBlank() -> "Загружена база ${doc.updated}"
                    oldUpdated != doc.updated -> "Было: $oldUpdated → стало: ${doc.updated}. ${doc.changelog.firstOrNull()?.text ?: ""}"
                    else -> "Уже актуальная версия (${doc.updated})"
                }
                doc to change
            } finally {
                conn.disconnect()
            }
        }
    }

    fun lastFetchMs(): Long = prefs.getLong("last_fetch", 0L)
}
