package org.camunda.community.extension.coworker.spring.annotation

import io.camunda.zeebe.spring.client.bean.MethodInfo
import kotlin.time.Duration

data class CoworkerValue(
    val methodInfo: MethodInfo,
    val type: String,
    var name: String,
    var timeout: Duration,
    var maxJobsActive: Int,
    var requestTimeout: Duration,
    var pollInterval: Duration,
    var fetchVariables: List<String>,
    var enabled: Boolean,
)
