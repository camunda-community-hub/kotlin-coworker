package org.camunda.community.extension.coworker.spring

import io.camunda.zeebe.spring.test.ZeebeSpringTest
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.extension.coworker.Cozeebe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@ZeebeSpringTest
@SpringBootTest(classes = [CoworkerAutoConfiguration::class])
class CoworkerAutoConfigurationSpringBootTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `should have cozeebe in context`() {
        assertThat(applicationContext.getBean<Cozeebe>()).isNotNull
    }
}
