package ai.jetbrains.code.mellum.sdk

import ai.grazie.code.features.common.completion.context.strategy.contexts
import ai.grazie.code.features.common.completion.context.strategy.legacyDirectoryStrategy
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

    fun getCompletion(file: Path, position: Position): String {
        val fileContent = runBlocking { documentProvider.charsByPath(file) } ?: return ""
        val offset = position.toOffset(fileContent)
        logger.info { "Executing completion at offset $offset in file $file" }
        val textBeforeCursor = fileContent.substring(0, offset)
        val textAfterCursor = fileContent.substring(offset, fileContent.length)

        val languageSet = LanguageSet.Completion.Jet.Kotlin
        val strategy = legacyDirectoryStrategy(
            queriedLanguages = languageSet,
            languageProvider = languageProvider,
            fs = fileSystemProvider,
            workspaceProvider = workspaceProvider,
            documentProvider = documentProvider,
            configurations = emptyMap(),
            preScoreLimit = Int.MAX_VALUE,
            softTimeout = 50.milliseconds,
        )

        val contextBuilder = StringBuilder()
        runBlocking {
            strategy.contexts(file, offset).collect { context ->
                contextBuilder.append("<filename>${context.path}\n")
                contextBuilder.append(context.content)
            }
        }
        logger.info { "Prepared context: ${contextBuilder.length} chars" }

        val completion = runBlocking {
            completionExecutor.execute(
                completionPrompt = textBeforeCursor, system = contextBuilder.toString(), suffix = textAfterCursor
            )
        }

        logger.info { "Got completion: $completion" }
        return completion
    }
}
