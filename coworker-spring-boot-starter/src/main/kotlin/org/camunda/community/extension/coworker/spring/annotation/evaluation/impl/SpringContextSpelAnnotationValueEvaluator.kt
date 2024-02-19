package org.camunda.community.extension.coworker.spring.annotation.evaluation.impl

import org.camunda.community.extension.coworker.spring.annotation.evaluation.AnnotationValueEvaluator
import org.camunda.community.extension.coworker.spring.annotation.scope.ImmutableMapScope
import org.springframework.beans.factory.config.BeanExpressionContext
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.core.env.Environment

class SpringContextSpelAnnotationValueEvaluator(
    private val configurableBeanFactory: ConfigurableBeanFactory,
    private val environment: Environment,
) : AnnotationValueEvaluator {
    private val expressionResolver =
        requireNotNull(configurableBeanFactory.beanExpressionResolver) {
            "Please, provide the `beanExpressionResolver` to resolve values in annotations"
        }

    override fun <T> evaluate(
        value: String,
        evaluationContext: Map<String, Any>,
    ): T {
        return expressionResolver.evaluate(
            environment.resolvePlaceholders(value),
            BeanExpressionContext(configurableBeanFactory, ImmutableMapScope(evaluationContext)),
        ) as T
    }
}
