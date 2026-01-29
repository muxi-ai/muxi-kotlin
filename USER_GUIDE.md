# MUXI Kotlin SDK User Guide

## Installation

Add to your `build.gradle.kts`:

```kotlin
implementation("dev.muxi:muxi-kotlin:0.20260129.0")
```

Or in Groovy `build.gradle`:

```groovy
implementation 'dev.muxi:muxi-kotlin:0.20260129.0'
```

## Requirements

- Kotlin 1.9+
- JDK 17+

## Quickstart

```kotlin
import dev.muxi.sdk.*

// Server client (management, HMAC auth)
val server = ServerClient(ServerConfig(
    url = System.getenv("MUXI_SERVER_URL"),
    keyId = System.getenv("MUXI_KEY_ID"),
    secretKey = System.getenv("MUXI_SECRET_KEY")
))
println(server.status())

// Formation client (runtime, key auth)
val client = FormationClient(FormationConfig(
    serverUrl = System.getenv("MUXI_SERVER_URL"),
    formationId = "my-bot",
    clientKey = System.getenv("MUXI_CLIENT_KEY"),
    adminKey = System.getenv("MUXI_ADMIN_KEY")
))
println(client.health())
```

## Clients

- **ServerClient** (management, HMAC): deploy/list/update formations, server health/status, server logs.
- **FormationClient** (runtime, client/admin keys): chat/audio (streaming), agents, secrets, MCP, memory, scheduler, sessions/requests, identifiers, credentials, triggers/SOPs/audit, async/A2A/logging config, overlord/LLM settings, events/logs streaming.

## Streaming

```kotlin
import kotlinx.coroutines.flow.collect

// Chat streaming with Kotlin Flow
client.chatStream(mapOf("message" to "Tell me a story"), userId = "user-123")
    .collect { event -> print(event.data) }

// Event streaming
client.streamEvents("user-123")
    .collect { event -> println(event) }

// Log streaming (admin)
client.streamLogs(mapOf("level" to "info"))
    .collect { log -> println(log) }
```

## Auth & Headers

- **ServerClient**: HMAC with `keyId`/`secretKey` on `/rpc` endpoints.
- **FormationClient**: `X-MUXI-CLIENT-KEY` or `X-MUXI-ADMIN-KEY` on `/api/{formation}/v1`. Override `baseUrl` for direct access (e.g., `http://localhost:9012/v1`).
- **Idempotency**: `X-Muxi-Idempotency-Key` auto-generated on every request.
- **SDK headers**: `X-Muxi-SDK`, `X-Muxi-Client` set automatically.

## Timeouts & Retries

- Default timeout: 30s (no timeout for streaming).
- Retries: `maxRetries` with exponential backoff on 429/5xx/connection errors; respects `Retry-After`.
- Debug logging: enabled when `debug = true` or `MUXI_DEBUG=1`.

## Error Handling

```kotlin
import dev.muxi.sdk.*

try {
    client.chat(mapOf("message" to "hello"))
} catch (e: AuthenticationException) {
    println("Auth failed: ${e.message}")
} catch (e: RateLimitException) {
    println("Rate limited. Retry after: ${e.retryAfter}s")
} catch (e: NotFoundException) {
    println("Not found: ${e.message}")
} catch (e: MuxiException) {
    println("${e.errorCode}: ${e.message} (${e.statusCode})")
}
```

Error types: `AuthenticationException`, `AuthorizationException`, `NotFoundException`, `ValidationException`, `RateLimitException`, `ServerException`, `ConnectionException`.

## Notable Endpoints (FormationClient)

| Category | Methods |
|----------|---------|
| Chat/Audio | `chat`, `chatStream`, `audioChat`, `audioChatStream` |
| Memory | `getMemoryConfig`, `getMemories`, `addMemory`, `deleteMemory`, `getUserBuffer`, `clearUserBuffer`, `clearSessionBuffer`, `clearAllBuffers`, `getBufferStats` |
| Scheduler | `getSchedulerConfig`, `getSchedulerJobs`, `getSchedulerJob`, `createSchedulerJob`, `deleteSchedulerJob` |
| Sessions | `getSessions`, `getSession`, `getSessionMessages`, `restoreSession` |
| Requests | `getRequests`, `getRequestStatus`, `cancelRequest` |
| Agents/MCP | `getAgents`, `getAgent`, `getMcpServers`, `getMcpServer`, `getMcpTools` |
| Secrets | `getSecrets`, `getSecret`, `setSecret`, `deleteSecret` |
| Credentials | `listCredentialServices`, `listCredentials`, `getCredential`, `createCredential`, `deleteCredential` |
| Identifiers | `getUserIdentifiersForUser`, `linkUserIdentifier`, `unlinkUserIdentifier` |
| Triggers/SOP | `getTriggers`, `getTrigger`, `fireTrigger`, `getSops`, `getSop` |
| Audit | `getAuditLog`, `clearAuditLog` |
| Config | `getStatus`, `getConfig`, `getFormationInfo`, `getAsyncConfig`, `getA2aConfig`, `getLoggingConfig`, `getLoggingDestinations`, `getOverlordConfig`, `getOverlordPersona`, `getLlmSettings` |
| Streaming | `streamEvents`, `streamLogs`, `streamRequest` |
| User | `resolveUser` |

## Webhook Verification

```kotlin
import dev.muxi.sdk.Webhook
import dev.muxi.sdk.WebhookVerificationException

fun handleWebhook(payload: String, signature: String?) {
    val secret = System.getenv("WEBHOOK_SECRET")
    
    if (!Webhook.verifySignature(payload, signature, secret)) {
        throw SecurityException("Invalid signature")
    }
    
    val event = Webhook.parse(payload)
    
    when (event.status) {
        "completed" -> event.content.filter { it.type == "text" }.forEach { println(it.text) }
        "failed" -> println("Error: ${event.error?.message}")
        "awaiting_clarification" -> println("Question: ${event.clarification?.question}")
    }
}
```

## Testing Locally

```bash
cd kotlin
./gradlew test
```
