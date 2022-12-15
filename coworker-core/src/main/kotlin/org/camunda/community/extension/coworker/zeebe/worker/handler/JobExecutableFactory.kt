package org.camunda.community.extension.coworker.zeebe.worker.handler

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import mu.KLogging
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Port of [io.camunda.zeebe.client.impl.worker.JobRunnableFactory] but with Kotlin coroutines
 */
class JobExecutableFactory(
    private val jobClient: JobClient,
    private val jobHandler: JobHandler
) {

    fun create(
        job: ActivatedJob,
        doneCallback: suspend () -> Unit
    ): suspend CoroutineScope.() -> Unit = { executeJob(job, doneCallback) }

    private suspend fun executeJob(job: ActivatedJob, doneCallback: suspend () -> Unit) {
        try {
            jobHandler.handle(jobClient, job)
        } catch (e: Exception) {
            logger.warn(e) {
                "Worker ${job.worker} failed to handle job with key ${job.key} of type ${job.type}, sending fail command to broker"
            }
            val stringWriter = StringWriter()
            val printWriter = PrintWriter(stringWriter)
            e.printStackTrace(printWriter)
            val message = stringWriter.toString()
            jobClient
                .newFailCommand(job.key)
                .retries(job.retries - 1)
                .errorMessage(message)
                .send()
                .await()
        } finally {
            doneCallback()
        }
    }

    companion object : KLogging()
}
