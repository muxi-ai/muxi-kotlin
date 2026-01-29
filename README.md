# MUXI Kotlin SDK

Official Kotlin/JVM SDK for the MUXI AI platform.

## Requirements

- Kotlin 1.9+
- JDK 17+

## Installation

Add to your `build.gradle.kts`:

```kotlin
implementation("dev.muxi:muxi-kotlin:0.20260129.0")
```

Or in Groovy `build.gradle`:

```groovy
implementation 'dev.muxi:muxi-kotlin:0.20260129.0'
```

## Quick Start

### Server Management (Control Plane)

```kotlin
import dev.muxi.sdk.*

val server = ServerClient(ServerConfig(
    url = System.getenv("MUXI_SERVER_URL"),
    keyId = System.getenv("MUXI_KEY_ID"),
    secretKey = System.getenv("MUXI_SECRET_KEY")
))

// List formations
val formations = server.listFormations()
println(formations)

// Get server status
val status = server.status()
println("Uptime: ${status?.get("uptime")}s")
```

### Formation Usage (Runtime API)

```kotlin
import dev.muxi.sdk.*
import kotlinx.coroutines.flow.collect

// Connect via server proxy
val client = FormationClient(FormationConfig(
    formationId = "my-bot",
    serverUrl = System.getenv("MUXI_SERVER_URL"),
    adminKey = System.getenv("MUXI_ADMIN_KEY"),
    clientKey = System.getenv("MUXI_CLIENT_KEY")
))

// Chat (non-streaming)
val response = client.chat(mapOf("message" to "Hello!"), userId = "user123")
println(response?.get("message"))

// Chat (streaming with Flow)
client.chatStream(mapOf("message" to "Tell me a story"), userId = "user123")
    .collect { event -> print(event.data) }

// Health check
val health = client.health()
println("Status: ${health?.get("status")}")
```

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

## Error Handling

```kotlin
import dev.muxi.sdk.*

try {
    server.getFormation("nonexistent")
} catch (e: NotFoundException) {
    println("Not found: ${e.message}")
} catch (e: AuthenticationException) {
    println("Auth failed: ${e.message}")
} catch (e: RateLimitException) {
    println("Rate limited. Retry after: ${e.retryAfter}s")
} catch (e: MuxiException) {
    println("Error: ${e.message} (${e.statusCode})")
}
```

## License

MIT
