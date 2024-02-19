package org.camunda.community.extension.coworker

import io.camunda.zeebe.client.ZeebeClientConfiguration
import org.camunda.community.extension.coworker.zeebe.worker.builder.JobCoworkerBuilder
import org.camunda.community.extension.coworker.zeebe.worker.handler.JobHandler

interface Cozeebe : AutoCloseable {
    fun newCoWorker(
        jobType: String,
        jobHandler: JobHandler,
    ): JobCoworkerBuilder

    fun configuration(): ZeebeClientConfiguration
}
