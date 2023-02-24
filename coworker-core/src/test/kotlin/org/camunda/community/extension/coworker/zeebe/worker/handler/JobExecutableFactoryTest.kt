package org.camunda.community.extension.coworker.zeebe.worker.handler

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JobExecutableFactoryTest {
    private lateinit var jobExecutableFactory: JobExecutableFactory
    private lateinit var jobClient: JobClient
    private lateinit var jobHandler: JobHandler
    private lateinit var jobErrorHandler: JobErrorHandler

    @BeforeEach
    fun setUp() {
        jobClient = mockk()
        jobHandler = mockk()
        jobErrorHandler = mockk()
        jobExecutableFactory = JobExecutableFactory(jobClient, jobHandler, jobErrorHandler)
    }

    @Test
    fun `should not call job error handler if job is success`() {
        // given
        val activatedJob = mockk<ActivatedJob>()
        val doneCallback = mockk<suspend () -> Unit> {
            coJustRun { this@mockk.invoke() }
        }
        coJustRun { jobHandler.handle(jobClient, activatedJob) }

        // when
        runBlocking {
            jobExecutableFactory.create(activatedJob, doneCallback).invoke(this)
        }

        // then
        coVerify(exactly = 1) { jobHandler.handle(jobClient, activatedJob) }
        coVerify(exactly = 1) { doneCallback.invoke() }
        coVerify(exactly = 0) { jobErrorHandler.handleError(any(), activatedJob, jobClient) }
    }

    @Test
    fun `should call job error handler in case of exception`() {
        // given
        val activatedJob = mockk<ActivatedJob>()
        val doneCallback = mockk<suspend () -> Unit> {
            coJustRun { this@mockk.invoke() }
        }
        val exception = Exception("oops, something bad happens!")
        coEvery { jobHandler.handle(jobClient, activatedJob) } throws exception
        coJustRun { jobErrorHandler.handleError(exception, activatedJob, jobClient) }

        // when
        runBlocking {
            jobExecutableFactory.create(activatedJob, doneCallback).invoke(this)
        }

        // then
        coVerify(exactly = 1) { jobHandler.handle(jobClient, activatedJob) }
        coVerify(exactly = 1) { doneCallback.invoke() }
        coVerify(exactly = 1) { jobErrorHandler.handleError(any(), activatedJob, jobClient) }
    }
}
