package ai.jetbrains.code.mellum.sdk

import ai.grazie.code.features.common.completion.context.strategy.contexts
import ai.grazie.code.features.common.completion.context.strategy.legacyIntersectionOverUnionStrategy
import ai.grazie.code.files.model.*
import ai.grazie.code.files.model.DocumentProvider.Position
import ai.jetbrains.code.mellum.sdk.ollama.DEFAULT_OLLAMA_MODEL_ID
import ai.jetbrains.code.mellum.sdk.ollama.OllamaClient
import ai.jetbrains.code.mellum.sdk.ollama.OllamaCompletionExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds


class MellumCompletionService<Path, Document>(
    private val documentProvider: DocumentProvider<Path, Document>,
    private val languageProvider: LanguageProvider<Path>,
    private val fileSystemProvider: FileSystemProvider.ReadOnly<Path>,
    private val workspaceProvider: WorkspaceProvider<Path>,
    private val ollamaClient: OllamaClient = OllamaClient(),
    private val modelName: String = DEFAULT_OLLAMA_MODEL_ID,
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val completionExecutor = OllamaCompletionExecutor(ollamaClient, modelName)

    fun getCompletion(file: Path, position: Position): String = runBlocking {
        val fileContent = documentProvider.charsByPath(file) ?: return@runBlocking ""
        val offset = position.toOffset(fileContent)
        logger.info { "Executing completion at offset $offset in file $file" }
        val textBeforeCursor = fileContent.substring(0, offset)
        val textAfterCursor = fileContent.substring(offset, fileContent.length)

        val languageSet = LanguageSet.of(languageProvider.language(file))
        val strategy = legacyIntersectionOverUnionStrategy(
            queriedLanguages = languageSet,
            languageProvider = languageProvider,
            fs = fileSystemProvider,
            workspaceProvider = workspaceProvider,
            documentProvider = documentProvider,
            configurations = emptyMap(),
            preScoreLimit = Int.MAX_VALUE,
            softTimeout = 50.milliseconds,
        )

        val finalPrompt = buildString {
            strategy.contexts(file, offset).collect { context ->
                append("<filename>${context.path}\n")
                append(context.content)
            }
            logger.info { "Prepared context: ${this.length} chars" }

            append("<filename>")
            append(file)
            append("<fim_suffix>")
            append(textAfterCursor)
            append("<fim_prefix>")
            append(textBeforeCursor)
            append("<fim_middle>")
        }

        val completion = completionExecutor.execute(finalPrompt)

        logger.info { "Got completion: $completion" }
        return@runBlocking completion
    }
}
