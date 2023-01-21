package org.camunda.community.extension.coworker.spring

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.spring.client.ZeebeClientSpringConfiguration
import io.camunda.zeebe.spring.client.config.ZeebeClientStarterAutoConfiguration
import org.camunda.community.extension.coworker.Cozeebe
import org.camunda.community.extension.coworker.toCozeebe
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@AutoConfigureAfter(ZeebeClientStarterAutoConfiguration::class, ZeebeClientSpringConfiguration::class)
@ConditionalOnClass(ZeebeClientStarterAutoConfiguration::class, ZeebeClient::class, ZeebeClientSpringConfiguration::class)
open class CoworkerAutoConfiguration {

    @Bean
    open fun coZeebe(zeebeClient: ZeebeClient): Cozeebe {
        return zeebeClient.toCozeebe()
    }
}
