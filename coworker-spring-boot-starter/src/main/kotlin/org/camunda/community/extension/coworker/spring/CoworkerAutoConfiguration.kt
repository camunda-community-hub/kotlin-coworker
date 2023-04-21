package org.camunda.community.extension.coworker.spring

import com.fasterxml.jackson.databind.ObjectMapper
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.JsonMapper
import io.camunda.zeebe.client.impl.ZeebeObjectMapper
import io.camunda.zeebe.spring.client.ZeebeClientSpringConfiguration
import io.camunda.zeebe.spring.client.annotation.processor.AbstractZeebeAnnotationProcessor
import io.camunda.zeebe.spring.client.annotation.processor.AnnotationProcessorConfiguration
import io.camunda.zeebe.spring.client.config.ZeebeClientStarterAutoConfiguration
import io.camunda.zeebe.spring.client.metrics.DefaultNoopMetricsRecorder
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties
import kotlinx.coroutines.slf4j.MDCContext
import org.camunda.community.extension.coworker.Cozeebe
import org.camunda.community.extension.coworker.spring.annotation.CoworkerAnnotationProcessor
import org.camunda.community.extension.coworker.spring.annotation.CoworkerManager
import org.camunda.community.extension.coworker.spring.annotation.customization.CoworkerValueCustomizer
import org.camunda.community.extension.coworker.spring.annotation.customization.impl.PropertyBasedCoworkerCustomizer
import org.camunda.community.extension.coworker.spring.annotation.evaluation.AnnotationValueEvaluator
import org.camunda.community.extension.coworker.spring.annotation.evaluation.impl.SpringContextSpelAnnotationValueEvaluator
import org.camunda.community.extension.coworker.spring.annotation.mapper.CoworkerToCoworkerValueMapper
import org.camunda.community.extension.coworker.spring.annotation.mapper.MethodToCoworkerMapper
import org.camunda.community.extension.coworker.spring.annotation.mapper.impl.CoworkerToCoworkerValueMapperImpl
import org.camunda.community.extension.coworker.spring.annotation.mapper.impl.MethodToCoworkerMapperImpl
import org.camunda.community.extension.coworker.spring.error.DefaultSpringZeebeErrorHandler
import org.camunda.community.extension.coworker.zeebe.worker.JobCoroutineContextProvider
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration
@AutoConfigureAfter(ZeebeClientStarterAutoConfiguration::class, ZeebeClientSpringConfiguration::class)
@AutoConfigureBefore(AnnotationProcessorConfiguration::class)
@ConditionalOnClass(
    ZeebeClientStarterAutoConfiguration::class,
    ZeebeClient::class,
    ZeebeClientSpringConfiguration::class,
    AnnotationProcessorConfiguration::class
)
open class CoworkerAutoConfiguration {

    @Bean
    open fun coZeebe(zeebeClient: ZeebeClient): Cozeebe = LazyCozeebe(zeebeClient)

    @ConditionalOnMissingBean
    @Bean
    open fun defaultJobErrorHandler(): JobErrorHandler = DefaultSpringZeebeErrorHandler()

    @ConditionalOnMissingBean
    @Bean
    open fun defaultJobCoroutineContextProvider(): JobCoroutineContextProvider =
        JobCoroutineContextProvider { _ -> MDCContext() }

    @ConditionalOnMissingBean
    @Bean
    open fun jsonMapper(objectMapper: ObjectMapper): JsonMapper = ZeebeObjectMapper(objectMapper)

    @ConditionalOnMissingBean
    @Bean
    open fun metricsRecorder(): MetricsRecorder = DefaultNoopMetricsRecorder()

    @Bean
    open fun coworkerManager(
        jobCoroutineContextProvider: JobCoroutineContextProvider,
        jobErrorHandler: JobErrorHandler,
        jsonMapper: JsonMapper,
        metricsRecorder: MetricsRecorder
    ): CoworkerManager = CoworkerManager(
        jobCoroutineContextProvider,
        jobErrorHandler,
        jsonMapper,
        metricsRecorder
    )

    @Bean
    open fun annotationValueEvaluator(
        configurableBeanFactory: ConfigurableBeanFactory,
        environment: Environment
    ): AnnotationValueEvaluator = SpringContextSpelAnnotationValueEvaluator(configurableBeanFactory, environment)

    @Bean
    open fun coworkerToCoworkerValueMapper(
        annotationValueEvaluator: AnnotationValueEvaluator
    ): CoworkerToCoworkerValueMapper = CoworkerToCoworkerValueMapperImpl(annotationValueEvaluator)

    @Bean
    open fun methodToCoworkerMapper(
        coworkerToCoworkerValueMapper: CoworkerToCoworkerValueMapper
    ): MethodToCoworkerMapper = MethodToCoworkerMapperImpl(
        coworkerToCoworkerValueMapper
    )

    @Bean
    open fun propertyBasedCoworkerCustomizer(
        zeebeClientConfigurationProperties: ZeebeClientConfigurationProperties
    ): CoworkerValueCustomizer = PropertyBasedCoworkerCustomizer(zeebeClientConfigurationProperties)

    @Bean
    open fun coworkerAnnotationProcessor(
        coworkerManager: CoworkerManager,
        methodToCoworkerMapper: MethodToCoworkerMapper,
        coworkerValueCustomizers: List<CoworkerValueCustomizer>
    ): AbstractZeebeAnnotationProcessor = CoworkerAnnotationProcessor(
        coworkerManager,
        methodToCoworkerMapper,
        coworkerValueCustomizers
    )
}
