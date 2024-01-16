package org.camunda.community.extension.coworker.impl

import io.camunda.zeebe.client.CredentialsProvider
import io.camunda.zeebe.client.ZeebeClientConfiguration
import io.camunda.zeebe.client.api.JsonMapper
import io.camunda.zeebe.client.api.command.ClientException
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.client.impl.NoopCredentialsProvider
import io.camunda.zeebe.client.impl.ZeebeClientImpl.buildChannel
import io.camunda.zeebe.client.impl.ZeebeClientImpl.buildGatewayStub
import io.camunda.zeebe.client.impl.worker.JobClientImpl
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub
import io.camunda.zeebe.gateway.protocol.GatewayGrpcKt
import io.grpc.CallCredentials
import io.grpc.CallOptions
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import org.camunda.community.extension.coworker.Cozeebe
import org.camunda.community.extension.coworker.credentials.ZeebeCoworkerClientCredentials
import org.camunda.community.extension.coworker.zeebe.worker.builder.JobCoworkerBuilder
import org.camunda.community.extension.coworker.zeebe.worker.handler.JobHandler
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

fun ZeebeClientConfiguration.buildCallCredentials(): CallCredentials? {
    val customCredentialsProvider = this.credentialsProvider ?: return null
    return ZeebeCoworkerClientCredentials(customCredentialsProvider)
}

fun ZeebeClientConfiguration.buildGatewayCoroutineStub(channel: ManagedChannel): GatewayGrpcKt.GatewayCoroutineStub {
    val callOptions =
        this.buildCallCredentials()?.let {
            CallOptions.DEFAULT.withCallCredentials(it)
        } ?: CallOptions.DEFAULT
    return GatewayGrpcKt
        .GatewayCoroutineStub(
            ClientInterceptors.intercept(channel, interceptors),
            callOptions,
        )
}

fun ZeebeClientConfiguration.buildExecutorService(): ScheduledExecutorService =
    Executors.newScheduledThreadPool(this.numJobWorkerExecutionThreads)

class CozeebeImpl(
    private val zeebeClientConfiguration: ZeebeClientConfiguration,
    private val managedChannel: ManagedChannel = buildChannel(zeebeClientConfiguration),
    private val gatewayStub: GatewayStub = buildGatewayStub(managedChannel, zeebeClientConfiguration),
    private val gatewayCoroutineStub: GatewayGrpcKt.GatewayCoroutineStub =
        zeebeClientConfiguration
            .buildGatewayCoroutineStub(managedChannel),
    private val jsonMapper: JsonMapper = zeebeClientConfiguration.jsonMapper,
    private val scheduledExecutorService: ScheduledExecutorService = zeebeClientConfiguration.buildExecutorService(),
    private val credentialsProvider: CredentialsProvider =
        zeebeClientConfiguration.credentialsProvider
            ?: NoopCredentialsProvider(),
    private val retryPredicate: (Throwable) -> Boolean = credentialsProvider::shouldRetryRequest,
    private val jobClient: JobClient =
        JobClientImpl(gatewayStub, zeebeClientConfiguration, jsonMapper, retryPredicate),
) : Cozeebe {
    private val closeables: MutableList<Closeable> = mutableListOf()

    override fun newCoWorker(
        jobType: String,
        jobHandler: JobHandler,
    ): JobCoworkerBuilder =
        JobCoworkerBuilder(
            configuration = zeebeClientConfiguration,
            jobClient = jobClient,
            gatewayStub = gatewayCoroutineStub,
            jsonMapper = jsonMapper,
            jobType = jobType,
            jobHandler = jobHandler,
            retryPredicate = retryPredicate,
            executorService = scheduledExecutorService,
            closeables = closeables,
        )

    override fun configuration() = zeebeClientConfiguration

    override fun close() {
        closeables
            .forEach {
                runCatching { it.close() }.onFailure {
                    if (it !is IOException) {
                        throw it
                    }
                }
            }

        scheduledExecutorService.shutdownNow()

        try {
            if (!scheduledExecutorService.awaitTermination(TIMEOUT, TimeUnit.SECONDS)) {
                throw ClientException(
                    "Timed out awaiting termination of job worker executor after 15 seconds",
                )
            }
        } catch (e: InterruptedException) {
            throw ClientException(
                "Unexpected interrupted awaiting termination of job worker executor",
                e,
            )
        }

        managedChannel.shutdownNow()

        try {
            if (!managedChannel.awaitTermination(TIMEOUT, TimeUnit.SECONDS)) {
                throw ClientException(
                    "Timed out awaiting termination of in-flight request channel after 15 seconds",
                )
            }
        } catch (e: InterruptedException) {
            throw ClientException(
                "Unexpectedly interrupted awaiting termination of in-flight request channel",
                e,
            )
        }
    }

    companion object {
        private const val TIMEOUT = 15L
    }
}
