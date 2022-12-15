package org.camunda.community.extension.coworker

import io.camunda.zeebe.client.ZeebeClient
import org.camunda.community.extension.coworker.impl.CozeebeImpl

fun ZeebeClient.toCozeebe() = CozeebeImpl(this.configuration)

fun Cozeebe.toZeebeClient() = ZeebeClient.newClient(this.configuration())
