package org.camunda.community.extension.coworker.zeebe.worker

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.future.await
import mu.KLogging
import kotlin.properties.Delegates
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Port of [io.camunda.zeebe.client.impl.worker.JobPoller] to Kotlin coroutines
 */
class JobPoller(
    private val jobClient: JobClient,
    private val type: String,
    private val timeout: Duration,
    private val workerName: String,
    private val fetchVariables: List<String>?,
    private var maxJobsToActivate: Int,
    private val retryThrowable: suspend (Throwable) -> Boolean,
) {

    private lateinit var jobConsumer: suspend (ActivatedJob) -> Unit
    private lateinit var doneCallback: suspend (Int) -> Unit
    private lateinit var errorCallback: suspend (Throwable) -> Unit
    private var activatedJobs by Delegates.notNull<Int>()
    private lateinit var openSupplier: suspend () -> Boolean

    private fun reset() {
        activatedJobs = 0
    }

    suspend fun poll(
        maxJobsToActivate: Int,
        jobConsumer: suspend (ActivatedJob) -> Unit,
        doneCallback: suspend (Int) -> Unit,
        errorCallback: suspend (Throwable) -> Unit,
        openSupplier: suspend () -> Boolean
    ) {
        reset()

        this.maxJobsToActivate = maxJobsToActivate
        this.jobConsumer = jobConsumer
        this.doneCallback = doneCallback
        this.errorCallback = errorCallback
        this.openSupplier = openSupplier

        poll()
    }

    @OptIn(FlowPreview::class)
    private suspend fun poll() {
        logger.trace {
            "Polling at max $maxJobsToActivate jobs for worker $workerName and job type $type"
        }
        val activateJobsCommand = jobClient.newActivateJobsCommand()
            .jobType(type)
            .maxJobsToActivate(maxJobsToActivate)
            .timeout(timeout.toJavaDuration())
        fetchVariables?.let {
            activateJobsCommand.fetchVariables(it)
        }
        runCatching {
            activateJobsCommand
                .send()
                .await()
        }.onFailure {
            if(retryThrowable(it)) {
                poll()
            } else if (openSupplier()) {
                try {
                    logFailure(it)
                } finally {
                    errorCallback(it)
                }
            }
        }.onSuccess {
            val jobs = it.jobs
            activatedJobs+= jobs.size
            jobs.forEach { activatedJob ->
                jobConsumer(activatedJob)
            }
            pollingDone()
        }
    }

    private fun logFailure(throwable: Throwable) {
        if (throwable is StatusRuntimeException) {
            if (throwable.status.code == Status.RESOURCE_EXHAUSTED.code) {
                // Log RESOURCE_EXHAUSTED status exceptions only as trace, otherwise it is just too
                // noisy. Furthermore it is not worth to be a warning since it is expected on a fully
                // loaded cluster. It should be handled by our backoff mechanism, but if there is an
                // issue or an configuration mistake the user can turn on trace logging to see this.
                logger.trace(throwable) { buildErrorMessage(workerName, type) }
                return
            }
        }

        logger.warn(throwable) { buildErrorMessage(workerName, type) }
    }

    private fun buildErrorMessage(
        worker: String,
        type: String
    ) = "Failed to activate jobs for worker $worker and job type $type"

    private suspend fun pollingDone() {
        if (activatedJobs > 0) {
            logger.debug {
                "Activated $activatedJobs jobs for worker $workerName and job type $type"
            }
        } else {
            logger.trace {
                "No jobs activated for worker $workerName and job type $type"
            }
        }
        doneCallback(activatedJobs)
    }

    companion object : KLogging()
}
