package org.camunda.community.extension.coworker.zeebe.worker

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.BackoffSupplier
import io.camunda.zeebe.client.api.worker.JobWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KLogging
import org.camunda.community.extension.coworker.zeebe.worker.handler.JobExecutableFactory
import java.io.Closeable
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Port of [io.camunda.zeebe.client.impl.worker.JobWorkerImpl] but with Kotlin coroutines
 */
class JobCoworker(
    private val maxJobsActive: Int,
    private val activationThreshold: Int = (maxJobsActive * JOB_LOAD_FACTOR).roundToInt(),
    private val scheduledCoroutineContext: CoroutineContext,
    private val jobExecutableFactory: JobExecutableFactory,
    private val initialPollInterval: Duration,
    private val backoffSupplier: BackoffSupplier,
    jobPoller: JobPoller,
    private val additionalCoroutineContextProvider: JobCoroutineContextProvider,
) : JobWorker, Closeable {
    private val remainingJobsMutex = Mutex()
    private var remainingJobs = 0

    private val isPollScheduledMutex = Mutex()
    private var isPollScheduled = false

    private val acquiringJobsMutex = Mutex()
    private var acquiringJobs = true

    private val pollIntervalMutex = Mutex()
    private var pollInterval = initialPollInterval

    private val jobPollerMutex = Mutex()
    private var claimableJobPoller: JobPoller? = jobPoller

    init {
        runBlocking {
            schedulePoll()
        }
    }

    override fun isOpen(): Boolean = runBlocking { isOpenWithSuspension() }

    suspend fun isOpenWithSuspension(): Boolean = acquiringJobsMutex.withLock { acquiringJobs }

    override fun isClosed(): Boolean = runBlocking { isClosedWithSuspension() }

    suspend fun isClosedWithSuspension(): Boolean =
        !isOpenWithSuspension() && jobPollerMutex.withLock { claimableJobPoller } != null &&
            remainingJobsMutex.withLock { remainingJobs <= 0 }

    override fun close() = runBlocking { closeWithSuspension() }

    suspend fun closeWithSuspension() {
        acquiringJobsMutex.withLock { acquiringJobs = false }
    }

    private suspend fun schedulePoll() {
        if (isPollScheduledMutex.withLock {
                if (!isPollScheduled) {
                    isPollScheduled = true
                }
                isPollScheduled
            }
        ) {
            CoroutineScope(scheduledCoroutineContext).launch {
                delay(pollIntervalMutex.withLock { pollInterval })
                onScheduledPoll()
            }
        }
    }

    private suspend fun onScheduledPoll() {
        isPollScheduledMutex.withLock { isPollScheduled = false }
        val actualRemainingJobs = remainingJobsMutex.withLock { remainingJobs }
        if (shouldPoll(actualRemainingJobs)) {
            tryPoll()
        }
    }

    private suspend fun shouldPoll(remainingJobs: Int): Boolean =
        acquiringJobsMutex.withLock { acquiringJobs } && remainingJobs <= activationThreshold

    private suspend fun tryClaimJobPoller(): JobPoller? =
        jobPollerMutex.withLock {
            val result = claimableJobPoller
            claimableJobPoller = null
            result
        }

    private suspend fun tryPoll() {
        val jobPoller = tryClaimJobPoller()
        if (jobPoller != null) {
            try {
                poll(jobPoller)
            } catch (error: Exception) {
                logger.warn(error) {
                    "Unexpected failure to activate jobs"
                }
                backoff(jobPoller, error)
            }
        }
    }

    private suspend fun poll(jobPoller: JobPoller) {
        // check the condition again within the critical section
        // to avoid race conditions that would let us exceed the buffer size
        val actualRemainingJobs = remainingJobsMutex.withLock { remainingJobs }
        if (!shouldPoll(actualRemainingJobs)) {
            logger.trace { "Expected to activate for jobs, but still enough remain. Reschedule poll." }
            releaseJobPoller(jobPoller)
            schedulePoll()
            return
        }
        val maxJobsToActivate = maxJobsActive - actualRemainingJobs
        jobPoller.poll(
            maxJobsToActivate,
            { job: ActivatedJob -> handleJob(job) },
            { activatedJobs: Int -> onPollSuccess(jobPoller, activatedJobs) },
            { error: Throwable -> onPollError(jobPoller, error) },
        ) { this.isOpenWithSuspension() }
    }

    private suspend fun releaseJobPoller(jobPoller: JobPoller) {
        jobPollerMutex.withLock { claimableJobPoller = jobPoller }
    }

    /** Apply the backoff strategy by scheduling the next poll at a new interval  */
    private suspend fun backoff(
        jobPoller: JobPoller,
        error: Throwable,
    ) {
        val prevInterval = pollIntervalMutex.withLock { pollInterval }
        try {
            val delay = backoffSupplier.supplyRetryDelay(prevInterval.inWholeMilliseconds).milliseconds
            pollIntervalMutex.withLock { pollInterval = delay }
        } catch (e: Exception) {
            logger.warn(e) { "Expected to supply retry delay, but an exception was thrown. Falling back to default backoff supplier" }
            val defaultDelay = DEFAULT_BACKOFF_SUPPLIER.supplyRetryDelay(prevInterval.inWholeMilliseconds).milliseconds
            pollIntervalMutex.withLock { pollInterval = defaultDelay }
        }
        val delay = pollIntervalMutex.withLock { pollInterval }
        logger.debug {
            "Failed to activate jobs due to ${error.message}, delay retry for $delay ms"
        }
        releaseJobPoller(jobPoller)
        schedulePoll()
    }

    private suspend fun onPollSuccess(
        jobPoller: JobPoller,
        activatedJobs: Int,
    ) {
        // first release, then lookup remaining jobs, to allow handleJobFinished() to poll
        releaseJobPoller(jobPoller)
        val actualRemainingJobs =
            remainingJobsMutex.withLock {
                val result = remainingJobs
                remainingJobs += activatedJobs
                result
            }
        pollInterval = initialPollInterval
        if (actualRemainingJobs <= 0) {
            schedulePoll()
        }
        // if jobs were activated, then successive polling happens due to handleJobFinished
    }

    private suspend fun onPollError(
        jobPoller: JobPoller,
        error: Throwable,
    ) {
        backoff(jobPoller, error)
    }

    private fun handleJob(job: ActivatedJob) {
        CoroutineScope(scheduledCoroutineContext + additionalCoroutineContextProvider.provide(job))
            .launch(block = jobExecutableFactory.create(job) { handleJobFinished() })
    }

    private suspend fun handleJobFinished() {
        val actualRemainingJobs =
            remainingJobsMutex.withLock {
                val result = remainingJobs
                remainingJobs -= 1
                result
            }
        if (isPollScheduledMutex.withLock { !isPollScheduled } && shouldPoll(actualRemainingJobs)) {
            tryPoll()
        }
    }

    companion object : KLogging() {
        private val DEFAULT_BACKOFF_SUPPLIER = BackoffSupplier.newBackoffBuilder().build()
        private const val JOB_LOAD_FACTOR = 0.3f
    }
}
