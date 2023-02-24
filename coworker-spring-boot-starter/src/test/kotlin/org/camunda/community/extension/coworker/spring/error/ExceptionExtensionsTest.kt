package org.camunda.community.extension.coworker.spring.error

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExceptionExtensionsTest {

    @Test
    fun `should strip exception from spring zeebe`() {
        // given
        val mockWorkerException = mockk<Exception>()
        val springZeebeException = mockk<RuntimeException> {
            every { stackTrace } returns arrayOf(
                mockk {
                    every { methodName } returns "invoke"
                    every { className } returns "io.camunda.zeebe.spring.client.bean.MethodInfo"
                },
                mockk {
                    every { methodName } returns "someMethod"
                    every { className } returns "java.lang.Object"
                }
            )
            every { cause } returns mockWorkerException
        }

        // when
        val workerException = springZeebeException.stripSpringZeebeExceptionIfNeeded()

        // then
        assertThat(workerException).isSameAs(mockWorkerException)
    }

    @Test
    fun `should return same exception if it is not from MethodInfo#invoke`() {
        // given
        val mockWorkerException = mockk<Exception>()
        val springZeebeException = mockk<RuntimeException> {
            every { stackTrace } returns arrayOf(
                mockk {
                    every { methodName } returns "someMethod"
                    every { className } returns "java.lang.Object"
                }
            )
            every { cause } returns mockWorkerException
        }

        // when
        val workerException = springZeebeException.stripSpringZeebeExceptionIfNeeded()

        // then
        assertThat(workerException).isSameAs(springZeebeException)
    }

    @Test
    fun `should return same exception if from MethodInfo#invoke but without cause`() {
        // given
        val springZeebeException = mockk<RuntimeException> {
            every { stackTrace } returns arrayOf(
                mockk {
                    every { methodName } returns "invoke"
                    every { className } returns "io.camunda.zeebe.spring.client.bean.MethodInfo"
                },
                mockk {
                    every { methodName } returns "someMethod"
                    every { className } returns "java.lang.Object"
                }
            )
            every { cause } returns null
        }

        // when
        val workerException = springZeebeException.stripSpringZeebeExceptionIfNeeded()

        // then
        assertThat(workerException).isSameAs(springZeebeException)
    }

    @Test
    fun `should return same exception if cause is throwable`() {
        // given
        val mockWorkerException = mockk<Throwable>()
        val springZeebeException = mockk<RuntimeException> {
            every { stackTrace } returns arrayOf(
                mockk {
                    every { methodName } returns "invoke"
                    every { className } returns "io.camunda.zeebe.spring.client.bean.MethodInfo"
                },
                mockk {
                    every { methodName } returns "someMethod"
                    every { className } returns "java.lang.Object"
                }
            )
            every { cause } returns mockWorkerException
        }

        // when
        val workerException = springZeebeException.stripSpringZeebeExceptionIfNeeded()

        // then
        assertThat(workerException).isSameAs(springZeebeException)
    }
}
