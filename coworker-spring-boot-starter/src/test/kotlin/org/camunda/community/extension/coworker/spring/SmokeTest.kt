package org.camunda.community.extension.coworker.spring

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat
import io.camunda.zeebe.spring.test.ZeebeSpringTest
import io.camunda.zeebe.spring.test.ZeebeTestThreadSupport
import kotlinx.coroutines.future.await
import org.assertj.core.api.Assertions
import org.camunda.community.extension.coworker.spring.annotation.Coworker
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.properties.Delegates

@ZeebeSpringTest
@SpringBootTest(classes = [CoworkerAutoConfiguration::class, SmokeTest::class])
open class SmokeTest {

    private var firstCalled by Delegates.notNull<Boolean>()

    @Autowired
    private lateinit var zeebeClient: ZeebeClient

    @Coworker(type = "test1")
    suspend fun firstWorker(jobClient: JobClient, activatedJob: ActivatedJob) {
        firstCalled = true
        jobClient.newCompleteCommand(activatedJob.key).send().await()
    }

    @Test
    fun `should suspend worker be called`() {
        // given
        val testBpmn = Bpmn
            .createExecutableProcess("test1")
            .startEvent()
            .serviceTask().zeebeJobType("test1")
            .endEvent()
            .done()

        zeebeClient.newDeployResourceCommand().addProcessModel(testBpmn, "test1.bpmn").send().join()

        // when
        val processInstanceEvent = zeebeClient.newCreateInstanceCommand().bpmnProcessId("test1").latestVersion().send().join()

        // then
        assertThat(processInstanceEvent).isStarted
        ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstanceEvent)
        Assertions.assertThat(firstCalled).isTrue
    }
}
