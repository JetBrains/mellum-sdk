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
    private val tokenLimit: Int = 8192
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private enum class FimTags(str: String) {
        FILENAME("<filename>"),
        SUFFIX("<fim_suffix>"),
        PREFIX("<fim_prefix>"),
        MIDDLE("<fim_middle>");

        val charLength: Int = str.length
        val charValue: String = str
    }

    private val completionExecutor = OllamaCompletionExecutor(ollamaClient, modelName)

    private fun approximateTokenCount(text: String): Int {
        return (text.length / 4) + 1
    }

    private fun prepareTokenLimitedPrompt(
        prefix: String,
        suffix: String,
        filePath: Path,
        contextItems: List<Pair<Path, String>>,
        tokenLimit: Int
    ): String {
        val essentialTagsTokens = FimTags.entries.sumOf { it.charLength }
        var availableTokens = tokenLimit - essentialTagsTokens
        val result = StringBuilder()

        // 1. Process file path (first priority)
        val filePathString = filePath.toString()
        val filePathTokens = approximateTokenCount(filePathString)
        var filePathToUse = filePathString

        if (filePathTokens <= availableTokens) {
            availableTokens -= filePathTokens
        } else {
            val charsToKeep = availableTokens * 4
            filePathToUse = filePathString.takeLast(charsToKeep) + ""
            availableTokens = 0
        }
        
        // 2. Process prefix (second priority)
        val prefixTokens = approximateTokenCount(prefix)
        var prefixToUse = prefix

        if (prefixTokens <= availableTokens) {
            availableTokens -= prefixTokens
        } else {
            val charsToKeep = availableTokens * 4
            prefixToUse = prefix.takeLast(charsToKeep)
            availableTokens = 0
        }
        
        // 3. Process suffix (third priority)
        val suffixTokens = approximateTokenCount(suffix)
        var suffixToUse = suffix
        
        if (suffixTokens <= availableTokens) {
            availableTokens -= suffixTokens
        } else {
            val charsToKeep = availableTokens * 4
            suffixToUse = suffix.take(charsToKeep)
            availableTokens = 0
        }
        
        // 4. Process context items (last priority, but first in prompt)
        for ((path, content) in contextItems) {
            if (availableTokens <= 0) break
            
            val pathString = path.toString()
            val contextItemWithTag = "${FimTags.FILENAME.charValue}$pathString\n$content"
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

        val contextItems = mutableListOf<Pair<Path, String>>()
        strategy.contexts(file, offset).collect { context ->
            contextItems.add(context.path to context.content)
        }
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
}
