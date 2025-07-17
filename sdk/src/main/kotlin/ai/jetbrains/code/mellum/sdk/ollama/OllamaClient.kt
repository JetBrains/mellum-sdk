package ai.jetbrains.code.mellum.sdk.ollama

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Client for interacting with the Ollama API.
 *
 * @property url The base URL of the Ollama API server.
 * @property client The HTTP client used for making requests.
 */
class OllamaClient(
    private val url: String = "http://localhost:11434",
    client: HttpClient =  HttpClient(CIO)
) {

    private val client = client.config {
        install(Logging)
        install(ContentNegotiation) {
            json()
        }
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Generate a chat completion for a given set of messages with a provided model.
     *
     * @param request The chat request parameters.
     * @return A flow of chat responses.
     */
    suspend fun completion(request: OllamaCompletionRequestDTO): OllamaCompletionResponseDTO {
        val requestBody = json.encodeToString(OllamaCompletionRequestDTO.serializer(), request)

        val response = client.post("$url/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Non-streaming response
        val result = response.body<OllamaCompletionResponseDTO>()
        return result
    }
}
