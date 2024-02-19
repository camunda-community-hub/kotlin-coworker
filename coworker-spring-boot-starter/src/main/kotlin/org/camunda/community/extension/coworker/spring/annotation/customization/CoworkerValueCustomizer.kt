package org.camunda.community.extension.coworker.spring.annotation.customization

import org.camunda.community.extension.coworker.spring.annotation.CoworkerValue

interface CoworkerValueCustomizer {
    fun customize(coworkerValue: CoworkerValue)
}
