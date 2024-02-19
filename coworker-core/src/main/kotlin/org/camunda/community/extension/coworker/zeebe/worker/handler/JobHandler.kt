package org.camunda.community.extension.coworker.zeebe.worker.handler

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient

fun interface JobHandler {
    suspend fun handle(
        client: JobClient,
        job: ActivatedJob,
    )
}
