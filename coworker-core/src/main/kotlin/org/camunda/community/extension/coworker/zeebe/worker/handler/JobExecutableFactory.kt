package org.camunda.community.extension.coworker.zeebe.worker.handler

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import kotlinx.coroutines.CoroutineScope
import mu.KLogging
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler

/**
 * Port of [io.camunda.zeebe.client.impl.worker.JobRunnableFactory] but with Kotlin coroutines
 */
class JobExecutableFactory(
    private val jobClient: JobClient,
    private val jobHandler: JobHandler,
    private val jobErrorHandler: JobErrorHandler
) {

    fun create(
        job: ActivatedJob,
        doneCallback: suspend () -> Unit
    ): suspend CoroutineScope.() -> Unit = { executeJob(job, doneCallback) }

    private suspend fun executeJob(job: ActivatedJob, doneCallback: suspend () -> Unit) {
        try {
            jobHandler.handle(jobClient, job)
        } catch (e: Exception) {
            jobErrorHandler.handleError(e, job, jobClient)
        } finally {
            doneCallback()
        }
    }

    companion object : KLogging()
}
