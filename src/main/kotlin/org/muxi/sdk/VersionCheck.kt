package org.muxi.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

object VersionCheck {
    private const val SDK_NAME = "kotlin"
    private const val TWELVE_HOURS = 12L
    private var checked = false
    private val lock = Any()
    
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    
    fun checkForUpdates(headers: Map<String, String>) {
        synchronized(lock) {
            if (checked) return
            checked = true
        }
        
        if (notificationsDisabled()) return
        
        val latest = headers["X-Muxi-SDK-Latest"] ?: headers["x-muxi-sdk-latest"] ?: return
        if (!isNewerVersion(latest, MuxiVersion.VERSION)) return
        
        updateLatestVersion(latest)
        
        if (!notifiedRecently()) {
            System.err.println("[muxi] SDK update available: $latest (current: ${MuxiVersion.VERSION})")
            System.err.println("[muxi] Update via Gradle: implementation(\"org.muxi:muxi-kotlin:$latest\")")
            markNotified()
        }
    }
    
    private fun notificationsDisabled(): Boolean = System.getenv("MUXI_SDK_VERSION_NOTIFICATION") == "0"
    
    private fun getCachePath(): File? {
        val home = System.getProperty("user.home") ?: return null
        return File(home, ".muxi/sdk-versions.json")
    }
    
    private fun loadCache(): MutableMap<String, VersionEntry> {
        val file = getCachePath() ?: return mutableMapOf()
        if (!file.exists()) return mutableMapOf()
        return try {
            json.decodeFromString<MutableMap<String, VersionEntry>>(file.readText())
        } catch (e: Exception) {
            mutableMapOf()
        }
    }
    
    private fun saveCache(cache: Map<String, VersionEntry>) {
        val file = getCachePath() ?: return
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(kotlinx.serialization.serializer(), cache))
        } catch (e: Exception) {
            // Ignore cache errors
        }
    }
    
    private fun isNewerVersion(latest: String, current: String): Boolean = latest > current
    
    private fun notifiedRecently(): Boolean {
        val cache = loadCache()
        val entry = cache[SDK_NAME] ?: return false
        val lastNotified = entry.lastNotified ?: return false
        return try {
            val lastTime = Instant.parse(lastNotified)
            ChronoUnit.HOURS.between(lastTime, Instant.now()) < TWELVE_HOURS
        } catch (e: Exception) {
            false
        }
    }
    
    private fun updateLatestVersion(latest: String) {
        val cache = loadCache()
        val entry = cache[SDK_NAME] ?: VersionEntry()
        cache[SDK_NAME] = entry.copy(current = MuxiVersion.VERSION, latest = latest)
        saveCache(cache)
    }
    
    private fun markNotified() {
        val cache = loadCache()
        cache[SDK_NAME]?.let {
            cache[SDK_NAME] = it.copy(lastNotified = Instant.now().toString())
            saveCache(cache)
        }
    }
    
    @Serializable
    data class VersionEntry(
        val current: String? = null,
        val latest: String? = null,
        val lastNotified: String? = null
    )
}
