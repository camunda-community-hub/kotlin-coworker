package org.camunda.community.extension.coworker.spring

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.spring.test.ZeebeSpringTest
import kotlinx.coroutines.slf4j.MDCContext
import mu.KLogging
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.extension.coworker.Cozeebe
import org.camunda.community.extension.coworker.spring.annotation.CoworkerManager
import org.camunda.community.extension.coworker.spring.error.DefaultSpringZeebeErrorHandler
import org.camunda.community.extension.coworker.zeebe.worker.JobCoroutineContextProvider
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

@ZeebeSpringTest
@SpringBootTest(classes = [CoworkerAutoConfigurationSpringBootTest.TestZeebeConfiguration::class, CoworkerAutoConfiguration::class])
class CoworkerAutoConfigurationSpringBootTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `should have cozeebe in context`() {
        assertThat(applicationContext.getBean<Cozeebe>()).isNotNull
    }

    @Test
    fun `should have CoworkerManager in the context`() {
        assertThat(applicationContext.getBean<CoworkerManager>()).isNotNull
    }

    @TestConfiguration
    open class TestZeebeConfiguration {
        @Bean
        open fun customJobCoroutineContextProvider(): JobCoroutineContextProvider = MyJobCoroutineContextProvider()

        @Bean
        open fun customErrorHandler(): JobErrorHandler {
            val defaultJobErrorHandler = DefaultSpringZeebeErrorHandler()
            return JobErrorHandler { e, activatedJob, jobClient ->
                logger.error(e) { "Got error: ${e.message}, on job: $activatedJob" }
                defaultJobErrorHandler.handleError(e, activatedJob, jobClient)
            }
        }

        companion object: KLogging()

        class MyJobCoroutineContextProvider: JobCoroutineContextProvider {
            override fun provide(job: ActivatedJob) = MDCContext()
        }
    }
}
