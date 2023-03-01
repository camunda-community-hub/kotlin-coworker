package org.camunda.community.extension.coworker.credentials

import io.camunda.zeebe.client.CredentialsProvider
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.gateway.protocol.GatewayGrpcKt
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass
import io.camunda.zeebe.gateway.protocol.activateJobsResponse
import io.camunda.zeebe.gateway.protocol.activatedJob
import io.grpc.Metadata
import io.grpc.ServerBuilder
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.mockk.spyk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.camunda.community.extension.coworker.toCozeebe
import org.camunda.community.extension.coworker.zeebe.worker.handler.JobHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.fail
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class ZeebeCoworkerClientCredentialsIntegrationTest {

    @Test
    @Timeout(60)
    fun `should apply credentials provider in the headers`() {
        val testAuthHeaderValue = "TEST_AUTH_HEADER_VALUE"
        val testJobType = "test-credentials-provider"
        val jobKey = Random(System.currentTimeMillis()).nextLong()
        val service = spyk(
            object : GatewayGrpcKt.GatewayCoroutineImplBase() {
                override fun activateJobs(request: GatewayOuterClass.ActivateJobsRequest): Flow<GatewayOuterClass.ActivateJobsResponse> {
                    val value = try {
                        activateJobsResponse {
                            jobs.add(
                                activatedJob {
                                    this.type = testJobType
                                    this.key = jobKey
                                    this.customHeaders = "{}"
                                }
                            )
                        }
                    } catch (t: Throwable) {
                        throw t
                    }
                    return flowOf(value)
                }
            }
        )
        val authHeaderKey = Metadata.Key.of(
            "Authorization",
            Metadata.ASCII_STRING_MARSHALLER
        )
        val server = ServerBuilder
            .forPort(0)
            .addService(service)
            .intercept(object : ServerInterceptor {
                override fun <ReqT : Any?, RespT : Any?> interceptCall(
                    call: ServerCall<ReqT, RespT>,
                    headers: Metadata,
                    next: ServerCallHandler<ReqT, RespT>
                ): ServerCall.Listener<ReqT> {
                    val authHeaderValue = headers.get(authHeaderKey)
                    if (authHeaderValue != testAuthHeaderValue) {
                        fail { "header value should be equal" }
                    }
                    return next.startCall(call, headers)
                }
            })
            .build()
        try {
            server.start()
            val mockZeebeGatewayPort = server.port
            val zeebeClient = ZeebeClient
                .newClientBuilder()
                .credentialsProvider(object : CredentialsProvider {
                    override fun applyCredentials(headers: Metadata) {
                        headers.put(
                            authHeaderKey,
                            testAuthHeaderValue
                        )
                    }

                    override fun shouldRetryRequest(throwable: Throwable?): Boolean = false
                })
                .usePlaintext()
                .gatewayAddress("localhost:$mockZeebeGatewayPort")
                .build()

            val latch = CountDownLatch(1)

            zeebeClient
                .toCozeebe()
                .newCoWorker(testJobType, object : JobHandler {
                    override suspend fun handle(client: JobClient, job: ActivatedJob) {
                        if (job.key != jobKey) {
                            fail { "Job keys should be equal" }
                        }
                        latch.countDown()
                    }

                })
                .also {
                    it.maxJobsActive = 1
                    it.retryPredicate = {
                        fail { "We should never retry" }
                    }
                    it.pollInterval = 1.seconds
                }
                .open()

            latch.await()
        } finally {
            server.shutdownNow()
            server.awaitTermination(10, TimeUnit.SECONDS)
        }
    }
}
