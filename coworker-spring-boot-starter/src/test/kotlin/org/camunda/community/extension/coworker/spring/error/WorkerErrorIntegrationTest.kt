package org.camunda.community.extension.coworker.spring.error

import com.ninjasquad.springmockk.SpykBean
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.process.test.assertions.BpmnAssert
import io.camunda.zeebe.spring.client.config.ZeebeClientStarterAutoConfiguration
import io.camunda.zeebe.spring.test.ZeebeSpringTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.future.await
import org.camunda.community.extension.coworker.spring.CoworkerAutoConfiguration
import org.camunda.community.extension.coworker.spring.annotation.Coworker
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest

@ZeebeSpringTest
@SpringBootTest(classes = [
    JacksonAutoConfiguration::class,
    ZeebeClientStarterAutoConfiguration::class,
    CoworkerAutoConfiguration::class,
    CoworkerAutoConfiguration::class,
    WorkerErrorIntegrationTest::class
])
class WorkerErrorIntegrationTest {

    @SpykBean
    private lateinit var jobErrorHandler: JobErrorHandler

    @Autowired
    private lateinit var zeebeClient: ZeebeClient

    private lateinit var exceptionToThrow: Exception

    @Coworker("coworker-with-exception")
    suspend fun coworkerWithException(activatedJob: ActivatedJob, jobClient: JobClient) {
        throw exceptionToThrow
    }

    @Test
    fun `should inner job error handler process exception from coworker`() {
        // given
        val message = "bad-happens"
        val model = Bpmn
            .createExecutableProcess()
            .startEvent()
            .serviceTask("error-service-task") {
                it
                    .zeebeJobType("coworker-with-exception")
                    .zeebeJobRetries("0")
                    .boundaryEvent()
                    .error(message)
                    .endEvent()
                    .done()
            }
            .endEvent().error("missed")
            .done()

        val deploymentEvent = zeebeClient
            .newDeployResourceCommand()
            .addProcessModel(model, "error.bpmn")
            .send()
            .join()

        exceptionToThrow = Exception(message)
        val mockJobHandler = mockk<JobErrorHandler> {
            coEvery {
                handleError(exceptionToThrow, any(), any())
            } coAnswers {
                val activatedJob = it.invocation.args[1] as ActivatedJob
                val caughtException = it.invocation.args[0] as Exception
                zeebeClient
                    .newThrowErrorCommand(activatedJob)
                    .errorCode(caughtException.message)
                    .send()
                    .await()
            }
        }
        coEvery { jobErrorHandler.handleError(any(), any(), any()) } coAnswers {
            DefaultSpringZeebeErrorHandler(mockJobHandler)
                .handleError(
                    it.invocation.args[0] as Exception,
                    it.invocation.args[1] as ActivatedJob,
                    it.invocation.args[2] as JobClient
                )
        }

        // when
        val result = zeebeClient
            .newCreateInstanceCommand()
            .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
            .withResult()
            .send()
            .join()

        // then
        BpmnAssert.assertThat(result).isCompleted.hasNoIncidents()
        coVerify(exactly = 1) { mockJobHandler.handleError(exceptionToThrow, any(), any()) }
    }
}
