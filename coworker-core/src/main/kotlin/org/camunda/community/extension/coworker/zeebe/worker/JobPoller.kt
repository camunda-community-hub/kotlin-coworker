package org.camunda.community.extension.coworker.zeebe.worker

import io.camunda.zeebe.client.api.JsonMapper
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.impl.response.ActivatedJobImpl
import io.camunda.zeebe.gateway.protocol.GatewayGrpcKt
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest.Builder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import mu.KLogging
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates
import kotlin.time.Duration

/**
 * Port of [io.camunda.zeebe.client.impl.worker.JobPoller] to Kotlin coroutines
 */
class JobPoller(
    private val gatewayStubKt: GatewayGrpcKt.GatewayCoroutineStub,
    private val requestBuilder: Builder,
    private val jsonMapper: JsonMapper,
    private val requestTimeout: Duration,
    private val retryThrowable: suspend (Throwable) -> Boolean
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

        requestBuilder.maxJobsToActivate = maxJobsToActivate
        this.jobConsumer = jobConsumer
        this.doneCallback = doneCallback
        this.errorCallback = errorCallback
        this.openSupplier = openSupplier

        poll()
    }

    @OptIn(FlowPreview::class)
    private suspend fun poll() {
        logger.trace {
            "Polling at max ${requestBuilder.maxJobsToActivate} jobs for worker ${requestBuilder.worker} and job type ${requestBuilder.type}"
        }
        gatewayStubKt
            .withDeadlineAfter(requestTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .activateJobs(requestBuilder.build())
            .catch {
                if (retryThrowable(it)) {
                    poll()
                } else if (openSupplier()) {
                    try {
                        logFailure(it)
                    } finally {
                        errorCallback(it)
                    }
                }
            }
            .flatMapConcat { it.jobsList.asFlow() }
            .map { ActivatedJobImpl(jsonMapper, it) }
            .collect(jobConsumer)
            .let { pollingDone() }
    }

    private fun logFailure(throwable: Throwable) {
        if (throwable is StatusRuntimeException) {
            if (throwable.status.code == Status.RESOURCE_EXHAUSTED.code) {
                // Log RESOURCE_EXHAUSTED status exceptions only as trace, otherwise it is just too
                // noisy. Furthermore it is not worth to be a warning since it is expected on a fully
                // loaded cluster. It should be handled by our backoff mechanism, but if there is an
                // issue or an configuration mistake the user can turn on trace logging to see this.
                logger.trace(throwable) { buildErrorMessage(requestBuilder.worker, requestBuilder.type) }
                return
            }
        }

        logger.warn(throwable) { buildErrorMessage(requestBuilder.worker, requestBuilder.type) }
    }

    private fun buildErrorMessage(
        worker: String,
        type: String
    ) = "Failed to activate jobs for worker $worker and job type $type"

    private suspend fun pollingDone() {
        if (activatedJobs > 0) {
            logger.debug {
                "Activated $activatedJobs jobs for worker ${requestBuilder.worker} and job type ${requestBuilder.type}"
            }
        } else {
            logger.trace {
                "No jobs activated for worker ${requestBuilder.worker} and job type ${requestBuilder.type}"
            }
        }
        doneCallback(activatedJobs)
    }

    companion object : KLogging()
}
