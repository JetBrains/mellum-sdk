package ai.jetbrains.code.mellum.sdk.ollama

import io.github.oshai.kotlinlogging.KotlinLogging

// Default model ID if none is provided
const val DEFAULT_OLLAMA_MODEL_ID = "JetBrains/Mellum-4b-sft-python"

/**
 * Executes code-related prompts using LLM Chat services.
 * This executor provides a unified way to handle prompts and obtain responses from LLM services.
 */
class OllamaCompletionExecutor(
    private val client: OllamaClient,
    private val modelName: String = DEFAULT_OLLAMA_MODEL_ID
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Executes the given prompt and returns the response as message.
     *
     * @param completionPrompt The prompt to execute
     * @param system the utility field for context
     * @param suffix the utility field for suffix
     * @return The text response from the LLM service
     * @throws IllegalStateException if no chat service is found for the specified model
     */
    suspend fun execute(completionPrompt: String, system: String = "", suffix: String = ""): String {
        logger.info { "Executing OLLAMA request" }
        val request = OllamaCompletionRequestDTO(
            model = modelName,
            system = system,
            suffix = suffix,
            prompt = completionPrompt,
            stream = false,
        )
        val result = client.completion(request)
        return result.response ?: error("No message in response")
    }
}
