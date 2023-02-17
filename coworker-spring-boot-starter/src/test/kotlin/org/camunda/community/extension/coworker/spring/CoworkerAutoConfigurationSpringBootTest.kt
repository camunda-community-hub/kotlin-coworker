package org.camunda.community.extension.coworker.spring

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.spring.test.ZeebeSpringTest
import kotlinx.coroutines.slf4j.MDCContext
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.extension.coworker.Cozeebe
import org.camunda.community.extension.coworker.spring.annotation.CoworkerManager
import org.camunda.community.extension.coworker.zeebe.worker.JobCoroutineContextProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

@ZeebeSpringTest
@SpringBootTest(classes = [CoworkerAutoConfiguration::class, CoworkerAutoConfigurationSpringBootTest.TestZeebeConfiguration::class])
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

        class MyJobCoroutineContextProvider: JobCoroutineContextProvider {
            override fun provide(job: ActivatedJob) = MDCContext()
        }
    }
}
