package org.camunda.community.extension.coworker.spring

import io.camunda.zeebe.client.ZeebeClient
import org.camunda.community.extension.coworker.Cozeebe
import org.camunda.community.extension.coworker.toCozeebe
import org.camunda.community.extension.coworker.zeebe.worker.handler.JobHandler

/**
 * We need to initialize [Cozeebe] not at the startup time because [ZeebeClient] has a
 * comprehended lifecycle [io.camunda.zeebe.spring.client.lifecycle.ZeebeClientLifecycle]
 * and it is not available at startup time. So I will initialize the [Cozeebe] instance lazily
 * later (the first time that we call any method of it).
 */
open class LazyCozeebe(
    private val zeebeClient: ZeebeClient,
) : Cozeebe {
    private val innerCozeebe: Cozeebe by lazy {
        zeebeClient.toCozeebe()
    }

    override fun newCoWorker(
        jobType: String,
        jobHandler: JobHandler,
    ) = innerCozeebe.newCoWorker(jobType, jobHandler)

    override fun configuration() = innerCozeebe.configuration()

    override fun close() {
        innerCozeebe.close()
    }
}
