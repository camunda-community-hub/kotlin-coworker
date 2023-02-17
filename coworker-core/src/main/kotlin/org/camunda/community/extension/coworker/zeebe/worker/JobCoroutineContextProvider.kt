package org.camunda.community.extension.coworker.zeebe.worker

import io.camunda.zeebe.client.api.response.ActivatedJob
import kotlin.coroutines.CoroutineContext

fun interface JobCoroutineContextProvider {

    fun provide(job: ActivatedJob): CoroutineContext
}
