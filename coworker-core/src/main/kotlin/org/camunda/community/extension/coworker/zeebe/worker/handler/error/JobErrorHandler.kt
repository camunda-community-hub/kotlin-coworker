package org.camunda.community.extension.coworker.zeebe.worker.handler.error

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient

fun interface JobErrorHandler {

    suspend fun handleError(e: Exception, activatedJob: ActivatedJob, jobClient: JobClient)
}
