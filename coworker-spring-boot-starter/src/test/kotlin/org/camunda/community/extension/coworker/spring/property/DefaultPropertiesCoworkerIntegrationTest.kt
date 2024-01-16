package org.camunda.community.extension.coworker.spring.property

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.process.test.assertions.BpmnAssert
import io.camunda.zeebe.spring.client.config.ZeebeClientStarterAutoConfiguration
import io.camunda.zeebe.spring.test.ZeebeSpringTest
import kotlinx.coroutines.future.await
import org.camunda.community.extension.coworker.spring.CoworkerAutoConfiguration
import org.camunda.community.extension.coworker.spring.annotation.Coworker
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest

@ZeebeSpringTest
@SpringBootTest(
    classes = [
        JacksonAutoConfiguration::class,
        ZeebeClientStarterAutoConfiguration::class,
        DefaultPropertiesCoworkerIntegrationTest::class,
        CoworkerAutoConfiguration::class,
    ],
)
class DefaultPropertiesCoworkerIntegrationTest {
    @Autowired
    private lateinit var zeebeClient: ZeebeClient

    @Test
    fun `should work correctly with default annotation`() {
        // given
        val model =
            Bpmn
                .createExecutableProcess()
                .startEvent()
                .serviceTask()
                // same as org.camunda.community.extension.coworker.spring.property.DefaultPropertiesCoworkerIntegrationTest.testWorker method name
                .zeebeJobType("testWorker")
                .endEvent()
                .done()

        val deploymentEvent =
            zeebeClient
                .newDeployResourceCommand()
                .addProcessModel(model, "default-annotation.bpmn")
                .send()
                .join()

        // when
        val result =
            zeebeClient
                .newCreateInstanceCommand()
                .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
                .withResult()
                .send()
                .join()

        // then
        BpmnAssert.assertThat(result).isCompleted.hasNoIncidents()
    }

    @Coworker
    suspend fun testWorker(
        activatedJob: ActivatedJob,
        jobClient: JobClient,
    ) {
        jobClient.newCompleteCommand(activatedJob).send().await()
    }
}
