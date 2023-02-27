package org.camunda.community.extension.coworker.spring.annotation.evaluation.impl

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.extension.coworker.spring.annotation.scope.ImmutableMapScope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.BeanExpressionResolver
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.core.env.Environment

class SpringContextSpelAnnotationValueEvaluatorTest {
    private lateinit var springContextSpelAnnotationValueEvaluator: SpringContextSpelAnnotationValueEvaluator
    private lateinit var configurableBeanFactory: ConfigurableBeanFactory
    private lateinit var mockBeanExpressionResolver: BeanExpressionResolver
    private lateinit var environment: Environment

    @BeforeEach
    fun setUp() {
        mockBeanExpressionResolver = mockk()
        configurableBeanFactory = mockk {
            every { beanExpressionResolver } returns mockBeanExpressionResolver
        }
        environment = mockk()
        springContextSpelAnnotationValueEvaluator = SpringContextSpelAnnotationValueEvaluator(
            configurableBeanFactory,
            environment
        )
    }

    @Test
    fun `should replace placeholders and evaluate spel`() {
        // given
        val valueToEvaluate = "value"
        val evaluationContext = mapOf("one" to 1)
        val valueWithoutPlaceholders = "valueWithoutPlaceholders"
        every { environment.resolvePlaceholders(valueToEvaluate) } returns valueWithoutPlaceholders
        val resultValue = "resultValue"
        every {
            mockBeanExpressionResolver.evaluate(
                valueWithoutPlaceholders,
                match {
                    it.beanFactory == configurableBeanFactory && it.scope == ImmutableMapScope(evaluationContext)
                }
            )
        } returns resultValue

        // when
        val evaluatedValue = springContextSpelAnnotationValueEvaluator.evaluate<String>(
            valueToEvaluate,
            evaluationContext
        )

        // then
        assertThat(evaluatedValue).isEqualTo(resultValue)
        verify(exactly = 1) { environment.resolvePlaceholders(valueToEvaluate) }
        verify(exactly = 1) {
            mockBeanExpressionResolver.evaluate(
                valueWithoutPlaceholders,
                match {
                    it.beanFactory == configurableBeanFactory && it.scope == ImmutableMapScope(evaluationContext)
                }
            )
        }
    }
}
