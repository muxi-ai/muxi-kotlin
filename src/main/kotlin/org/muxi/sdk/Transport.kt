package org.muxi.sdk

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class Transport(
    private val baseUrl: String,
    private val keyId: String,
    private val secretKey: String,
    private val timeout: Int = 30,
    private val maxRetries: Int = 0,
    private val debug: Boolean = false,
    private val app: String? = null
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeout.toLong(), TimeUnit.SECONDS)
        .readTimeout(timeout.toLong(), TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    companion object {
        private val RETRY_STATUSES = setOf(429, 500, 502, 503, 504)
    }
    
    suspend fun requestJson(method: String, path: String, params: Map<String, Any?>? = null, body: Any? = null): JsonElement? {
        val (url, fullPath) = buildUrl(path, params)
        val headers = buildHeaders(method, fullPath)
        
        var attempt = 0
        var backoff = 0.5
        
        while (true) {
            val startTime = System.currentTimeMillis()
            try {
                val requestBuilder = Request.Builder().url(url)
                headers.forEach { (k, v) -> requestBuilder.header(k, v) }
                
                val requestBody = if (body != null) {
                    json.encodeToString(JsonElement.serializer(), body.toJsonElement()).toRequestBody("application/json".toMediaType())
                } else null
                
                when (method.uppercase()) {
                    "GET" -> requestBuilder.get()
                    "POST" -> requestBuilder.post(requestBody ?: "".toRequestBody())
                    "PUT" -> requestBuilder.put(requestBody ?: "".toRequestBody())
                    "DELETE" -> if (requestBody != null) requestBuilder.delete(requestBody) else requestBuilder.delete()
                    "PATCH" -> requestBuilder.patch(requestBody ?: "".toRequestBody())
                }
                
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                    log("$method $fullPath -> ${response.code} (${String.format("%.3f", elapsed)}s)")
                    
                    // Check for SDK updates (non-blocking, once per process)
                    val responseHeaders = response.headers.toMap()
                    VersionCheck.checkForUpdates(responseHeaders)
                    
                    if (!response.isSuccessful) {
                        val retryAfter = response.header("Retry-After")?.toIntOrNull()
                        
                        if (RETRY_STATUSES.contains(response.code) && attempt < maxRetries) {
                            val sleepFor = minOf(backoff, 30.0)
                            log("retry $method $fullPath after ${sleepFor}s due to ${response.code}")
                            delay((sleepFor * 1000).toLong())
                            backoff *= 2
                            attempt++
                            return@use null
                        }
                        
                        val responseBody = response.body?.string() ?: ""
                        var code: String? = null
                        var message = "Unknown error"
                        var details: Map<String, Any?>? = null
                        
                        try {
                            val payload = json.parseToJsonElement(responseBody).jsonObject
                            code = payload["code"]?.jsonPrimitive?.contentOrNull ?: payload["error"]?.jsonPrimitive?.contentOrNull
                            message = payload["message"]?.jsonPrimitive?.contentOrNull ?: message
                        } catch (_: Exception) {}
                        
                        throw ErrorMapper.map(response.code, code, message, details, retryAfter)
                    }
                    
                    val content = response.body?.string()
                    if (content.isNullOrEmpty()) return null
                    
                    return unwrapEnvelope(json.parseToJsonElement(content))
                } ?: continue
            } catch (e: IOException) {
                if (attempt < maxRetries) {
                    val sleepFor = minOf(backoff, 30.0)
                    log("retry $method $fullPath after ${sleepFor}s due to connection error: ${e.message}")
                    delay((sleepFor * 1000).toLong())
                    backoff *= 2
                    attempt++
                    continue
                }
                throw ConnectionException(e.message ?: "Connection error")
            }
        }
    }
    
    fun streamLines(method: String, path: String, params: Map<String, Any?>? = null, body: Any? = null): Flow<String> = flow {
        val (url, fullPath) = buildUrl(path, params)
        val headers = buildHeaders(method, fullPath, "text/event-stream")
        
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        
        val requestBody = if (body != null) {
            json.encodeToString(JsonElement.serializer(), body.toJsonElement()).toRequestBody("application/json".toMediaType())
        } else null
        
        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(requestBody ?: "".toRequestBody())
        }
        
        val streamClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()
        streamClient.newCall(requestBuilder.build()).execute().use { response ->
            response.body?.source()?.let { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    emit(line)
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
    
    private fun buildHeaders(method: String, path: String, accept: String = "application/json"): Map<String, String> {
        val headers = mutableMapOf(
            "Authorization" to Auth.buildAuthHeader(keyId, secretKey, method, path),
            "Content-Type" to "application/json",
            "Accept" to accept,
            "X-Muxi-SDK" to "kotlin/${MuxiVersion.VERSION}",
            "X-Muxi-Client" to "kotlin/${System.getProperty("java.version")}",
            "X-Muxi-Idempotency-Key" to UUID.randomUUID().toString()
        )
        if (!app.isNullOrEmpty()) headers["X-Muxi-App"] = app
        return headers
    }
    
    private fun unwrapEnvelope(obj: JsonElement): JsonElement? {
        if (obj !is JsonObject || !obj.containsKey("data")) return obj
        
        val data = obj["data"]
        if (data is JsonObject) {
            val result = data.toMutableMap()
            val req = obj["request"]?.jsonObject
            val requestId = req?.get("id")?.jsonPrimitive?.contentOrNull ?: obj["request_id"]?.jsonPrimitive?.contentOrNull
            val ts = obj["timestamp"]
            
            if (requestId != null && !result.containsKey("request_id")) result["request_id"] = JsonPrimitive(requestId)
            if (ts != null && !result.containsKey("timestamp")) result["timestamp"] = ts
            
            return JsonObject(result)
        }
        
        return data ?: obj
    }
    
    private fun log(msg: String) {
        if (debug || System.getenv("MUXI_DEBUG") == "1") {
            System.err.println("[MUXI] $msg")
        }
    }
    
    private fun Any.toJsonElement(): JsonElement = when (this) {
        is JsonElement -> this
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Map<*, *> -> JsonObject(this.entries.associate { (k, v) -> k.toString() to (v?.toJsonElement() ?: JsonNull) })
        is List<*> -> JsonArray(this.map { it?.toJsonElement() ?: JsonNull })
        else -> JsonPrimitive(this.toString())
    }
}
