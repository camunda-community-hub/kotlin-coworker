package org.camunda.community.extension.coworker.spring.annotation

import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.client.api.worker.JobWorker
import io.camunda.zeebe.spring.client.bean.ParameterInfo
import org.camunda.community.extension.coworker.Cozeebe
import org.camunda.community.extension.coworker.zeebe.worker.JobCoroutineContextProvider
import org.camunda.community.extension.coworker.zeebe.worker.builder.JobCoworkerBuilder
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

class CoworkerManager(
    private val jobCoroutineContextProvider: JobCoroutineContextProvider,
    private val jobErrorHandler: JobErrorHandler
) {

    private val openedWorkers: MutableList<JobWorker> = mutableListOf()

    fun openWorker(coworkerValue: CoworkerValue, cozeebe: Cozeebe) {
        val coWorker = cozeebe.newCoWorker(coworkerValue.type) { client, job ->
            suspendCoroutineUninterceptedOrReturn { it: Continuation<Any> ->
                val args = createArguments(client, job, it, coworkerValue.methodInfo.parameters)
                coworkerValue.methodInfo.invoke(*(args.toTypedArray()))
            }
        }.also { builder: JobCoworkerBuilder ->
            builder.additionalCoroutineContextProvider = jobCoroutineContextProvider
            builder.jobErrorHandler = jobErrorHandler
        }
        val worker = coWorker.open()
        openedWorkers.add(worker)
    }

    private fun createArguments(
        jobClient: JobClient,
        activatedJob: ActivatedJob,
        continuation: Continuation<Any>,
        parameters: List<ParameterInfo>
    ): List<Any> = parameters
        .flatMap {
            val paramType = it.parameterInfo.type
            val args = mutableListOf<Any>()
            if (JobClient::class.java.isAssignableFrom(paramType)) {
                args.add(jobClient)
            } else if (ActivatedJob::class.java.isAssignableFrom(paramType)) {
                args.add(activatedJob)
            } else if (Continuation::class.java.isAssignableFrom(paramType)) {
                args.add(continuation)
            }
            args
        }

    fun closeAllWorkers() {
        openedWorkers.forEach(JobWorker::close)
    }
}
