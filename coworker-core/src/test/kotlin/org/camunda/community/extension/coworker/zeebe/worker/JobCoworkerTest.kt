package org.camunda.community.extension.coworker.zeebe.worker

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.slf4j.MDCContext
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.extension.coworker.zeebe.worker.builder.DEFAULT_BACKOFF_SUPPLIER
import org.camunda.community.extension.coworker.zeebe.worker.handler.JobExecutableFactory
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@Suppress("UNREACHABLE_CODE", "UNCHECKED_CAST")
class JobCoworkerTest {
    @Test
    fun `should be polled expected counts`() {
        // given
        val standardDelay: Long = 100
        val jobExecutableFactory =
            mockk<JobExecutableFactory> {
                every { create(any(), any()) } answers {
                    val doneCallback = this.args[1] as suspend () -> Unit

                    object : suspend CoroutineScope.() -> Unit {
                        override suspend fun invoke(coroutineScope: CoroutineScope) {
                            delay(standardDelay * 3)
                            doneCallback()
                        }
                    }
                }
            }
        val firstWaitPeriod = standardDelay * 3
        val standardWaitPeriod = standardDelay * 2
        val jobPoller =
            mockk<JobPoller> {
                coEvery { poll(any(), any(), any(), any(), any()) } coAnswers {
                    val jobConsumer = args[1] as suspend (ActivatedJob) -> Unit
                    val doneCallback = args[2] as suspend (Int) -> Unit
                    delay(firstWaitPeriod)
                    jobConsumer(mockk())
                    jobConsumer(mockk())
                    doneCallback(2)
                } coAndThen {
                    val doneCallback = args[2] as suspend (Int) -> Unit
                    delay(standardWaitPeriod)
                    doneCallback(0)
                }
            }
        // when
        JobCoworker(
            maxJobsActive = 32,
            scheduledCoroutineContext = Executors.newScheduledThreadPool(1).asCoroutineDispatcher(),
            additionalCoroutineContextProvider = { MDCContext() },
            backoffSupplier = DEFAULT_BACKOFF_SUPPLIER,
            initialPollInterval = standardDelay.milliseconds,
            jobExecutableFactory = jobExecutableFactory,
            jobPoller = jobPoller,
        )
        val waitPeriod: Long = 5000
        TimeUnit.MILLISECONDS.sleep(waitPeriod)

        val expectedCountOfPolling = ((waitPeriod - standardDelay - firstWaitPeriod) / (standardWaitPeriod + standardDelay)).toInt()

        // then
        coVerify(
            atLeast = expectedCountOfPolling - (expectedCountOfPolling * 0.1).roundToInt(),
            atMost = expectedCountOfPolling + (expectedCountOfPolling * 0.1).roundToInt(),
        ) { jobPoller.poll(any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { jobExecutableFactory.create(any(), any()) }
    }

    @Test
    fun `should not poll if reach maximum job limit`() {
        // given
        val jobExecutableFactory =
            mockk<JobExecutableFactory> {
                every { create(any(), any()) } answers {
                    object : suspend CoroutineScope.() -> Unit {
                        override suspend fun invoke(coroutineScope: CoroutineScope) {
                            // should never be executed in test
                            delay(2.days)
                            throw error("should be never executed")
                        }
                    }
                }
            }
        val latch = CountDownLatch(2)
        val maxJobsActive = 4
        val activatedJobs = CopyOnWriteArrayList<ActivatedJob>(ArrayList(maxJobsActive))
        val jobPoller =
            mockk<JobPoller> {
                coEvery { poll(any(), any(), any(), any(), any()) } coAnswers {
                    val maxJobToPoll = args[0] as Int
                    val jobConsumer = args[1] as suspend (ActivatedJob) -> Unit
                    val doneCallback = args[2] as suspend (Int) -> Unit
                    delay(50.milliseconds)
                    repeat((1..maxJobToPoll).count()) {
                        val activatedJob = mockk<ActivatedJob>()
                        activatedJobs.add(activatedJob)
                        jobConsumer(activatedJob)
                    }
                    doneCallback(maxJobToPoll)
                    latch.countDown()
                }
            }

        // when
        JobCoworker(
            maxJobsActive = maxJobsActive,
            scheduledCoroutineContext = Executors.newScheduledThreadPool(1).asCoroutineDispatcher(),
            additionalCoroutineContextProvider = { MDCContext() },
            backoffSupplier = DEFAULT_BACKOFF_SUPPLIER,
            initialPollInterval = 50.milliseconds,
            jobExecutableFactory = jobExecutableFactory,
            jobPoller = jobPoller,
        )

        // then
        latch.await(5, TimeUnit.SECONDS)
        assertThat(activatedJobs).hasSize(maxJobsActive)
    }
}
