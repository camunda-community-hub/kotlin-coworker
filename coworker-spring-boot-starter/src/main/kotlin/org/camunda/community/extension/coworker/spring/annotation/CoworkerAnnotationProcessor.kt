package org.camunda.community.extension.coworker.spring.annotation

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.spring.client.annotation.processor.AbstractZeebeAnnotationProcessor
import io.camunda.zeebe.spring.client.bean.ClassInfo
import io.camunda.zeebe.spring.client.bean.MethodInfo
import org.camunda.community.extension.coworker.toCozeebe
import org.springframework.util.ReflectionUtils
import kotlin.coroutines.Continuation
import kotlin.jvm.optionals.getOrNull

class CoworkerAnnotationProcessor(
    private val coworkerManager: CoworkerManager
) : AbstractZeebeAnnotationProcessor() {

    private val coworkerValues: MutableList<CoworkerValue> = mutableListOf()
    override fun isApplicableFor(classInfo: ClassInfo): Boolean {
        return classInfo.hasMethodAnnotation(Coworker::class.java)
    }

    override fun configureFor(classInfo: ClassInfo) {
        ReflectionUtils.doWithMethods(
            classInfo.targetClass,
            { method -> readFromMethodInfo(classInfo.toMethodInfo(method))?.let { coworkerValues.add(it) } },
            ReflectionUtils.USER_DECLARED_METHODS
                .and {
                        method -> method.parameterTypes.contains(Continuation::class.java)
                }
        )
    }

    override fun start(zeebeClient: ZeebeClient) {
        val cozeebe = zeebeClient.toCozeebe()
        coworkerValues
            .forEach {
                coworkerManager.openWorker(it, cozeebe)
            }
    }

    override fun stop(zeebeClient: ZeebeClient) {
        coworkerManager.closeAllWorkers()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun readFromMethodInfo(method: MethodInfo): CoworkerValue? {
        return method.getAnnotation(Coworker::class.java)
            .map {
                CoworkerValue(
                    type = it.type,
                    methodInfo = method
                )
            }
            .getOrNull()
    }
}
