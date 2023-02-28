package org.camunda.community.extension.coworker.spring.annotation.customization.impl

import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties
import org.camunda.community.extension.coworker.spring.annotation.CoworkerValue
import org.camunda.community.extension.coworker.spring.annotation.customization.CoworkerValueCustomizer
import kotlin.time.Duration.Companion.milliseconds

class PropertyBasedCoworkerCustomizer(
    private val zeebeClientConfigurationProperties: ZeebeClientConfigurationProperties
) : CoworkerValueCustomizer {

    override fun customize(coworkerValue: CoworkerValue) {
        zeebeClientConfigurationProperties
            .worker
            .override[coworkerValue.type]
            ?.let {
                if (it.name != null) {
                    coworkerValue.name = it.name
                }
                if (it.timeout != null) {
                    coworkerValue.timeout = it.timeout.milliseconds
                }
                if (it.maxJobsActive != null) {
                    coworkerValue.maxJobsActive = it.maxJobsActive
                }
                if (it.requestTimeout != null) {
                    coworkerValue.requestTimeout = it.requestTimeout.milliseconds
                }
                if (it.pollInterval != null) {
                    coworkerValue.pollInterval = it.pollInterval.milliseconds
                }
                if (it.fetchVariables != null) {
                    coworkerValue.fetchVariables = it.fetchVariables.asList()
                }
                if (it.enabled != null) {
                    coworkerValue.enabled = it.enabled
                }
            }
    }
}
