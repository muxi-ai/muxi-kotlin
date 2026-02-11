package org.muxi.sdk

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class FormationConfig(
    val formationId: String? = null,
    val url: String? = null,
    val serverUrl: String? = null,
    val baseUrl: String? = null,
    val adminKey: String? = null,
    val clientKey: String? = null,
    val maxRetries: Int = 0,
    val timeout: Int = 30,
    val debug: Boolean = false,
    val mode: String = "live",  // "live" (default) or "draft" for local dev
    internal val app: String? = null  // Internal: for Console telemetry
)

class FormationClient(config: FormationConfig) {
    private val transport: FormationTransport
    
    init {
        val baseUrl = buildBaseUrl(config)
        transport = FormationTransport(baseUrl, config.adminKey, config.clientKey, config.timeout, config.maxRetries, config.debug, config.app)
    }
    
    // Health / status
    suspend fun health(): JsonElement? = transport.request("GET", "/health", useAdmin = false)
    suspend fun getStatus(): JsonElement? = transport.request("GET", "/status")
    suspend fun getConfig(): JsonElement? = transport.request("GET", "/config")
    suspend fun getFormationInfo(): JsonElement? = transport.request("GET", "/formation")
    
    // Agents / MCP
    suspend fun getAgents(): JsonElement? = transport.request("GET", "/agents")
    suspend fun getAgent(agentId: String): JsonElement? = transport.request("GET", "/agents/$agentId")
    suspend fun getMcpServers(): JsonElement? = transport.request("GET", "/mcp/servers")
    suspend fun getMcpServer(serverId: String): JsonElement? = transport.request("GET", "/mcp/servers/$serverId")
    suspend fun getMcpTools(): JsonElement? = transport.request("GET", "/mcp/tools")
    
    // Secrets
    suspend fun getSecrets(): JsonElement? = transport.request("GET", "/secrets")
    suspend fun getSecret(key: String): JsonElement? = transport.request("GET", "/secrets/$key")
    suspend fun setSecret(key: String, value: String) { transport.request("PUT", "/secrets/$key", body = mapOf("value" to value)) }
    suspend fun deleteSecret(key: String) { transport.request("DELETE", "/secrets/$key") }
    
    // Chat
    suspend fun chat(payload: Map<String, Any>, userId: String = ""): JsonElement? = transport.request("POST", "/chat", body = payload, useAdmin = false, userId = userId)
    fun chatStream(payload: Map<String, Any>, userId: String = ""): Flow<SseEvent> = transport.streamSse("POST", "/chat", body = payload + ("stream" to true), useAdmin = false, userId = userId)
    suspend fun audioChat(payload: Map<String, Any>, userId: String = ""): JsonElement? = transport.request("POST", "/audiochat", body = payload, useAdmin = false, userId = userId)
    fun audioChatStream(payload: Map<String, Any>, userId: String = ""): Flow<SseEvent> = transport.streamSse("POST", "/audiochat", body = payload + ("stream" to true), useAdmin = false, userId = userId)
    
    // Sessions
    suspend fun getSessions(userId: String, limit: Int? = null): JsonElement? = transport.request("GET", "/sessions", mapOf("user_id" to userId, "limit" to limit), useAdmin = false, userId = userId)
    suspend fun getSession(sessionId: String, userId: String): JsonElement? = transport.request("GET", "/sessions/$sessionId", useAdmin = false, userId = userId)
    suspend fun getSessionMessages(sessionId: String, userId: String): JsonElement? = transport.request("GET", "/sessions/$sessionId/messages", useAdmin = false, userId = userId)
    suspend fun restoreSession(sessionId: String, userId: String, messages: List<Map<String, Any>>) { transport.request("POST", "/sessions/$sessionId/restore", body = mapOf("messages" to messages), useAdmin = false, userId = userId) }
    
    // Requests
    suspend fun getRequests(userId: String): JsonElement? = transport.request("GET", "/requests", useAdmin = false, userId = userId)
    suspend fun getRequestStatus(requestId: String, userId: String): JsonElement? = transport.request("GET", "/requests/$requestId", useAdmin = false, userId = userId)
    suspend fun cancelRequest(requestId: String, userId: String) { transport.request("DELETE", "/requests/$requestId", useAdmin = false, userId = userId) }
    
    // Memory
    suspend fun getMemoryConfig(): JsonElement? = transport.request("GET", "/memory")
    suspend fun getMemories(userId: String, limit: Int? = null): JsonElement? = transport.request("GET", "/memories", mapOf("user_id" to userId, "limit" to limit), useAdmin = false, userId = userId)
    suspend fun addMemory(userId: String, type: String, detail: String): JsonElement? = transport.request("POST", "/memories", body = mapOf("user_id" to userId, "type" to type, "detail" to detail), useAdmin = false, userId = userId)
    suspend fun deleteMemory(userId: String, memoryId: String) { transport.request("DELETE", "/memories/$memoryId", mapOf("user_id" to userId), useAdmin = false, userId = userId) }
    suspend fun getUserBuffer(userId: String): JsonElement? = transport.request("GET", "/memory/buffer", mapOf("user_id" to userId), useAdmin = false, userId = userId)
    suspend fun clearUserBuffer(userId: String): JsonElement? = transport.request("DELETE", "/memory/buffer", mapOf("user_id" to userId), useAdmin = false, userId = userId)
    suspend fun clearSessionBuffer(userId: String, sessionId: String): JsonElement? = transport.request("DELETE", "/memory/buffer/$sessionId", mapOf("user_id" to userId), useAdmin = false, userId = userId)
    suspend fun clearAllBuffers(): JsonElement? = transport.request("DELETE", "/memory/buffer")
    suspend fun getBufferStats(): JsonElement? = transport.request("GET", "/memory/stats")
    
    // Scheduler
    suspend fun getSchedulerConfig(): JsonElement? = transport.request("GET", "/scheduler")
    suspend fun getSchedulerJobs(userId: String): JsonElement? = transport.request("GET", "/scheduler/jobs", mapOf("user_id" to userId))
    suspend fun getSchedulerJob(jobId: String): JsonElement? = transport.request("GET", "/scheduler/jobs/$jobId")
    suspend fun createSchedulerJob(type: String, schedule: String, message: String, userId: String): JsonElement? = transport.request("POST", "/scheduler/jobs", body = mapOf("type" to type, "schedule" to schedule, "message" to message, "user_id" to userId))
    suspend fun deleteSchedulerJob(jobId: String) { transport.request("DELETE", "/scheduler/jobs/$jobId") }
    
    // Config endpoints
    suspend fun getAsyncConfig(): JsonElement? = transport.request("GET", "/async")
    suspend fun getA2aConfig(): JsonElement? = transport.request("GET", "/a2a")
    suspend fun getLoggingConfig(): JsonElement? = transport.request("GET", "/logging")
    suspend fun getLoggingDestinations(): JsonElement? = transport.request("GET", "/logging/destinations")
    
    // Credentials
    suspend fun listCredentialServices(): JsonElement? = transport.request("GET", "/credentials/services")
    suspend fun listCredentials(userId: String): JsonElement? = transport.request("GET", "/credentials", useAdmin = false, userId = userId)
    suspend fun getCredential(credentialId: String, userId: String): JsonElement? = transport.request("GET", "/credentials/$credentialId", useAdmin = false, userId = userId)
    suspend fun createCredential(userId: String, payload: Map<String, Any>): JsonElement? = transport.request("POST", "/credentials", body = payload, useAdmin = false, userId = userId)
    suspend fun deleteCredential(credentialId: String, userId: String): JsonElement? = transport.request("DELETE", "/credentials/$credentialId", useAdmin = false, userId = userId)
    
    // User identifiers
    suspend fun getUserIdentifiersForUser(userId: String): JsonElement? = transport.request("GET", "/users/identifiers/$userId")
    suspend fun linkUserIdentifier(muxiUserId: String, identifiers: List<Any>): JsonElement? = transport.request("POST", "/users/identifiers", body = mapOf("muxi_user_id" to muxiUserId, "identifiers" to identifiers))
    suspend fun unlinkUserIdentifier(identifier: String) { transport.request("DELETE", "/users/identifiers/$identifier") }
    
    // Overlord / LLM
    suspend fun getOverlordConfig(): JsonElement? = transport.request("GET", "/overlord")
    suspend fun getOverlordPersona(): JsonElement? = transport.request("GET", "/overlord/persona")
    suspend fun getLlmSettings(): JsonElement? = transport.request("GET", "/llm/settings")
    
    // Triggers / SOP / Audit
    suspend fun getTriggers(): JsonElement? = transport.request("GET", "/triggers", useAdmin = false)
    suspend fun getTrigger(name: String): JsonElement? = transport.request("GET", "/triggers/$name", useAdmin = false)
    suspend fun fireTrigger(name: String, data: Any, async: Boolean = false, userId: String = ""): JsonElement? = transport.request("POST", "/triggers/$name", mapOf("async" to if (async) "true" else "false"), data, useAdmin = false, userId = userId)
    suspend fun getSops(): JsonElement? = transport.request("GET", "/sops", useAdmin = false)
    suspend fun getSop(name: String): JsonElement? = transport.request("GET", "/sops/$name", useAdmin = false)
    suspend fun getAuditLog(): JsonElement? = transport.request("GET", "/audit")
    suspend fun clearAuditLog() { transport.request("DELETE", "/audit?confirm=clear-audit-log") }
    
    // Streaming
    fun streamEvents(userId: String): Flow<SseEvent> = transport.streamSse("GET", "/events", mapOf("user_id" to userId), useAdmin = false, userId = userId)
    fun streamRequest(userId: String, sessionId: String, requestId: String): Flow<SseEvent> = transport.streamSse("GET", "/events/$sessionId/$requestId", useAdmin = false, userId = userId)
    fun streamLogs(filters: Map<String, Any?>? = null): Flow<SseEvent> = transport.streamSse("GET", "/logs", filters)
    
    // Resolve user
    suspend fun resolveUser(identifier: String, createUser: Boolean = false): JsonElement? = transport.request("POST", "/users/resolve", body = mapOf("identifier" to identifier, "create_user" to createUser), useAdmin = false)
    
    private fun buildBaseUrl(config: FormationConfig): String {
        config.baseUrl?.takeIf { it.isNotEmpty() }?.let { return it.trimEnd('/') }
        config.url?.takeIf { it.isNotEmpty() }?.let { return "${it.trimEnd('/')}/v1" }
        if (!config.serverUrl.isNullOrEmpty() && !config.formationId.isNullOrEmpty()) {
            val prefix = if (config.mode == "draft") "draft" else "api"
            return "${config.serverUrl.trimEnd('/')}/$prefix/${config.formationId}/v1"
        }
        throw IllegalArgumentException("must set baseUrl, url, or serverUrl+formationId")
    }
}

internal class FormationTransport(
    private val baseUrl: String,
    private val adminKey: String?,
    private val clientKey: String?,
    private val timeout: Int,
    private val maxRetries: Int,
    private val debug: Boolean,
    private val app: String? = null
) {
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
    
    suspend fun request(method: String, path: String, params: Map<String, Any?>? = null, body: Any? = null, useAdmin: Boolean = true, userId: String = ""): JsonElement? {
        val (url, _) = buildUrl(path, params)
        val headers = buildHeaders(useAdmin, userId, body != null)
        
        val requestBuilder = okhttp3.Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        
        val requestBody = if (body != null) {
            json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), body.toJsonElement())
                .toRequestBody("application/json".toMediaType())
        } else null
        
        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(requestBody ?: "".toRequestBody())
            "PUT" -> requestBuilder.put(requestBody ?: "".toRequestBody())
            "DELETE" -> if (requestBody != null) requestBuilder.delete(requestBody) else requestBuilder.delete()
        }
        
        client.newCall(requestBuilder.build()).execute().use { response ->
            // Check for SDK updates (non-blocking, once per process)
            val responseHeaders = response.headers.toMap()
            VersionCheck.checkForUpdates(responseHeaders)
            
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                var code: String? = null; var message = "Unknown error"
                try { val p = json.parseToJsonElement(responseBody).jsonObject; code = p["code"]?.jsonPrimitive?.contentOrNull ?: p["error"]?.jsonPrimitive?.contentOrNull; message = p["message"]?.jsonPrimitive?.contentOrNull ?: message } catch (_: Exception) {}
                throw ErrorMapper.map(response.code, code, message, null, response.header("Retry-After")?.toIntOrNull())
            }
            val content = response.body?.string()
            return if (content.isNullOrEmpty()) null else unwrapEnvelope(json.parseToJsonElement(content))
        }
    }
    
    fun streamSse(method: String, path: String, params: Map<String, Any?>? = null, body: Any? = null, useAdmin: Boolean = true, userId: String = ""): Flow<SseEvent> = flow {
        val (url, _) = buildUrl(path, params)
        val headers = buildHeaders(useAdmin, userId, body != null, "text/event-stream")
        
        val requestBuilder = okhttp3.Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        
        val requestBody = if (body != null) json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), body.toJsonElement()).toRequestBody("application/json".toMediaType()) else null
        when (method.uppercase()) { "GET" -> requestBuilder.get(); "POST" -> requestBuilder.post(requestBody ?: "".toRequestBody()) }
        
        val streamClient = client.newBuilder().readTimeout(0, java.util.concurrent.TimeUnit.SECONDS).build()
        streamClient.newCall(requestBuilder.build()).execute().use { response ->
            var currentEvent: String? = null; val dataParts = mutableListOf<String>()
            response.body?.source()?.let { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith(":")) continue
                    if (line.isEmpty()) { if (dataParts.isNotEmpty()) emit(SseEvent(currentEvent ?: "message", dataParts.joinToString("\n"))); currentEvent = null; dataParts.clear(); continue }
                    when { line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim(); line.startsWith("data:") -> dataParts.add(line.removePrefix("data:").trim()) }
                }
            }
        }
    }
    
    private fun buildUrl(path: String, params: Map<String, Any?>?): Pair<String, String> {
        val relPath = if (path.startsWith("/")) path else "/$path"
        val query = params?.filterValues { it != null }?.entries?.joinToString("&") { "${it.key}=${it.value}" }
        val fullPath = if (!query.isNullOrEmpty()) "$relPath?$query" else relPath
        return "${baseUrl.trimEnd('/')}$fullPath" to fullPath
    }
    
    private fun buildHeaders(useAdmin: Boolean, userId: String, hasBody: Boolean, accept: String = "application/json"): Map<String, String> {
        val headers = mutableMapOf("X-Muxi-SDK" to "kotlin/${MuxiVersion.VERSION}", "X-Muxi-Client" to "kotlin/${MuxiVersion.VERSION}", "X-Muxi-Idempotency-Key" to java.util.UUID.randomUUID().toString(), "Accept" to accept)
        if (!app.isNullOrEmpty()) headers["X-Muxi-App"] = app
        if (useAdmin) headers["X-MUXI-ADMIN-KEY"] = adminKey ?: throw IllegalArgumentException("admin key required")
        else headers["X-MUXI-CLIENT-KEY"] = clientKey ?: throw IllegalArgumentException("client key required")
        if (userId.isNotEmpty()) headers["X-Muxi-User-ID"] = userId
        if (hasBody) headers["Content-Type"] = "application/json"
        return headers
    }
    
    private fun unwrapEnvelope(obj: JsonElement): JsonElement? {
        if (obj !is kotlinx.serialization.json.JsonObject || !obj.containsKey("data")) return obj
        val data = obj["data"]; if (data is kotlinx.serialization.json.JsonObject) {
            val result = data.toMutableMap(); val req = obj["request"]?.jsonObject
            val requestId = req?.get("id")?.jsonPrimitive?.contentOrNull ?: obj["request_id"]?.jsonPrimitive?.contentOrNull
            if (requestId != null && !result.containsKey("request_id")) result["request_id"] = kotlinx.serialization.json.JsonPrimitive(requestId)
            obj["timestamp"]?.let { if (!result.containsKey("timestamp")) result["timestamp"] = it }
            return kotlinx.serialization.json.JsonObject(result)
        }
        return data ?: obj
    }
    
    private fun Any.toJsonElement(): kotlinx.serialization.json.JsonElement = when (this) {
        is kotlinx.serialization.json.JsonElement -> this
        is Number -> kotlinx.serialization.json.JsonPrimitive(this)
        is String -> kotlinx.serialization.json.JsonPrimitive(this)
        is Boolean -> kotlinx.serialization.json.JsonPrimitive(this)
        is Map<*, *> -> kotlinx.serialization.json.JsonObject(this.entries.associate { (k, v) -> k.toString() to (v?.toJsonElement() ?: kotlinx.serialization.json.JsonNull) })
        is List<*> -> kotlinx.serialization.json.JsonArray(this.map { it?.toJsonElement() ?: kotlinx.serialization.json.JsonNull })
        else -> kotlinx.serialization.json.JsonPrimitive(this.toString())
    }
}
