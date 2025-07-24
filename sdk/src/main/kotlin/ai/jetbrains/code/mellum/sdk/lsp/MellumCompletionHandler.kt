package ai.jetbrains.code.mellum.sdk.lsp

import ai.grazie.code.files.model.*
import ai.jetbrains.code.mellum.sdk.MellumCompletionService
import ai.jetbrains.code.mellum.sdk.ollama.OllamaClient
import kotlinx.coroutines.runBlocking
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import java.net.URI
import java.util.concurrent.CompletableFuture

@JsonSegment("mellum")
interface MellumCompletionHandler {

    @JsonRequest("completion")
    fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>>

    @JsonRequest("rawCompletion")
    fun rawCompletion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>>
}

class MellumLspCompletionService(
    private val documentProvider: DocumentProvider<URI, URI>,
    private val languageProvider: LanguageProvider<URI>,
    private val fileSystemProvider: FileSystemProvider.ReadOnly<URI>,
    private val workspaceProvider: WorkspaceProvider<URI>,
    private val ollamaClient: OllamaClient = OllamaClient(),
    private val modelName: String
) : MellumCompletionHandler {
    // Applied little post-formatting for VSCode
    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return CompletableFuture.supplyAsync<Either<List<CompletionItem>, CompletionList>> {
            val documentPosition = DocumentProvider.Position(params.position.line, params.position.character)
            val completionResult = MellumCompletionService(
                documentProvider, languageProvider, fileSystemProvider, workspaceProvider, ollamaClient, modelName
            ).getCompletion(
                URI(params.textDocument.uri), documentPosition
            )
            val fileContent = runBlocking { documentProvider.charsByPath(URI(params.textDocument.uri)) }
                ?: return@supplyAsync Either.forLeft(emptyList())
            val offset = documentPosition.toOffset(fileContent)
            val textOnLineBeforeCursor = fileContent
                .substring(0, offset)
                .substringAfterLast("\n") // get the last line
                .substringAfterLast(" ") // get the last word before completion if present
                .trim()
            val formatCompletionResult = textOnLineBeforeCursor + completionResult

            val completionItem = CompletionItem(formatCompletionResult)
            completionItem.insertText = formatCompletionResult

            Either.forLeft(listOf(completionItem))
        }
    }

    // Returns the raw completion result without post-formatting
    override fun rawCompletion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return CompletableFuture.supplyAsync<Either<List<CompletionItem>, CompletionList>> {
            val documentPosition = DocumentProvider.Position(params.position.line, params.position.character)
            val completionResult = MellumCompletionService(
                documentProvider, languageProvider, fileSystemProvider, workspaceProvider, ollamaClient, modelName
            ).getCompletion(
                URI(params.textDocument.uri), documentPosition
            )

            val completionItem = CompletionItem(completionResult)
            completionItem.insertText = completionResult

            Either.forLeft(listOf(completionItem))
        }
    }
}
