package dev.muxi.sdk

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationTest {
    
    private lateinit var serverClient: ServerClient
    private lateinit var formationClient: FormationClient
    private var configured = false
    
    private fun env(name: String): String? = System.getenv(name)
    
    private fun requireEnv(name: String): String {
        val value = env(name)
        assumeTrue(value != null && value.isNotEmpty()) { "$name not set" }
        return value!!
    }
    
    @BeforeAll
    fun setup() {
        try {
            val serverUrl = requireEnv("MUXI_SDK_E2E_SERVER_URL")
            val keyId = requireEnv("MUXI_SDK_E2E_KEY_ID")
            val secretKey = requireEnv("MUXI_SDK_E2E_SECRET_KEY")
            val formationId = requireEnv("MUXI_SDK_E2E_FORMATION_ID")
            val clientKey = requireEnv("MUXI_SDK_E2E_CLIENT_KEY")
            val adminKey = requireEnv("MUXI_SDK_E2E_ADMIN_KEY")
            
            serverClient = ServerClient(ServerConfig(
                url = serverUrl,
                keyId = keyId,
                secretKey = secretKey
            ))
            
            formationClient = FormationClient(FormationConfig(
                serverUrl = serverUrl,
                formationId = formationId,
                clientKey = clientKey,
                adminKey = adminKey
            ))
            
            configured = true
        } catch (e: Exception) {
            // Will skip tests
        }
    }
    
    @Test
    fun testServerPing() = runBlocking {
        assumeTrue(configured) { "Not configured" }
        val result = serverClient.ping()
        assertTrue(result >= 0)
    }
    
    @Test
    fun testServerHealth() = runBlocking {
        assumeTrue(configured) { "Not configured" }
        val result = serverClient.health()
        assertNotNull(result)
    }
    
    @Test
    fun testServerStatus() = runBlocking {
        assumeTrue(configured) { "Not configured" }
        val result = serverClient.status()
        assertNotNull(result)
    }
    
    @Test
    fun testServerListFormations() = runBlocking {
        assumeTrue(configured) { "Not configured" }
        val result = serverClient.listFormations()
        assertNotNull(result)
    }
    
    @Test
    fun testFormationHealth() = runBlocking {
        assumeTrue(configured) { "Not configured" }
        val result = formationClient.health()
        assertNotNull(result)
    }
    
    @Test
    fun testFormationGetStatus() = runBlocking {
        assumeTrue(configured) { "Not configured" }
        val result = formationClient.getStatus()
        assertNotNull(result)
    }
    
    @Test
    fun testFormationGetConfig() = runBlocking {
        assumeTrue(configured) { "Not configured" }
        val result = formationClient.getConfig()
        assertNotNull(result)
    }
    
    @Test
    fun testFormationGetAgents() = runBlocking {
        assumeTrue(configured) { "Not configured" }
        val result = formationClient.getAgents()
        assertNotNull(result)
    }
}
