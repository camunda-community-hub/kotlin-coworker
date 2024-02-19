package org.camunda.community.extension.coworker.spring

import com.ninjasquad.springmockk.MockkBean
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.spring.client.config.ZeebeClientStarterAutoConfiguration
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder
import io.camunda.zeebe.spring.test.ZeebeSpringTest
import io.mockk.clearMocks
import io.mockk.verify
import kotlinx.coroutines.future.await
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.camunda.community.extension.coworker.spring.annotation.Coworker
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest

private const val PASS_NAME = "pass"
private const val THROW_NAME = "throw"

@ZeebeSpringTest
@SpringBootTest(
    classes = [
        JacksonAutoConfiguration::class,
        ZeebeClientStarterAutoConfiguration::class,
        CoworkerAutoConfiguration::class,
        MetricsTest::class,
    ],
)
class MetricsTest {
    @Autowired
    private lateinit var zeebeClient: ZeebeClient

    @MockkBean(relaxed = true)
    private lateinit var metricsRecorder: MetricsRecorder

    @Coworker(type = PASS_NAME)
    suspend fun testPass(
        jobClient: JobClient,
        job: ActivatedJob,
    ) {
        jobClient.newCompleteCommand(job).send().await()
    }

    @Coworker(type = "throw")
    suspend fun testThrow(
        jobClient: JobClient,
        job: ActivatedJob,
    ) {
        error("Something goes wrooong!")
    }

    @Test
    fun `should increase metrics on invocation only`() {
        // given
        clearMocks(metricsRecorder, answers = false)
        val modelInstance =
            Bpmn
                .createExecutableProcess()
                .startEvent()
                .serviceTask().zeebeJobType(PASS_NAME)
                .endEvent()
                .done()
        val deploymentEvent =
            zeebeClient.newDeployResourceCommand().addProcessModel(modelInstance, "pass.bpmn").send().join()

        // when
        zeebeClient.newCreateInstanceCommand()
            .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey).withResult().send().join()

        // then
        verify(exactly = 1) {
            metricsRecorder.increase(
                MetricsRecorder.METRIC_NAME_JOB,
                MetricsRecorder.ACTION_ACTIVATED,
                PASS_NAME,
            )
        }
    }

    @Test
    fun `should increase metrics on error`() {
        // given
        clearMocks(metricsRecorder, answers = false)
        val modelInstance =
            Bpmn.createExecutableProcess().startEvent().serviceTask().zeebeJobType(THROW_NAME).zeebeJobRetries("0")
                .endEvent().done()
        val deploymentEvent =
            zeebeClient.newDeployResourceCommand().addProcessModel(modelInstance, "throw.bpmn").send().join()

        // when
        assertThatThrownBy {
            zeebeClient.newCreateInstanceCommand()
                .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey).withResult().send().join()
        }

        // then
        verify(exactly = 1) {
            metricsRecorder.increase(
                MetricsRecorder.METRIC_NAME_JOB,
                MetricsRecorder.ACTION_ACTIVATED,
                THROW_NAME,
            )
        }
        verify(exactly = 1) {
            metricsRecorder.increase(
                MetricsRecorder.METRIC_NAME_JOB,
                MetricsRecorder.ACTION_FAILED,
                THROW_NAME,
            )
        }
    }
}
