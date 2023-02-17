package org.camunda.community.extension.coworker

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.process.test.api.ZeebeTestEngine
import io.camunda.zeebe.process.test.assertions.BpmnAssert
import io.camunda.zeebe.process.test.extension.testcontainer.ZeebeProcessTest
import io.camunda.zeebe.process.test.filters.RecordStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.await
import mu.KLogging
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.extension.coworker.zeebe.worker.JobCoroutineContextProvider
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@ZeebeProcessTest
class JobCoworkerIntegrationTest {
    private lateinit var engine: ZeebeTestEngine
    private lateinit var client: ZeebeClient
    private lateinit var recordStream: RecordStream

    @Test
    internal fun `should JobCoworker successfully created`() {
        client.toCozeebe().newCoWorker("myType") { client, job ->
            client.newCompleteCommand(job).send().await()
        }.open().use {
            assertThat(it.isOpen).isTrue
            assertThat(it.isClosed).isFalse
        }
    }

    @Test
    internal fun `should JobCoworker successfully execute task`() {
        val jobType = "myServiceTask"
        val simpleProcess = Bpmn
            .createExecutableProcess()
            .startEvent()
            .serviceTask("my-service-task")
            .zeebeJobType(jobType)
            .endEvent()
            .done()

        val deploymentEvent = client.newDeployResourceCommand().addProcessModel(simpleProcess, "process.bpmn").send().join()

        client.toCozeebe().newCoWorker(jobType) { client, job ->
            logger.info { "Got it!" }
            val variables = job.variablesAsMap
            val aVar = variables["a"] as Int
            val bVar = variables["b"] as Int
            variables["c"] = aVar + bVar

            client.newCompleteCommand(job).variables(variables).send().await()
        }.open().use {

            val instanceResult = client.newCreateInstanceCommand()
                .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
                .variables(mapOf("a" to 1, "b" to 3)).withResult().requestTimeout(Duration.ofMinutes(1)).send().join()
            BpmnAssert.assertThat(instanceResult).isCompleted.hasNoIncidents().hasVariableWithValue("c", 4)
        }
    }

    @Test
    fun `should update coroutine context`() {
        val jobType = "myServiceTask"
        val simpleProcess = Bpmn
            .createExecutableProcess()
            .startEvent()
            .serviceTask("my-service-task")
            .zeebeJobType(jobType)
            .endEvent()
            .done()

        val deploymentEvent = client.newDeployResourceCommand().addProcessModel(simpleProcess, "process.bpmn").send().join()

        val testCoroutineContext = TestCoroutineContext()
        client.toCozeebe().newCoWorker(jobType) { client, job ->
            assertThat(currentCoroutineContext()[TestCoroutineContext.Key]).isNotNull.isEqualTo(testCoroutineContext)
            client.newCompleteCommand(job).send().await()
        }
            .also { it.additionalCoroutineContextProvider = JobCoroutineContextProvider { testCoroutineContext } }
            .open().use {
            val instanceResult = client.newCreateInstanceCommand()
                .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
                .withResult().requestTimeout(Duration.ofMinutes(1)).send().join()
            BpmnAssert.assertThat(instanceResult).isCompleted.hasNoIncidents()
        }
    }

    class TestCoroutineContext: AbstractCoroutineContextElement(Key) {
        companion object Key: CoroutineContext.Key<TestCoroutineContext>
    }

    companion object: KLogging()
}
