package org.camunda.community.extension.coworker.spring.annotation

import io.camunda.zeebe.spring.client.bean.MethodInfo

data class CoworkerValue(
    val type: String,
    val methodInfo: MethodInfo
)
