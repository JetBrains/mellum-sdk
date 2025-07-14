package ai.jetbrains.code.tests.sdk

import ai.jetbrains.code.mellum.sdk.ollama.OllamaCompletionExecutor
import ai.jetbrains.code.fixtures.InjectMellumOllamaTestFixture
import ai.jetbrains.code.fixtures.MellumOllamaTestFixture
import ai.jetbrains.code.fixtures.MellumOllamaTestFixtureExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertTrue

@ExtendWith(MellumOllamaTestFixtureExtension::class)
@Tag("ollama")
class OllamaCompletionExecutorTest {
    companion object {
        @field:InjectMellumOllamaTestFixture
        lateinit var ollamaFixture: MellumOllamaTestFixture
        private val ollamaFixtureClient get() = ollamaFixture.client
    }

    @Test
    fun test() = runBlocking {
        val prompt = "fun sum(a: Int, b: Int) = "

        val result = OllamaCompletionExecutor(ollamaFixtureClient).execute(prompt)
        assertTrue(result.isNotEmpty(), "Completion result should not be empty")
    }
}
