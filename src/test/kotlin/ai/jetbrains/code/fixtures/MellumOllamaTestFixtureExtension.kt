package ai.jetbrains.code.fixtures

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport.findAnnotatedFields
import org.junit.platform.commons.support.ModifierSupport
import java.lang.reflect.Field

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class InjectMellumOllamaTestFixture

class MellumOllamaTestFixtureExtension : BeforeAllCallback, AfterAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        setupFields(testClass)
    }

    override fun afterAll(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        tearDownFields(testClass)
    }

    private fun findFields(testClass: Class<*>): List<Field> {
        return findAnnotatedFields(
            testClass,
            InjectMellumOllamaTestFixture::class.java,
        ) { field -> ModifierSupport.isStatic(field) && field.type == MellumOllamaTestFixture::class.java }
    }

    private fun setupFields(testClass: Class<*>) {
        findFields(testClass).forEach { field ->
            field.isAccessible = true
            field.set(null, MellumOllamaTestFixture().apply { setUp() })
        }
    }

    private fun tearDownFields(testClass: Class<*>) {
        findFields(testClass).forEach { field ->
            field.isAccessible = true
            (field.get(null) as MellumOllamaTestFixture).tearDown()
            field.set(null, null)
        }
    }
}