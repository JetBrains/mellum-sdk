package ai.jetbrains.code.mellum.sdk

import ai.grazie.code.features.common.completion.context.CollectedContext
import ai.grazie.code.features.common.completion.context.strategy.contexts
import ai.grazie.code.features.common.completion.context.strategy.legacyIntersectionOverUnionStrategy
import ai.grazie.code.files.model.*
import ai.grazie.code.files.model.DocumentProvider.Position
import ai.jetbrains.code.mellum.sdk.ollama.DEFAULT_OLLAMA_MODEL_ID
import ai.jetbrains.code.mellum.sdk.ollama.OllamaClient
import ai.jetbrains.code.mellum.sdk.ollama.OllamaCompletionExecutor
import ai.koog.rag.base.files.FileSystemProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds


class MellumCompletionService<Path, Document>(
    private val documentProvider: DocumentProvider<Path, Document>,
    private val languageProvider: LanguageProvider<Path>,
    private val fileSystemProvider: FileSystemProvider.ReadOnly<Path>,
    private val workspaceProvider: WorkspaceProvider<Path>,
    private val ollamaClient: OllamaClient = OllamaClient(),
    private val modelName: String = DEFAULT_OLLAMA_MODEL_ID,
    private val tokenLimit: Int = 8192
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

        val contextItems = strategy.contexts(file, offset).toList()
        logger.info { "Collected ${contextItems.size} context items" }

        val finalPrompt = prepareTokenLimitedPrompt(
            prefix = textBeforeCursor,
            suffix = textAfterCursor,
            filePath = file,
            contextItems = contextItems,
            tokenLimit = tokenLimit
        )

        val completion = completionExecutor.execute(finalPrompt)

        logger.info { "Got completion: $completion" }
        return@runBlocking completion
    }

    private fun approximateTokenCount(text: String): Int {
        return (text.length / 4) + 1
    }

    private suspend fun prepareTokenLimitedPrompt(
        prefix: String,
        suffix: String,
        filePath: Path,
        contextItems: List<CollectedContext<Path>>,
        tokenLimit: Int
    ): String {
        val essentialTagsTokens = FimTags.entries.sumOf { approximateTokenCount(it.charValue) }
        var availableTokens = tokenLimit - essentialTagsTokens
        val result = StringBuilder()

        // 1. Process file path (first priority)
        val filePathString = resolveActualPathToString(filePath)
        val filePathTruncatedResult = asIsOrTruncated(filePathString, availableTokens, true)
        val filePathToUse = filePathTruncatedResult.first
        availableTokens -= filePathTruncatedResult.second

        // 2. Process prefix (second priority)
        val prefixTruncatedResult = asIsOrTruncated(prefix, availableTokens, true)
        val prefixToUse = prefixTruncatedResult.first
        availableTokens -= prefixTruncatedResult.second

        // 3. Process suffix (third priority)
        val suffixTruncatedResult = asIsOrTruncated(suffix, availableTokens, false)
        val suffixToUse = suffixTruncatedResult.first
        availableTokens -= suffixTruncatedResult.second

        // 4. Process context items (last priority, but first in prompt)
        for (item in contextItems) {
            if (availableTokens <= 0) break

            val contextPath = resolveActualPathToString(item.path)
            val contextItemWithTag = "${FimTags.FILENAME.charValue}$contextPath\n${item.content}"
            val contextItemTokens = approximateTokenCount(contextItemWithTag)

            if (contextItemTokens <= availableTokens) {
                result.append(contextItemWithTag)
                availableTokens -= contextItemTokens
            }
        }

        // Build the allocated part of the prompt
        result.append("${FimTags.FILENAME.charValue}$filePathToUse")
        result.append("${FimTags.PREFIX.charValue}$prefixToUse")
        result.append("${FimTags.SUFFIX.charValue}$suffixToUse")
        result.append(FimTags.MIDDLE.charValue)
        return result.toString()
    }

    // Returns consumed number of tokens
    private fun asIsOrTruncated(text: String, availableTokens: Int, takeLast: Boolean): Pair<String, Int> {
        val textTokens = approximateTokenCount(text)
        return if (textTokens <= availableTokens) {
            Pair(text, textTokens)
        } else {
            val charsToKeep = availableTokens * 4
            if (takeLast) {
                Pair(text.takeLast(charsToKeep), availableTokens)
            } else {
                Pair(text.take(charsToKeep), availableTokens)
            }
        }
    }

    private suspend fun resolveActualPathToString(filePath: Path): String {
        val relativeFilePath = fileSystemProvider.relativize(
            workspaceProvider.findWorkspaceRoot(filePath) ?: filePath, filePath
        )
        return when (relativeFilePath) {
            null, "" -> fileSystemProvider.toAbsolutePathString(filePath)
            else -> relativeFilePath
        }
    }

    private enum class FimTags(val charValue: String) {
        FILENAME("<filename>"),
        SUFFIX("<fim_suffix>"),
        PREFIX("<fim_prefix>"),
        MIDDLE("<fim_middle>");
    }
}
