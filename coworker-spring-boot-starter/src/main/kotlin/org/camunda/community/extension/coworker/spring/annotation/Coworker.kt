package org.camunda.community.extension.coworker.spring.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Coworker(
    val type: String
)
