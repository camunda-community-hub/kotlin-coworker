package org.camunda.community.extension.coworker.spring.property

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.process.test.assertions.BpmnAssert
import io.camunda.zeebe.spring.client.annotation.Variable
import io.camunda.zeebe.spring.client.annotation.ZeebeVariable
import io.camunda.zeebe.spring.client.config.ZeebeClientStarterAutoConfiguration
import io.camunda.zeebe.spring.test.ZeebeSpringTest
import kotlinx.coroutines.future.await
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.extension.coworker.spring.CoworkerAutoConfiguration
import org.camunda.community.extension.coworker.spring.annotation.Coworker
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal

@ZeebeSpringTest
@SpringBootTest(
    classes = [
        JacksonAutoConfiguration::class,
        ZeebeClientStarterAutoConfiguration::class,
        SpelValuesCoworkerIntegrationTest::class,
        CoworkerAutoConfiguration::class
    ]
)
@TestPropertySource(
    properties = [
        "firstProp=true"
    ]
)
class SpelValuesCoworkerIntegrationTest {

    @Autowired
    private lateinit var zeebeClient: ZeebeClient

    private val zeebeVar = true
    private val firstVariable = 1
    private val secondVariable = "2"
    private val variable = 1e7

    @Coworker(
        type = "enabled",
        name = "StaticSpelValuesCoworkerIntegrationTest#enabledCoworkerMethod",
        timeout = "#{T(java.time.Duration).ofSeconds(5)}",
        maxJobsActive = "#{1}",
        requestTimeout = "#{T(java.time.Duration).ofMillis(200)}",
        pollInterval = "#{T(java.time.Duration).ofMillis(10)}",
        fetchVariables = "#{new String[2]{'firstVariable', 'secondVariable'}}",
        forceFetchAllVariables = "#{false == true}",
        enabled = "#{\${firstProp}}"
    )
    suspend fun enabledCoworkerMethod(
        activatedJob: ActivatedJob,
        jobClient: JobClient,
        @ZeebeVariable zeebeVar: Boolean,
        @Variable variable: BigDecimal
    ) {
        assertThat(zeebeVar).isEqualTo(this.zeebeVar)
        assertThat(variable).isEqualTo(this.variable.toBigDecimal())
        assertThat(activatedJob.variablesAsMap)
            .hasSize(4)
            .containsEntry("zeebeVar", zeebeVar)
            .containsEntry("firstVariable", firstVariable)
            .containsEntry("secondVariable", secondVariable)
            .hasEntrySatisfying(
                "variable"
            ) { t -> assertThat(t).isEqualTo(this@SpelValuesCoworkerIntegrationTest.variable) }
        jobClient.newCompleteCommand(activatedJob).send().await()
    }

    @Test
    fun `should enabledCoworker called`() {
        // given
        val model = Bpmn
            .createExecutableProcess()
            .startEvent()
            .serviceTask().zeebeJobType("enabled")
            .endEvent()
            .done()

        val deploymentEvent = zeebeClient
            .newDeployResourceCommand()
            .addProcessModel(model, "full-configured-coworker.bpmn")
            .send()
            .join()

        // when
        val result = zeebeClient
            .newCreateInstanceCommand()
            .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
            .variables(
                mapOf(
                    "firstVariable" to firstVariable,
                    "secondVariable" to secondVariable,
                    "zeebeVar" to zeebeVar,
                    "variable" to variable
                )
            )
            .withResult()
            .send()
            .join()

        // then
        BpmnAssert.assertThat(result).isCompleted.hasNoIncidents()
    }
}
