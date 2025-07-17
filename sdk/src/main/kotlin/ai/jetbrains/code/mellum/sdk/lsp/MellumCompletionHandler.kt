package ai.jetbrains.code.mellum.sdk.lsp

import ai.grazie.code.files.model.DocumentProvider
import ai.grazie.code.files.model.FileSystemProvider
import ai.grazie.code.files.model.LanguageProvider
import ai.grazie.code.files.model.WorkspaceProvider
import ai.grazie.code.files.model.charsByPath
import ai.grazie.code.files.model.toOffset
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
}

class MellumLspCompletionService(
    private val documentProvider: DocumentProvider<URI, URI>,
    private val languageProvider: LanguageProvider<URI>,
    private val fileSystemProvider: FileSystemProvider.ReadOnly<URI>,
    private val workspaceProvider: WorkspaceProvider<URI>,
    private val ollamaClient: OllamaClient = OllamaClient(),
    private val modelName: String
) : MellumCompletionHandler {
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
            val textOnLineBeforeCursor = fileContent.substring(0, offset).substringAfterLast("\n")
                .trim() // get the last line where completion was triggered
            // concat with line prefix the completion
            val formatCompletionResult = textOnLineBeforeCursor + completionResult.substringBefore("\n#")

            val completionItem = CompletionItem(formatCompletionResult)
            completionItem.insertText = formatCompletionResult

            Either.forLeft(listOf(completionItem))
        }
    }
}
