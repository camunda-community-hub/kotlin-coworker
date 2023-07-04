package org.camunda.community.extension.coworker.zeebe.worker.builder

import io.camunda.zeebe.client.ZeebeClientConfiguration
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.BackoffSupplier
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.client.impl.command.ArgumentUtil.ensureGreaterThan
import io.camunda.zeebe.client.impl.command.ArgumentUtil.ensureNotNullNorEmpty
import io.camunda.zeebe.client.impl.worker.JobWorkerBuilderImpl
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.slf4j.MDCContext
import org.camunda.community.extension.coworker.zeebe.worker.JobCoroutineContextProvider
import org.camunda.community.extension.coworker.zeebe.worker.JobCoworker
import org.camunda.community.extension.coworker.zeebe.worker.JobPoller
import org.camunda.community.extension.coworker.zeebe.worker.handler.JobExecutableFactory
import org.camunda.community.extension.coworker.zeebe.worker.handler.JobHandler
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.impl.DefaultJobErrorHandler
import java.io.Closeable
import java.util.concurrent.ScheduledExecutorService
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

val DEFAULT_BACKOFF_SUPPLIER = JobWorkerBuilderImpl.DEFAULT_BACKOFF_SUPPLIER

class JobCoworkerBuilder(
    configuration: ZeebeClientConfiguration,
    var jobClient: JobClient,
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
    private var backoffSupplier: BackoffSupplier = DEFAULT_BACKOFF_SUPPLIER,
    var additionalCoroutineContextProvider: JobCoroutineContextProvider = JobCoroutineContextProvider { _: ActivatedJob -> MDCContext() },
    var jobErrorHandler: JobErrorHandler = DefaultJobErrorHandler()
) {

    private fun checkPreconditions() {
        ensureNotNullNorEmpty("jobType", jobType)
        ensureGreaterThan("timeout", timeout.inWholeMilliseconds, 0L)
        ensureNotNullNorEmpty("workerName", workerName)
        ensureGreaterThan("maxJobsActive", maxJobsActive.toLong(), 0)
    }

    fun open(): JobCoworker {
        checkPreconditions()
        val jobExecutableFactory = JobExecutableFactory(jobClient, jobHandler, jobErrorHandler)
        val jobPoller = JobPoller(
            jobClient = jobClient,
            type = jobType,
            timeout = timeout,
            workerName = workerName,
            fetchVariables = fetchVariables,
            maxJobsToActivate = maxJobsActive,
            retryThrowable = retryPredicate
        )
        val jobCoWorker = JobCoworker(
            maxJobsActive = maxJobsActive,
            scheduledCoroutineContext = executorService.asCoroutineDispatcher(),
            jobExecutableFactory = jobExecutableFactory,
            initialPollInterval = pollInterval,
            backoffSupplier = backoffSupplier,
            jobPoller = jobPoller,
            additionalCoroutineContextProvider = additionalCoroutineContextProvider
        )
        closeables.add(jobCoWorker)
        return jobCoWorker
    }
}
