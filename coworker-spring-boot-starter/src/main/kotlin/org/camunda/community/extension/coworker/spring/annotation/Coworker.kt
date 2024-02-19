package org.camunda.community.extension.coworker.spring.annotation

const val ZEEBE_CLIENT_CONFIGURATION_PROPERTIES =
    "beanFactory.getBean(T(io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties))"

/**
 * Annotation to create [org.camunda.community.extension.coworker.zeebe.worker.JobCoworker].
 * Works closely with Spring Zeebe.
 *
 * All property placeholders in all annotation values at first replaced by
 * [org.springframework.core.env.Environment.resolvePlaceholders] that we get from the Spring context and then
 * evaluated by [org.springframework.beans.factory.config.BeanExpressionResolver.evaluate]
 * that we take by
 * getting [org.springframework.beans.factory.config.ConfigurableBeanFactory.getBeanExpressionResolver] from the context.
 *
 * So, feel free to use property placeholders, like `${}` and SpEL expressions, like `#{}` in the values.
 *
 * @see [CoworkerAnnotationProcessor]
 * @see [SpEL](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Coworker(
    /**
     * Coworker's type.
     * By default, we are getting it from the configuration properties
     * bean's method [io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.getDefaultJobWorkerType]
     * and if it is null we are getting it by accessing
     * the [io.camunda.zeebe.spring.client.bean.MethodInfo.getMethodName] method.
     *
     * There is an object exists in the evaluation context with
     * type [io.camunda.zeebe.spring.client.bean.MethodInfo] with the key `methodInfo`.
     * Feel free to use it in the expressions if you need it.
     *
     * The evaluated value should be [String].
     */
    val type: String = "#{$ZEEBE_CLIENT_CONFIGURATION_PROPERTIES.getDefaultJobWorkerType() ?: methodInfo.getMethodName()}",
    /**
     * Coworker's type.
     * By default, we are getting it from the configuration properties
     * bean's method [io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.getDefaultJobWorkerName]
     * and if it is null we are getting it by summarize
     * [io.camunda.zeebe.spring.client.bean.MethodInfo.getBeanName], '#',
     * [io.camunda.zeebe.spring.client.bean.MethodInfo.getMethodName].
     *
     * In the evaluation context, two objects exist:
     * 1. Key: `methodInfo`, value type: [io.camunda.zeebe.spring.client.bean.MethodInfo],
     * 2. Key: `type`, value type: [String] - this is the current [Coworker.type].
     *
     * The evaluated value should be [String].
     */
    val name: String =
        "#{$ZEEBE_CLIENT_CONFIGURATION_PROPERTIES.getDefaultJobWorkerName() ?:" +
            " methodInfo.getBeanName() + '#' + methodInfo.getMethodName()}",
    /**
     * Coworker's job timeout.
     * By default, we are getting it from the configuration properties
     * bean's method [io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.getDefaultJobTimeout].
     *
     * In the evaluation context, two objects exist:
     * 1. Key: `methodInfo`, value type: [io.camunda.zeebe.spring.client.bean.MethodInfo],
     * 2. Key: `type`, value type: [String] - this is the current [Coworker.type].
     *
     * The evaluated value should be [java.time.Duration].
     */
    val timeout: String = "#{$ZEEBE_CLIENT_CONFIGURATION_PROPERTIES.getDefaultJobTimeout()}",
    /**
     * Coworker's limit of active jobs.
     * By default, we are getting it from the configuration properties
     * bean's method [io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.getDefaultJobWorkerMaxJobsActive].
     *
     * In the evaluation context, two objects exist:
     * 1. Key: `methodInfo`, value type: [io.camunda.zeebe.spring.client.bean.MethodInfo],
     * 2. Key: `type`, value type: [String] - this is the current [Coworker.type].
     *
     * The evaluated value should be [Integer].
     */
    val maxJobsActive: String = "#{$ZEEBE_CLIENT_CONFIGURATION_PROPERTIES.getDefaultJobWorkerMaxJobsActive()}",
    /**
     * Coworker's request timeout.
     * By default, we are getting it from the configuration properties
     * bean's method [io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.getRequestTimeout].
     *
     * In the evaluation context, two objects exist:
     * 1. Key: `methodInfo`, value type: [io.camunda.zeebe.spring.client.bean.MethodInfo],
     * 2. Key: `type`, value type: [String] - this is the current [Coworker.type].
     *
     * The evaluated value should be [java.time.Duration].
     */
    val requestTimeout: String = "#{$ZEEBE_CLIENT_CONFIGURATION_PROPERTIES.getRequestTimeout()}",
    /**
     * Coworker's poll interval.
     * By default, we are getting it from the configuration properties
     * bean's method [io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.getDefaultJobPollInterval].
     *
     * In the evaluation context, two objects exist:
     * 1. Key: `methodInfo`, value type: [io.camunda.zeebe.spring.client.bean.MethodInfo],
     * 2. Key: `type`, value type: [String] - this is the current [Coworker.type].
     *
     * The evaluated value should be [java.time.Duration].
     */
    val pollInterval: String = "#{$ZEEBE_CLIENT_CONFIGURATION_PROPERTIES.getDefaultJobPollInterval()}",
    /**
     * What variables should be fetched by this coworker.
     * Also, please note that this array will be summarized with variables that annotated
     * [io.camunda.zeebe.spring.client.annotation.ZeebeVariable] and [io.camunda.zeebe.spring.client.annotation.Variable].
     * The default value is an empty array.
     *
     * In the evaluation context, two objects exist:
     * 1. Key: `methodInfo`, value type: [io.camunda.zeebe.spring.client.bean.MethodInfo],
     * 2. Key: `type`, value type: [String] - this is the current [Coworker.type].
     *
     * The evaluated value should be [Array] of [String].
     */
    val fetchVariables: String = "#{new String[0]}",
    /**
     * Should be all variable fetched despite other settings. The default value is false.
     *
     * In the evaluation context, two objects exist:
     * 1. Key: `methodInfo`, value type: [io.camunda.zeebe.spring.client.bean.MethodInfo],
     * 2. Key: `type`, value type: [String] - this is the current [Coworker.type].
     *
     * The evaluated value should be [Boolean].
     */
    val forceFetchAllVariables: String = "#{false}",
    /**
     * Should the Coworker be enabled. The default value is true.
     *
     * In the evaluation context, two objects exist:
     * 1. Key: `methodInfo`, value type: [io.camunda.zeebe.spring.client.bean.MethodInfo],
     * 2. Key: `type`, value type: [String] - this is the current [Coworker.type].
     *
     * The evaluated value should be [Boolean].
     */
    val enabled: String = "#{true}",
)
