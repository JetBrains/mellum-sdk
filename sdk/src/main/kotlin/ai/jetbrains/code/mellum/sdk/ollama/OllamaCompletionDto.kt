package ai.jetbrains.code.mellum.sdk.ollama

import kotlinx.serialization.Serializable

/**
 * Request for the /api/completion endpoint.
 */
@Serializable
data class OllamaCompletionRequestDTO(
    val model: String,
    val prompt: String,
    val stream: Boolean,
    val raw: Boolean
)

/**
 * Response from the /api/completion endpoint.
 */
@Serializable
@Suppress("PropertyName")
data class OllamaCompletionResponseDTO(
    val model: String? = null,
    val error: String? = null,
    val created_at: String? = null,
    val response: String? = null,
    val done: Boolean? = null,
    val done_reason: String? = null,
    val context: List<Int>? = null,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null,
)

