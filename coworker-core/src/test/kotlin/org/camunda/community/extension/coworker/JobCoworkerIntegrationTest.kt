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
import org.awaitility.Awaitility
import org.camunda.community.extension.coworker.zeebe.worker.JobCoroutineContextProvider
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.LinkedList
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
        val simpleProcess =
            Bpmn.createExecutableProcess()
                .startEvent()
                .serviceTask("my-service-task")
                .zeebeJobType(jobType)
                .endEvent()
                .done()

        val deploymentEvent =
            client.newDeployResourceCommand().addProcessModel(simpleProcess, "process.bpmn").send().join()

        client.toCozeebe().newCoWorker(jobType) { client, job ->
            logger.info { "Got it!" }
            val variables = job.variablesAsMap
            val aVar = variables["a"] as Int
            val bVar = variables["b"] as Int
            variables["c"] = aVar + bVar

            client.newCompleteCommand(job).variables(variables).send().await()
        }.open().use {
            val instanceResult =
                client.newCreateInstanceCommand()
                    .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
                    .variables(mapOf("a" to 1, "b" to 3)).withResult().requestTimeout(Duration.ofMinutes(1)).send().join()
            BpmnAssert.assertThat(instanceResult).isCompleted.hasNoIncidents().hasVariableWithValue("c", 4)
        }
    }

    @Test
    fun `should update coroutine context`() {
        val jobType = "myServiceTask"
        val simpleProcess =
            Bpmn
                .createExecutableProcess()
                .startEvent()
                .serviceTask("my-service-task")
                .zeebeJobType(jobType)
                .endEvent()
                .done()

        val deploymentEvent =
            client.newDeployResourceCommand().addProcessModel(simpleProcess, "process.bpmn").send().join()

        val testCoroutineContext = TestCoroutineContext()
        client.toCozeebe().newCoWorker(jobType) { client, job ->
            assertThat(currentCoroutineContext()[TestCoroutineContext.Key]).isNotNull.isEqualTo(testCoroutineContext)
            client.newCompleteCommand(job).send().await()
        }
            .also { it.additionalCoroutineContextProvider = JobCoroutineContextProvider { testCoroutineContext } }
            .open().use {
                val instanceResult =
                    client.newCreateInstanceCommand()
                        .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
                        .withResult().requestTimeout(Duration.ofMinutes(1)).send().join()
                BpmnAssert.assertThat(instanceResult).isCompleted.hasNoIncidents()
            }
    }

    @Test
    fun `should work with custom error handler`() {
        // given
        val jobType = "customErrorHandler"
        val simpleProcess =
            Bpmn.createExecutableProcess()
                .startEvent()
                .serviceTask("custom-error-handler")
                .zeebeJobType(jobType)
                .endEvent()
                .done()

        val deploymentEvent =
            client.newDeployResourceCommand().addProcessModel(simpleProcess, "process.bpmn").send().join()

        client.toCozeebe().newCoWorker(jobType) { _, _ ->
            throw IgnorableException()
        }
            .also {
                it.jobErrorHandler =
                    JobErrorHandler { e, activatedJob, jobClient ->
                        if (e is IgnorableException) {
                            jobClient.newCompleteCommand(activatedJob).variables(mapOf("ignored" to true)).send().await()
                        } else {
                            jobClient.newFailCommand(activatedJob).retries(activatedJob.retries - 1).send().await()
                        }
                    }
            }
            .open().use {
                val instanceResult =
                    client.newCreateInstanceCommand()
                        .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
                        .withResult().requestTimeout(Duration.ofMinutes(1)).send().join()
                BpmnAssert.assertThat(instanceResult).isCompleted
            }
    }

    @Test
    fun `should retry and throw an error by default if exception occurred while handling the job`() {
        // given
        val jobType = "defaultErrorHandler"
        val serviceTaskName = "default-error-handler"
        val simpleProcess =
            Bpmn.createExecutableProcess()
                .startEvent()
                .serviceTask(serviceTaskName).zeebeJobRetries("3")
                .zeebeJobType(jobType)
                .endEvent()
                .done()

        val deploymentEvent =
            client.newDeployResourceCommand().addProcessModel(simpleProcess, "process.bpmn").send().join()

        val expectedRetriesQueue = LinkedList(arrayListOf(3, 2, 1))
        val exceptionMessage = "Oops, something bad happened"
        val processInstanceEvent =
            client.toCozeebe().newCoWorker(jobType) { _, activatedJob ->
                // we are checking that retries are decreasing
                assertThat(activatedJob.retries).isEqualTo(expectedRetriesQueue.poll())
                throw IllegalStateException(exceptionMessage)
            }
                .open().use {
                    // when
                    val instanceResult =
                        client.newCreateInstanceCommand()
                            .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
                            .requestTimeout(Duration.ofMinutes(1))
                            .send()
                            .join()
                    Awaitility
                        .await()
                        .atMost(Duration.ofSeconds(5))
                        .pollDelay(Duration.ofMillis(500))
                        .until {
                            recordStream
                                .incidentRecords()
                                .any { it.value.processInstanceKey == instanceResult.processInstanceKey }
                        }
                    instanceResult
                }

        // then
        BpmnAssert
            .assertThat(processInstanceEvent)
            .isNotCompleted
            .hasAnyIncidents()
            .extractingLatestIncident()
            .isUnresolved
            .extractingErrorMessage()
            .contains(exceptionMessage)
        // check that we are walk through all retries
        assertThat(expectedRetriesQueue).isEmpty()
    }

    class TestCoroutineContext : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<TestCoroutineContext>
    }

    class IgnorableException : Exception()

    companion object : KLogging()
}
