package dev.muxi.sdk

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement

data class ServerConfig(
    val url: String,
    val keyId: String,
    val secretKey: String,
    val maxRetries: Int = 0,
    val timeout: Int = 30,
    val debug: Boolean = false
)

class ServerClient(config: ServerConfig) {
    private val transport = Transport(
        baseUrl = config.url,
        keyId = config.keyId,
        secretKey = config.secretKey,
        timeout = config.timeout,
        maxRetries = config.maxRetries,
        debug = config.debug
    )
    
    // Unauthenticated
    suspend fun ping(): Int = (transport.requestJson("GET", "/ping") as? Map<*, *>)?.size ?: 0
    suspend fun health(): JsonElement? = transport.requestJson("GET", "/health")
    
    // Authenticated
    suspend fun status(): JsonElement? = rpcGet("/rpc/server/status")
    suspend fun listFormations(): JsonElement? = rpcGet("/rpc/formations")
    suspend fun getFormation(formationId: String): JsonElement? = rpcGet("/rpc/formations/$formationId")
    suspend fun stopFormation(formationId: String): JsonElement? = rpcPost("/rpc/formations/$formationId/stop", emptyMap<String, Any>())
    suspend fun startFormation(formationId: String): JsonElement? = rpcPost("/rpc/formations/$formationId/start", emptyMap<String, Any>())
    suspend fun restartFormation(formationId: String): JsonElement? = rpcPost("/rpc/formations/$formationId/restart", emptyMap<String, Any>())
    suspend fun rollbackFormation(formationId: String): JsonElement? = rpcPost("/rpc/formations/$formationId/rollback", emptyMap<String, Any>())
    suspend fun deleteFormation(formationId: String): JsonElement? = rpcDelete("/rpc/formations/$formationId")
    suspend fun cancelUpdate(formationId: String): JsonElement? = rpcPost("/rpc/formations/$formationId/cancel-update", emptyMap<String, Any>())
    suspend fun deployFormation(formationId: String, payload: Map<String, Any>): JsonElement? = rpcPost("/rpc/formations/$formationId/deploy", payload)
    suspend fun updateFormation(formationId: String, payload: Map<String, Any>): JsonElement? = rpcPost("/rpc/formations/$formationId/update", payload)
    suspend fun getFormationLogs(formationId: String, limit: Int? = null): JsonElement? = rpcGet("/rpc/formations/$formationId/logs", limit?.let { mapOf("limit" to it) })
    suspend fun getServerLogs(limit: Int? = null): JsonElement? = rpcGet("/rpc/server/logs", limit?.let { mapOf("limit" to it) })
    
    // Streaming
    fun deployFormationStream(formationId: String, payload: Map<String, Any>): Flow<SseEvent> = streamSse("/rpc/formations/$formationId/deploy/stream", payload)
    fun updateFormationStream(formationId: String, payload: Map<String, Any>): Flow<SseEvent> = streamSse("/rpc/formations/$formationId/update/stream", payload)
    fun startFormationStream(formationId: String): Flow<SseEvent> = streamSse("/rpc/formations/$formationId/start/stream", emptyMap<String, Any>())
    fun restartFormationStream(formationId: String): Flow<SseEvent> = streamSse("/rpc/formations/$formationId/restart/stream", emptyMap<String, Any>())
    fun rollbackFormationStream(formationId: String): Flow<SseEvent> = streamSse("/rpc/formations/$formationId/rollback/stream", emptyMap<String, Any>())
    fun streamFormationLogs(formationId: String): Flow<SseEvent> = streamSseGet("/rpc/formations/$formationId/logs/stream")
    
    private suspend fun rpcGet(path: String, params: Map<String, Any?>? = null): JsonElement? = transport.requestJson("GET", path, params)
    private suspend fun rpcPost(path: String, body: Any): JsonElement? = transport.requestJson("POST", path, body = body)
    private suspend fun rpcDelete(path: String): JsonElement? = transport.requestJson("DELETE", path)
    
    private fun streamSse(path: String, body: Any): Flow<SseEvent> = flow {
        var currentEvent: String? = null
        val dataParts = mutableListOf<String>()
        
        transport.streamLines("POST", path, body = body).collect { line ->
            if (line.startsWith(":")) return@collect
            if (line.isEmpty()) {
                if (dataParts.isNotEmpty()) emit(SseEvent(currentEvent ?: "message", dataParts.joinToString("\n")))
                currentEvent = null
                dataParts.clear()
                return@collect
            }
            when {
                line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim()
                line.startsWith("data:") -> dataParts.add(line.removePrefix("data:").trim())
            }
        }
    }
    
    private fun streamSseGet(path: String): Flow<SseEvent> = flow {
        var currentEvent: String? = null
        val dataParts = mutableListOf<String>()
        
        transport.streamLines("GET", path).collect { line ->
            if (line.startsWith(":")) return@collect
            if (line.isEmpty()) {
                if (dataParts.isNotEmpty()) emit(SseEvent(currentEvent ?: "message", dataParts.joinToString("\n")))
                currentEvent = null
                dataParts.clear()
                return@collect
            }
            when {
                line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim()
                line.startsWith("data:") -> dataParts.add(line.removePrefix("data:").trim())
            }
        }
    }
}

data class SseEvent(val event: String, val data: String)
