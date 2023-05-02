package org.camunda.community.extension.coworker.spring.error

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.impl.DefaultJobErrorHandler

class DefaultSpringZeebeErrorHandler(
    private val jobErrorHandler: JobErrorHandler = DefaultJobErrorHandler(),
    private val metricsRecorder: MetricsRecorder
): JobErrorHandler {
    override suspend fun handleError(e: Exception, activatedJob: ActivatedJob, jobClient: JobClient) {
        metricsRecorder.increase(MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_FAILED, activatedJob.type)
        jobErrorHandler.handleError(e.stripSpringZeebeExceptionIfNeeded(), activatedJob, jobClient)
    }
}
