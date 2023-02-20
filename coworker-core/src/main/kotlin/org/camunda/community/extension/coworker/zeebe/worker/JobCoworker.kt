package org.camunda.community.extension.coworker.zeebe.worker

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.BackoffSupplier
import io.camunda.zeebe.client.api.worker.JobWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KLogging
import org.camunda.community.extension.coworker.zeebe.worker.handler.JobExecutableFactory
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * Port of [io.camunda.zeebe.client.impl.worker.JobWorkerImpl] but with Kotlin coroutines
 */
class JobCoworker(
    private val maxJobsActive: Int,
    private val activationThreshold: Int = (maxJobsActive * 0.3f).roundToInt(),
    private val remainingJobs: AtomicInteger = AtomicInteger(0),
    private val scheduledCoroutineContext: CoroutineContext,
    private val jobExecutableFactory: JobExecutableFactory,
    private val initialPollInterval: Duration,
    private val backoffSupplier: BackoffSupplier,
    jobPoller: JobPoller,
    private val additionalCoroutineContextProvider: JobCoroutineContextProvider
) : JobWorker, Closeable {

    private val acquiringJobs = AtomicBoolean(true)
    private val claimableJobPoller: AtomicReference<JobPoller?> = AtomicReference(jobPoller)
    private val isPollScheduled = AtomicBoolean(false)

    @Volatile
    private var pollInterval = initialPollInterval

    init {
        schedulePoll()
    }

    override fun isOpen(): Boolean {
        return acquiringJobs.get()
    }

    override fun isClosed(): Boolean {
        return !isOpen && claimableJobPoller.get() != null && remainingJobs.get() <= 0
    }

    override fun close() {
        acquiringJobs.set(false)
    }

    @OptIn(ExperimentalTime::class)
    private fun schedulePoll() {
        if (isPollScheduled.compareAndSet(false, true)) {
            CoroutineScope(scheduledCoroutineContext).launch {
                delay(pollInterval)
                onScheduledPoll()
            }
        }
    }

    private suspend fun onScheduledPoll() {
        isPollScheduled.set(false)
        val actualRemainingJobs = remainingJobs.get()
        if (shouldPoll(actualRemainingJobs)) {
            tryPoll()
        }
    }

    private fun shouldPoll(remainingJobs: Int): Boolean {
        return acquiringJobs.get() && remainingJobs <= activationThreshold
    }

    private fun tryClaimJobPoller(): JobPoller? {
        return claimableJobPoller.getAndSet(null)
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
        val actualRemainingJobs = remainingJobs.get()
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
            { error: Throwable -> onPollError(jobPoller, error) }
        ) { this.isOpen }
    }

    private fun releaseJobPoller(jobPoller: JobPoller) {
        claimableJobPoller.set(jobPoller)
    }

    /** Apply the backoff strategy by scheduling the next poll at a new interval  */
    private fun backoff(jobPoller: JobPoller, error: Throwable) {
        val prevInterval = pollInterval
        pollInterval = try {
            backoffSupplier.supplyRetryDelay(prevInterval.inWholeMilliseconds).milliseconds
        } catch (e: Exception) {
            logger.warn(e) { "Expected to supply retry delay, but an exception was thrown. Falling back to default backoff supplier" }
            DEFAULT_BACKOFF_SUPPLIER.supplyRetryDelay(prevInterval.inWholeMilliseconds).milliseconds
        }
        logger.debug {
            "Failed to activate jobs due to ${error.message}, delay retry for $pollInterval ms"
        }
        releaseJobPoller(jobPoller)
        schedulePoll()
    }

    private fun onPollSuccess(jobPoller: JobPoller, activatedJobs: Int) {
        // first release, then lookup remaining jobs, to allow handleJobFinished() to poll
        releaseJobPoller(jobPoller)
        val actualRemainingJobs = remainingJobs.addAndGet(activatedJobs)
        pollInterval = initialPollInterval
        if (actualRemainingJobs <= 0) {
            schedulePoll()
        }
        // if jobs were activated, then successive polling happens due to handleJobFinished
    }

    private fun onPollError(jobPoller: JobPoller, error: Throwable) {
        backoff(jobPoller, error)
    }

    private fun handleJob(job: ActivatedJob) {
        CoroutineScope(scheduledCoroutineContext + additionalCoroutineContextProvider.provide(job))
            .launch(block = jobExecutableFactory.create(job) { handleJobFinished() })
    }

    private suspend fun handleJobFinished() {
        val actualRemainingJobs = remainingJobs.decrementAndGet()
        if (!isPollScheduled.get() && shouldPoll(actualRemainingJobs)) {
            tryPoll()
        }
    }

    companion object: KLogging() {
        private val DEFAULT_BACKOFF_SUPPLIER = BackoffSupplier.newBackoffBuilder().build()
    }
}
