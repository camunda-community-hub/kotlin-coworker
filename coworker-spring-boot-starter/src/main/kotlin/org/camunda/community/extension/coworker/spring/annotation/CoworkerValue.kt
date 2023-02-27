package org.camunda.community.extension.coworker.spring.annotation

import io.camunda.zeebe.spring.client.bean.MethodInfo
import kotlin.time.Duration

data class CoworkerValue(
    val methodInfo: MethodInfo,
    val type: String,
    val name: String,
    val timeout: Duration,
    val maxJobsActive: Int,
    val requestTimeout: Duration,
    val pollInterval: Duration,
    val fetchVariables: List<String>,
    val enabled: Boolean
)
