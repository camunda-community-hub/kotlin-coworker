package org.camunda.community.extension.coworker.spring.annotation.mapper.impl

import io.camunda.zeebe.spring.client.bean.ClassInfo
import org.camunda.community.extension.coworker.spring.annotation.Coworker
import org.camunda.community.extension.coworker.spring.annotation.CoworkerValue
import org.camunda.community.extension.coworker.spring.annotation.mapper.CoworkerToCoworkerValueMapper
import org.camunda.community.extension.coworker.spring.annotation.mapper.MethodToCoworkerMapper
import java.lang.reflect.Method
import kotlin.jvm.optionals.getOrNull

class MethodToCoworkerMapperImpl(
    private val coworkerToCoworkerValueMapper: CoworkerToCoworkerValueMapper
) : MethodToCoworkerMapper {

    @OptIn(ExperimentalStdlibApi::class)
    override fun map(classInfo: ClassInfo, method: Method): CoworkerValue? {
        return classInfo
            .toMethodInfo(method)
            .getAnnotation(Coworker::class.java)
            .getOrNull()
            ?.let {
                coworkerToCoworkerValueMapper.map(it, classInfo.toMethodInfo(method))
            }
    }
}
