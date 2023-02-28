package org.camunda.community.extension.coworker.zeebe.worker.builder

import io.camunda.zeebe.client.ZeebeClientConfiguration
import io.camunda.zeebe.client.api.JsonMapper
import io.camunda.zeebe.client.api.worker.BackoffSupplier
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.client.impl.command.ArgumentUtil.ensureGreaterThan
import io.camunda.zeebe.client.impl.command.ArgumentUtil.ensureNotNullNorEmpty
import io.camunda.zeebe.client.impl.worker.JobWorkerBuilderImpl
import io.camunda.zeebe.gateway.protocol.GatewayGrpcKt
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.slf4j.MDCContext
import org.camunda.community.extension.coworker.zeebe.worker.JobCoworker
import org.camunda.community.extension.coworker.zeebe.worker.JobPoller
import org.camunda.community.extension.coworker.zeebe.worker.handler.JobExecutableFactory
import org.camunda.community.extension.coworker.zeebe.worker.handler.JobHandler
import java.io.Closeable
import java.util.concurrent.ScheduledExecutorService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

val DEFAULT_BACKOFF_SUPPLIER = JobWorkerBuilderImpl.DEFAULT_BACKOFF_SUPPLIER
private val DEADLINE_OFFSET = 10.seconds

class JobCoworkerBuilder(
    configuration: ZeebeClientConfiguration,
    var jobClient: JobClient,
    var gatewayStub: GatewayGrpcKt.GatewayCoroutineStub,
    var jsonMapper: JsonMapper,
    var jobType: String,
    var jobHandler: JobHandler,
    var retryPredicate: suspend (Throwable) -> Boolean,
    var executorService: ScheduledExecutorService,
    var closeables: MutableList<Closeable>,
    var timeout: Duration = configuration.defaultJobTimeout.toKotlinDuration(),
    var workerName: String = configuration.defaultJobWorkerName,
    var maxJobsActive: Int = configuration.defaultJobWorkerMaxJobsActive,
    var pollInterval: Duration = configuration.defaultJobPollInterval.toKotlinDuration(),
    var requestTimeout: Duration = configuration.defaultRequestTimeout.toKotlinDuration(),
    var fetchVariables: List<String>? = null,
    var backoffSupplier: BackoffSupplier = DEFAULT_BACKOFF_SUPPLIER
) {

    private fun checkPreconditions() {
        ensureNotNullNorEmpty("jobType", jobType)
        ensureGreaterThan("timeout", timeout.inWholeMilliseconds, 0L)
        ensureNotNullNorEmpty("workerName", workerName)
        ensureGreaterThan("maxJobsActive", maxJobsActive.toLong(), 0)
    }

    fun open(): JobCoworker {
        checkPreconditions()
        val requestBuilder = GatewayOuterClass.ActivateJobsRequest.newBuilder()
            .setType(jobType)
            .setTimeout(timeout.inWholeMilliseconds)
            .setWorker(workerName)
            .setMaxJobsToActivate(maxJobsActive)
            .setRequestTimeout(requestTimeout.inWholeMilliseconds)
        fetchVariables?.let {
            requestBuilder.addAllFetchVariable(it)
        }
        val deadline = requestTimeout.plus(DEADLINE_OFFSET)
        val jobExecutableFactory = JobExecutableFactory(jobClient, jobHandler)
        val jobPoller = JobPoller(
            gatewayStubKt = gatewayStub,
            requestBuilder = requestBuilder,
            jsonMapper = jsonMapper,
            requestTimeout = deadline,
            retryThrowable = retryPredicate
        )
        val jobCoWorker = JobCoworker(
            maxJobsActive = maxJobsActive,
            scheduledCoroutineContext = executorService.asCoroutineDispatcher() + MDCContext(),
            jobExecutableFactory = jobExecutableFactory,
            initialPollInterval = pollInterval,
            backoffSupplier = backoffSupplier,
            jobPoller = jobPoller
        )
        closeables.add(jobCoWorker)
        return jobCoWorker
    }
}
