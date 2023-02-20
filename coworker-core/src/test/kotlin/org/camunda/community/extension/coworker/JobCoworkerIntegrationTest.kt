package org.camunda.community.extension.coworker

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.process.test.api.ZeebeTestEngine
import io.camunda.zeebe.process.test.assertions.BpmnAssert
import io.camunda.zeebe.process.test.extension.testcontainer.ZeebeProcessTest
import io.camunda.zeebe.process.test.filters.RecordStream
import kotlinx.coroutines.future.await
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.camunda.community.extension.coworker.zeebe.worker.handler.JobHandler
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.LinkedList

@ZeebeProcessTest
class JobCoworkerIntegrationTest {
    private lateinit var engine: ZeebeTestEngine
    private lateinit var client: ZeebeClient
    private lateinit var recordStream: RecordStream

    @Test
    internal fun `should JobCoworker successfully created`() {
        client.toCozeebe().newCoWorker("myType", object : JobHandler {
            override suspend fun handle(client: JobClient, job: ActivatedJob) {
                client.newCompleteCommand(job).send().await()
            }
        }).open().use {
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

        val deploymentEvent =
            client.newDeployResourceCommand().addProcessModel(simpleProcess, "process.bpmn").send().join()

        client.toCozeebe().newCoWorker(jobType, object : JobHandler {
            override suspend fun handle(client: JobClient, job: ActivatedJob) {
                val variables = job.variablesAsMap
                val aVar = variables["a"] as Int
                val bVar = variables["b"] as Int
                variables["c"] = aVar + bVar

                client.newCompleteCommand(job).variables(variables).send().await()
            }
        }
        ).open().use {

            val instanceResult = client.newCreateInstanceCommand()
                .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
                .variables(mapOf("a" to 1, "b" to 3)).withResult().requestTimeout(Duration.ofMinutes(1)).send().join()
            BpmnAssert.assertThat(instanceResult).isCompleted.hasNoIncidents().hasVariableWithValue("c", 4)
        }
    }

    @Test
    fun `should retry and throw an error by default if exception occurred while handling the job`() {
        // given
        val jobType = "defaultErrorHandler"
        val serviceTaskName = "default-error-handler"
        val simpleProcess = Bpmn
            .createExecutableProcess()
            .startEvent()
            .serviceTask(serviceTaskName).zeebeJobRetries("3")
            .zeebeJobType(jobType)
            .endEvent()
            .done()

        val deploymentEvent =
            client.newDeployResourceCommand().addProcessModel(simpleProcess, "process.bpmn").send().join()

        val expectedRetriesQueue = LinkedList(arrayListOf(3, 2, 1))
        val exceptionMessage = "Oops, something bad happened"
        val processInstanceEvent = client.toCozeebe().newCoWorker(jobType, object : JobHandler {
            override suspend fun handle(client: JobClient, job: ActivatedJob) {
                // we are checking that retries are decreasing
                assertThat(job.retries).isEqualTo(expectedRetriesQueue.poll())
                throw Exception(exceptionMessage)
            }

        })
            .open().use {

                // when
                val instanceResult = client.newCreateInstanceCommand()
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

}
