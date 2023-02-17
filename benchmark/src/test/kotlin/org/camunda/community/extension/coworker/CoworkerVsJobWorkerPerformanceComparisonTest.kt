package org.camunda.community.extension.coworker

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.zeebe.containers.ZeebeContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import mu.KLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.toJavaDuration

@Testcontainers
class CoworkerVsJobWorkerPerformanceComparisonTest {

    @Container
    private val zeebeContainer = ZeebeContainer()
        // Three partitions
        .withEnv("ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT", "3")
        .withCreateContainerCmdModifier {
            val oneGigabyte = 1024L * 1024L * 1024L * 1024L
            requireNotNull(it.hostConfig)
                // 1G
                .withMemory(oneGigabyte)
                // No swap
                .withMemorySwap(oneGigabyte)
                // One core
                .withCpuCount(1)
        }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `should coworkers works faster then zeebe-client-java`() {
        val jobType = "myServiceTask"
        val simpleProcess = Bpmn
            .createExecutableProcess()
            .startEvent()
            .serviceTask("my-service-task")
            .zeebeJobType(jobType)
            .endEvent()
            .done()
        // Create process definition
        val instancesCount = System.getenv("INSTANCES_COUNT")?.toInt() ?: 40
        val deploymentEvent =
            ZeebeClient
                .newClientBuilder()
                .gatewayAddress(zeebeContainer.externalGatewayAddress)
                .usePlaintext()
                .defaultRequestTimeout(Duration.ofMinutes(1))
                .build()
                .use {
                    it
                        .newDeployResourceCommand()
                        .addProcessModel(simpleProcess, "simple-process.bpmn")
                        .send()
                        .join()
                }
        val zeebeClientDuration = ZeebeClient
            .newClientBuilder()
            .gatewayAddress(zeebeContainer.externalGatewayAddress)
            .usePlaintext()
            .defaultRequestTimeout(Duration.ofMinutes(1))
            .build()
            .use { zeebeClient ->
                // Create worker with 1 second await and complete
                val jobWorkerBuilder = zeebeClient
                    .newWorker()
                    .jobType(jobType)
                    .handler { client, job ->
                        TimeUnit.SECONDS.sleep(1)
                        client.newCompleteCommand(job).send().join()
                    }
                // measure how much it takes to finish instancesCount instances
                val zeebeClientDuration = jobWorkerBuilder.open().use {
                    measureTime {
                        val processInstanceResults = (1..instancesCount)
                            .map {
                                zeebeClient
                                    .newCreateInstanceCommand()
                                    .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
                                    .withResult()
                                    .send()
                            }
                            .map { it as CompletableFuture<*> }
                            .toTypedArray()
                        // Wait for all results
                        CompletableFuture.allOf(*processInstanceResults).join()
                    }
                }
                println("For Zeebe Client it took $zeebeClientDuration to process $instancesCount")
                zeebeClientDuration
            }

        val coworkerDuration = ZeebeClient
            .newClientBuilder()
            .gatewayAddress(zeebeContainer.externalGatewayAddress)
            .usePlaintext()
            .build()
            .use { zeebeClient ->
                val cozeebe = zeebeClient.toCozeebe()
                val coworkerBuilder = cozeebe.newCoWorker(
                    jobType
                ) { client, job ->
                    logger.info { "Got it!" }
                    delay(1.seconds)
                    client.newCompleteCommand(job).send().await()
                }
                val coworkerDuration = coworkerBuilder.open().use {
                    measureTime {
                        val processInstanceResults = (1..instancesCount)
                            .map {
                                zeebeClient
                                    .newCreateInstanceCommand()
                                    .processDefinitionKey(deploymentEvent.processes.first().processDefinitionKey)
                                    .withResult()
                                    .send()
                            }
                            .map { it as CompletableFuture<*> }
                            .toTypedArray()
                        // Wait for all results
                        CompletableFuture.allOf(*processInstanceResults).join()
                    }
                }
                println("For Coworker it took $coworkerDuration to process $instancesCount")
                coworkerDuration
            }
        println("So, Zeebe Client Java duration / Coworker duration = ${zeebeClientDuration/coworkerDuration}")
        // is more than 2 times faster
        assertThat(coworkerDuration.toJavaDuration()).isLessThan((zeebeClientDuration / 2).toJavaDuration())
    }

    companion object: KLogging()
}
