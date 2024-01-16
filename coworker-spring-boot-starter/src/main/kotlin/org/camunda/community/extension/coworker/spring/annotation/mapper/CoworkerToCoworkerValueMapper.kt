package org.camunda.community.extension.coworker.spring.annotation.mapper

import io.camunda.zeebe.spring.client.bean.MethodInfo
import org.camunda.community.extension.coworker.spring.annotation.Coworker
import org.camunda.community.extension.coworker.spring.annotation.CoworkerValue

interface CoworkerToCoworkerValueMapper {
    fun map(
        coworker: Coworker,
        methodInfo: MethodInfo,
    ): CoworkerValue
}
