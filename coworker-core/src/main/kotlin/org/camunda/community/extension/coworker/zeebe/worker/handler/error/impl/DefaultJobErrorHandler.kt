package org.camunda.community.extension.coworker.zeebe.worker.handler.error.impl

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import kotlinx.coroutines.future.await
import mu.KLogging
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler
import java.io.PrintWriter
import java.io.StringWriter

class DefaultJobErrorHandler: JobErrorHandler {
    override suspend fun handleError(e: Exception, activatedJob: ActivatedJob, jobClient: JobClient) {
        logger.warn(e) {
            "Worker ${activatedJob.worker} failed to handle job with key ${activatedJob.key} of type ${activatedJob.type}, sending fail command to broker"
        }
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        e.printStackTrace(printWriter)
        val message = stringWriter.toString()
        jobClient
            .newFailCommand(activatedJob.key)
            .retries(activatedJob.retries - 1)
            .errorMessage(message)
            .send()
            .await()
    }

    companion object: KLogging()
}
