package org.camunda.community.extension.coworker.spring.error

import io.camunda.zeebe.spring.client.bean.MethodInfo
import java.lang.RuntimeException

fun Exception.stripSpringZeebeExceptionIfNeeded(): Exception {
    return if (this is RuntimeException &&
        this
            .stackTrace
            .firstOrNull()
            ?.let { it.methodName == "invoke" && it.className == MethodInfo::class.qualifiedName } == true
    ) {
        if (this.cause != null) {
            if (this.cause is Exception) {
                this.cause as Exception
            } else {
                this
            }
        } else {
            this
        }
    } else {
        this
    }
}
