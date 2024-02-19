package org.camunda.community.extension.coworker.spring.annotation

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.spring.client.annotation.processor.AbstractZeebeAnnotationProcessor
import io.camunda.zeebe.spring.client.bean.ClassInfo
import org.camunda.community.extension.coworker.spring.annotation.customization.CoworkerValueCustomizer
import org.camunda.community.extension.coworker.spring.annotation.mapper.MethodToCoworkerMapper
import org.camunda.community.extension.coworker.toCozeebe
import org.springframework.util.ReflectionUtils
import kotlin.coroutines.Continuation

class CoworkerAnnotationProcessor(
    private val coworkerManager: CoworkerManager,
    private val methodToCoworkerMapper: MethodToCoworkerMapper,
    private val coworkerValueCustomizers: List<CoworkerValueCustomizer>,
) : AbstractZeebeAnnotationProcessor() {
    private val coworkerValues: MutableList<CoworkerValue> = mutableListOf()

    override fun isApplicableFor(classInfo: ClassInfo): Boolean = classInfo.hasMethodAnnotation(Coworker::class.java)

    override fun configureFor(classInfo: ClassInfo) {
        ReflectionUtils.doWithMethods(
            classInfo.targetClass,
            { method -> methodToCoworkerMapper.map(classInfo, method)?.let { coworkerValues.add(it) } },
            ReflectionUtils.USER_DECLARED_METHODS
                .and { method ->
                    method.parameterTypes.contains(Continuation::class.java)
                },
        )
    }

    override fun start(zeebeClient: ZeebeClient) {
        val cozeebe = zeebeClient.toCozeebe()
        coworkerValues
            .asSequence()
            .onEach { coworkerValue -> coworkerValueCustomizers.forEach { it.customize(coworkerValue) } }
            .filter(CoworkerValue::enabled)
            .forEach {
                coworkerManager.openWorker(it, cozeebe)
            }
    }

    override fun stop(zeebeClient: ZeebeClient) {
        coworkerManager.closeAllWorkers()
    }
}
