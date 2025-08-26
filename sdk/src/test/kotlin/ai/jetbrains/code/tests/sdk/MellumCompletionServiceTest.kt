package ai.jetbrains.code.tests.sdk

import ai.grazie.code.files.jvm.JVMDocumentProvider
import ai.grazie.code.files.jvm.JVMLanguageProvider
import ai.grazie.code.files.jvm.JVMWorkspaceProvider
import ai.grazie.code.files.model.DocumentProvider.Position
import ai.jetbrains.code.fixtures.InjectMellumOllamaTestFixture
import ai.jetbrains.code.fixtures.MellumOllamaTestFixture
import ai.jetbrains.code.fixtures.MellumOllamaTestFixtureExtension
import ai.jetbrains.code.mellum.sdk.MellumCompletionService
import ai.koog.rag.base.files.JVMFileSystemProvider
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path
import java.nio.file.Paths

@ExtendWith(MellumOllamaTestFixtureExtension::class)
@Tag("ollama")
class MellumCompletionServiceTest {
    companion object {
        @field:InjectMellumOllamaTestFixture
        lateinit var ollamaFixture: MellumOllamaTestFixture
        private val ollamaFixtureClient get() = ollamaFixture.client
    }

    @Test
    fun `test completion`() {
        val testFile = getResource("/TestClass.kt")

        // Document Provider position is zero-based
        val result = MellumCompletionService(
            JVMDocumentProvider,
            JVMLanguageProvider,
            JVMFileSystemProvider.ReadOnly,
            JVMWorkspaceProvider(emptySet()),
            ollamaFixtureClient,
            "JetBrains/Mellum-4b-sft-kotlin"
        ).getCompletion(testFile, Position(2, 23))

        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "Completion result should not be empty")
        assertTrue(result.contains("World!"), "Completion should contain hello world completion, but was $result")
    }

    private fun getResource(name: String): Path {
        return Paths.get(MellumCompletionServiceTest::class.java.getResource(name)!!.toURI())
    }
}
