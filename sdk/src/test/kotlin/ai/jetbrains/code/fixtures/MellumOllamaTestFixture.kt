package ai.jetbrains.code.fixtures

import ai.jetbrains.code.mellum.sdk.ollama.OllamaClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.PullPolicy

// mellum requires at least 11 GB to run
class MellumOllamaTestFixture {
    private val PORT = 11434

    private lateinit var ollamaContainer: GenericContainer<*>

    lateinit var client: OllamaClient

    fun setUp() {
        val ollamaImageUrl = System.getenv("OLLAMA_IMAGE_URL")
        ollamaContainer = GenericContainer(ollamaImageUrl).apply {
            withExposedPorts(PORT)
            withImagePullPolicy(PullPolicy.alwaysPull())
        }
        ollamaContainer.start()

        val host = ollamaContainer.host
        val port = ollamaContainer.getMappedPort(PORT)
        val baseUrl = "http://$host:$port"
        waitForOllamaServer(baseUrl)

        client = OllamaClient(baseUrl)
    }

    fun tearDown() {
        ollamaContainer.stop()
    }

    private fun waitForOllamaServer(baseUrl: String) {
        val httpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 1000
            }
        }

        val maxAttempts = 100

        runBlocking {
            for (attempt in 1..maxAttempts) {
                try {
                    val response = httpClient.get(baseUrl)
                    if (response.status.isSuccess()) {
                        httpClient.close()
                        return@runBlocking
                    }
                } catch (e: Exception) {
                    if (attempt == maxAttempts) {
                        httpClient.close()
                        throw IllegalStateException(
                            "Ollama server didn't respond after $maxAttempts attemps", e
                        )
                    }
                }
                delay(1000)
            }
        }
    }
}
