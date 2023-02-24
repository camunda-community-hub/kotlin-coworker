package org.camunda.community.extension.coworker.spring

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.spring.client.ZeebeClientSpringConfiguration
import io.camunda.zeebe.spring.client.annotation.processor.AbstractZeebeAnnotationProcessor
import io.camunda.zeebe.spring.client.annotation.processor.AnnotationProcessorConfiguration
import io.camunda.zeebe.spring.client.config.ZeebeClientStarterAutoConfiguration
import kotlinx.coroutines.slf4j.MDCContext
import org.camunda.community.extension.coworker.Cozeebe
import org.camunda.community.extension.coworker.spring.annotation.CoworkerAnnotationProcessor
import org.camunda.community.extension.coworker.spring.annotation.CoworkerManager
import org.camunda.community.extension.coworker.spring.error.DefaultSpringZeebeErrorHandler
import org.camunda.community.extension.coworker.zeebe.worker.JobCoroutineContextProvider
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@AutoConfigureAfter(ZeebeClientStarterAutoConfiguration::class, ZeebeClientSpringConfiguration::class)
@AutoConfigureBefore(AnnotationProcessorConfiguration::class)
@ConditionalOnClass(ZeebeClientStarterAutoConfiguration::class, ZeebeClient::class, ZeebeClientSpringConfiguration::class, AnnotationProcessorConfiguration::class)
open class CoworkerAutoConfiguration {

    @Bean
    open fun coZeebe(zeebeClient: ZeebeClient): Cozeebe = LazyCozeebe(zeebeClient)

    @ConditionalOnMissingBean
    @Bean
    open fun defaultJobErrorHandler(): JobErrorHandler {
        return DefaultSpringZeebeErrorHandler()
    }

    @ConditionalOnMissingBean
    @Bean
    open fun defaultJobCoroutineContextProvider(): JobCoroutineContextProvider {
        return JobCoroutineContextProvider{ _ -> MDCContext() }
    }

    @Bean
    open fun coworkerManager(
        jobCoroutineContextProvider: JobCoroutineContextProvider,
        jobErrorHandler: JobErrorHandler
    ): CoworkerManager = CoworkerManager(
        jobCoroutineContextProvider,
        jobErrorHandler
    )

    @Bean
    open fun coworkerAnnotationProcessor(
        coworkerManager: CoworkerManager
    ): AbstractZeebeAnnotationProcessor = CoworkerAnnotationProcessor(coworkerManager)
}
