package org.camunda.community.extension.coworker.spring.annotation

import io.camunda.zeebe.client.api.JsonMapper
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.client.api.worker.JobWorker
import io.camunda.zeebe.spring.client.annotation.CustomHeaders
import io.camunda.zeebe.spring.client.annotation.Variable
import io.camunda.zeebe.spring.client.annotation.VariablesAsType
import io.camunda.zeebe.spring.client.annotation.ZeebeCustomHeaders
import io.camunda.zeebe.spring.client.annotation.ZeebeVariable
import io.camunda.zeebe.spring.client.annotation.ZeebeVariablesAsType
import io.camunda.zeebe.spring.client.bean.ParameterInfo
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder
import org.camunda.community.extension.coworker.Cozeebe
import org.camunda.community.extension.coworker.zeebe.worker.JobCoroutineContextProvider
import org.camunda.community.extension.coworker.zeebe.worker.builder.JobCoworkerBuilder
import org.camunda.community.extension.coworker.zeebe.worker.handler.error.JobErrorHandler
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

class CoworkerManager(
    private val jobCoroutineContextProvider: JobCoroutineContextProvider,
    private val jobErrorHandler: JobErrorHandler,
    private val jsonMapper: JsonMapper,
    private val metricsRecorder: MetricsRecorder
) {

    private val openedWorkers: MutableList<JobWorker> = mutableListOf()

    fun openWorker(coworkerValue: CoworkerValue, cozeebe: Cozeebe) {
        val coWorker = cozeebe.newCoWorker(coworkerValue.type) { client, job ->
            suspendCoroutineUninterceptedOrReturn { it: Continuation<Any> ->
                val args = createArguments(client, job, it, coworkerValue.methodInfo.parameters)
                try {
                    metricsRecorder.increase(MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_ACTIVATED, job.type)
                    coworkerValue.methodInfo.invoke(*(args.toTypedArray()))
                } catch (ex: Exception) {
                    metricsRecorder.increase(MetricsRecorder.METRIC_NAME_JOB, MetricsRecorder.ACTION_FAILED, job.type)
                    throw ex
                }
            }
        }.also { builder: JobCoworkerBuilder ->
            builder.additionalCoroutineContextProvider = jobCoroutineContextProvider
            builder.jobErrorHandler = jobErrorHandler
            builder.workerName = coworkerValue.name
            builder.timeout = coworkerValue.timeout
            builder.maxJobsActive = coworkerValue.maxJobsActive
            builder.requestTimeout = coworkerValue.requestTimeout
            builder.pollInterval = coworkerValue.pollInterval
            builder.fetchVariables = coworkerValue.fetchVariables
        }
        val worker = coWorker.open()
        openedWorkers.add(worker)
    }

    private fun createArguments(
        jobClient: JobClient,
        activatedJob: ActivatedJob,
        continuation: Continuation<Any>,
        parameters: List<ParameterInfo>
    ): List<Any> = parameters.flatMap {
            val parameterInfo = it.parameterInfo
            val paramType = parameterInfo.type
            val args = mutableListOf<Any>()
            if (JobClient::class.java.isAssignableFrom(paramType)) {
                args.add(jobClient)
            } else if (ActivatedJob::class.java.isAssignableFrom(paramType)) {
                args.add(activatedJob)
            } else if (Continuation::class.java.isAssignableFrom(paramType)) {
                args.add(continuation)
            } else if (parameterInfo.isAnnotationPresent(Variable::class.java) || parameterInfo.isAnnotationPresent(
                    ZeebeVariable::class.java
                )
            ) {
                val variable = activatedJob.variablesAsMap[it.parameterName]
                val arg = if (variable != null && !paramType.isInstance(variable)) {
                    jsonMapper.fromJson(jsonMapper.toJson(variable), paramType)
                } else {
                    paramType.cast(variable)
                }
                args.add(arg)
            } else if (parameterInfo.isAnnotationPresent(VariablesAsType::class.java) || parameterInfo.isAnnotationPresent(
                    ZeebeVariablesAsType::class.java
                )
            ) {
                args.add(activatedJob.getVariablesAsType(paramType))
            } else if (parameterInfo.isAnnotationPresent(CustomHeaders::class.java) || parameterInfo.isAnnotationPresent(
                    ZeebeCustomHeaders::class.java
                )
            ) {
                args.add(activatedJob.customHeaders)
            }
            args
        }

    fun closeAllWorkers() {
        openedWorkers.forEach(JobWorker::close)
    }
}
