package org.camunda.community.extension.coworker.spring.annotation.mapper

import io.camunda.zeebe.spring.client.bean.ClassInfo
import org.camunda.community.extension.coworker.spring.annotation.CoworkerValue
import java.lang.reflect.Method

interface MethodToCoworkerMapper {

    fun map(classInfo: ClassInfo, method: Method): CoworkerValue?
}
