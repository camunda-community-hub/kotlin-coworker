package org.camunda.community.extension.coworker.spring.annotation.evaluation

interface AnnotationValueEvaluator {

    fun <T> evaluate(value: String, evaluationContext: Map<String, Any>): T
}
