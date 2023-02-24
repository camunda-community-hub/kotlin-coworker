package org.camunda.community.extension.coworker.spring.error

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.impl.DefaultJobErrorHandler

class DefaultSpringZeebeErrorHandler(
    private val jobErrorHandler: JobErrorHandler = DefaultJobErrorHandler()
): JobErrorHandler {
    override suspend fun handleError(e: Exception, activatedJob: ActivatedJob, jobClient: JobClient) {
        jobErrorHandler.handleError(e.stripSpringZeebeExceptionIfNeeded(), activatedJob, jobClient)
    }
}
