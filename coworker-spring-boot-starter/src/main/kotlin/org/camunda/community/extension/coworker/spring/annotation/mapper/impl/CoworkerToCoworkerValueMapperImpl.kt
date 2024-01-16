package org.camunda.community.extension.coworker.spring.annotation.mapper.impl

import io.camunda.zeebe.spring.client.annotation.Variable
import io.camunda.zeebe.spring.client.annotation.ZeebeVariable
import io.camunda.zeebe.spring.client.bean.MethodInfo
import org.camunda.community.extension.coworker.spring.annotation.Coworker
import org.camunda.community.extension.coworker.spring.annotation.CoworkerValue
import org.camunda.community.extension.coworker.spring.annotation.evaluation.AnnotationValueEvaluator
import org.camunda.community.extension.coworker.spring.annotation.mapper.CoworkerToCoworkerValueMapper
import java.time.Duration
import kotlin.time.toKotlinDuration

class CoworkerToCoworkerValueMapperImpl(
    private val annotationValueEvaluator: AnnotationValueEvaluator,
) : CoworkerToCoworkerValueMapper {
    override fun map(
        coworker: Coworker,
        methodInfo: MethodInfo,
    ): CoworkerValue {
        val methodInfoContextMap = mapOf<String, Any>("methodInfo" to methodInfo)
        val type = annotationValueEvaluator.evaluate<String>(coworker.type, methodInfoContextMap)
        val valueContextMap = methodInfoContextMap + mapOf("type" to type)
        return CoworkerValue(
            methodInfo = methodInfo,
            type = type,
            name = annotationValueEvaluator.evaluate(coworker.name, valueContextMap),
            timeout =
                annotationValueEvaluator
                    .evaluate<Duration>(coworker.timeout, valueContextMap)
                    .toKotlinDuration(),
            maxJobsActive = annotationValueEvaluator.evaluate(coworker.maxJobsActive, valueContextMap),
            requestTimeout =
                annotationValueEvaluator.evaluate<Duration>(
                    coworker.requestTimeout,
                    valueContextMap,
                ).toKotlinDuration(),
            pollInterval =
                annotationValueEvaluator.evaluate<Duration>(coworker.pollInterval, valueContextMap)
                    .toKotlinDuration(),
            fetchVariables =
                prepareFetchVariables(
                    methodInfo,
                    coworker.fetchVariables,
                    coworker.forceFetchAllVariables,
                    valueContextMap,
                ),
            enabled = annotationValueEvaluator.evaluate(coworker.enabled, valueContextMap),
        )
    }

    private fun prepareFetchVariables(
        method: MethodInfo,
        fetchVariablesParam: String,
        forceFetchVariablesParam: String,
        contextMap: Map<String, Any>,
    ): List<String> {
        return if (annotationValueEvaluator.evaluate(forceFetchVariablesParam, contextMap)) {
            emptyList()
        } else {
            val fetchVariablesSequence = annotationValueEvaluator.evaluate<Array<String>>(fetchVariablesParam, contextMap).asSequence()
            val variableParameters =
                (
                    method
                        .getParametersFilteredByAnnotation(Variable::class.java)
                        .asSequence() +
                        method
                            .getParametersFilteredByAnnotation(ZeebeVariable::class.java).asSequence()
                )
                    .map {
                        it.parameterName
                    }
            (fetchVariablesSequence + variableParameters).toList()
        }
    }
}
