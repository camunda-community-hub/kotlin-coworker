package org.camunda.community.extension.coworker.spring.property

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.process.test.assertions.BpmnAssert
import io.camunda.zeebe.spring.client.config.ZeebeClientStarterAutoConfiguration
import io.camunda.zeebe.spring.test.ZeebeSpringTest
import kotlinx.coroutines.future.await
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.extension.coworker.spring.CoworkerAutoConfiguration
import org.camunda.community.extension.coworker.spring.annotation.Coworker
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

private const val FROM_OVERRIDE_VAR_NAME = "from_override"
private const val FROM_ANNOTATION_VAR_NAME = "from_annotation"
private const val OVERRIDABLE_WORKER_TYPE = "overridable-worker"

@ZeebeSpringTest
@SpringBootTest(
    classes = [
        JacksonAutoConfiguration::class,
        ZeebeClientStarterAutoConfiguration::class,
        OverridePropertiesCoworkerIntegrationTest::class,
        CoworkerAutoConfiguration::class
    ]
)
@TestPropertySource(
    properties = [
        "zeebe.client.worker.override.overridable-worker.fetch-variables[0]=${FROM_OVERRIDE_VAR_NAME}"
    ]
)
class OverridePropertiesCoworkerIntegrationTest {

    private val fromOverrideVar = "fromOverride"
    private val fromAnnotationVar = "fromAnnotation"

    @Autowired
    private lateinit var zeebeClient: ZeebeClient

    @Coworker(type = OVERRIDABLE_WORKER_TYPE, fetchVariables = "#{new String[1] {'${FROM_ANNOTATION_VAR_NAME}'}}")
    suspend fun testCoworker(activatedJob: ActivatedJob, jobClient: JobClient) {
        assertThat(activatedJob.variablesAsMap)
            .hasSize(1)
            .containsEntry(FROM_OVERRIDE_VAR_NAME, fromOverrideVar)
            .doesNotContainEntry(FROM_ANNOTATION_VAR_NAME, fromAnnotationVar)

        jobClient.newCompleteCommand(activatedJob).send().await()
    }

    @Disabled("https://github.com/camunda-community-hub/kotlin-coworker/issues/59")
    @Test
    fun `should override works for fetch-variables`() {
        val model = Bpmn
            .createExecutableProcess()
            .startEvent()
            .serviceTask().zeebeJobType(OVERRIDABLE_WORKER_TYPE).zeebeJobRetries("0")
            .endEvent()
            .done()

        val deploymentEvent = zeebeClient
            .newDeployResourceCommand()
            .addProcessModel(model, "overridable-worker.bpmn")
            .send()
            .join()

        val result = zeebeClient
            .newCreateInstanceCommand()
            .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
            .variables(mapOf(FROM_OVERRIDE_VAR_NAME to fromOverrideVar, FROM_ANNOTATION_VAR_NAME to fromAnnotationVar))
            .withResult()
            .send()
            .join()

        BpmnAssert.assertThat(result).isCompleted.hasNoIncidents()
    }
}
