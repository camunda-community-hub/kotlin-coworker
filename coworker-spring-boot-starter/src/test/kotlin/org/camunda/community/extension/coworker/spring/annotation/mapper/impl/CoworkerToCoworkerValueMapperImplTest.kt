package org.camunda.community.extension.coworker.spring.annotation.mapper.impl

import io.camunda.zeebe.spring.client.annotation.Variable
import io.camunda.zeebe.spring.client.annotation.ZeebeVariable
import io.camunda.zeebe.spring.client.bean.MethodInfo
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.extension.coworker.spring.annotation.Coworker
import org.camunda.community.extension.coworker.spring.annotation.evaluation.AnnotationValueEvaluator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.time.toKotlinDuration

class CoworkerToCoworkerValueMapperImplTest {
    private lateinit var coworkerToCoworkerValueMapperImpl: CoworkerToCoworkerValueMapperImpl
    private lateinit var annotationValueEvaluator: AnnotationValueEvaluator

    @BeforeEach
    fun setUp() {
        annotationValueEvaluator = mockk(relaxed = true)
        coworkerToCoworkerValueMapperImpl = CoworkerToCoworkerValueMapperImpl(annotationValueEvaluator)
    }

    @Test
    fun `should map correctly if no variables annotation`() {
        // given
        val mockType = "mockType"
        val mockName = "mockName"
        val mockTimeout = "mockTimeout"
        val mockMaxJobsActive = "mockMaxJobsActive"
        val mockRequestTimeout = "mockRequestTimeout"
        val mockPollInterval = "mockPollInterval"
        val mockForceFetchAllVariables = "mockForceFetchAllVariables"
        val mockEnabled = "mockEnabled"
        val coworkerAnnotation =
            mockk<Coworker> {
                every { type } returns mockType
                every { name } returns mockName
                every { timeout } returns mockTimeout
                every { maxJobsActive } returns mockMaxJobsActive
                every { requestTimeout } returns mockRequestTimeout
                every { pollInterval } returns mockPollInterval
                every { fetchVariables } returns "mockFetchVariables"
                every { forceFetchAllVariables } returns mockForceFetchAllVariables
                every { enabled } returns mockEnabled
            }
        val methodInfo = mockk<MethodInfo>()
        val typeContextMap = mapOf("methodInfo" to methodInfo)
        val resultType = "resultType"
        val commonContextMap = typeContextMap + mapOf("type" to resultType)
        every {
            annotationValueEvaluator.evaluate<String>(
                mockType,
                typeContextMap,
            )
        } returns resultType
        val resultName = "resultName"
        every {
            annotationValueEvaluator.evaluate<String>(
                mockName,
                commonContextMap,
            )
        } returns resultName
        val resultTimeout = Duration.ofSeconds(1)
        every {
            annotationValueEvaluator.evaluate<Duration>(
                mockTimeout,
                commonContextMap,
            )
        } returns resultTimeout
        val resultMaxJobsActive = 2
        every {
            annotationValueEvaluator.evaluate<Int>(
                mockMaxJobsActive,
                commonContextMap,
            )
        } returns resultMaxJobsActive
        val resultRequestTimeout = Duration.ofMinutes(3)
        every {
            annotationValueEvaluator.evaluate<Duration>(
                mockRequestTimeout,
                commonContextMap,
            )
        } returns resultRequestTimeout
        val resultPollInterval = Duration.ofMillis(4)
        every {
            annotationValueEvaluator.evaluate<Duration>(
                mockPollInterval,
                commonContextMap,
            )
        } returns resultPollInterval
        every { annotationValueEvaluator.evaluate<Boolean>(mockForceFetchAllVariables, commonContextMap) } returns true
        val resultEnabled = false
        every { annotationValueEvaluator.evaluate<Boolean>(mockEnabled, commonContextMap) } returns resultEnabled

        // when
        val coworkerValue = coworkerToCoworkerValueMapperImpl.map(coworkerAnnotation, methodInfo)

        // then
        assertThat(coworkerValue.type).isEqualTo(resultType)
        assertThat(coworkerValue.name).isEqualTo(resultName)
        assertThat(coworkerValue.timeout).isEqualTo(resultTimeout.toKotlinDuration())
        assertThat(coworkerValue.maxJobsActive).isEqualTo(resultMaxJobsActive)
        assertThat(coworkerValue.requestTimeout).isEqualTo(resultRequestTimeout.toKotlinDuration())
        assertThat(coworkerValue.pollInterval).isEqualTo(resultPollInterval.toKotlinDuration())
        assertThat(coworkerValue.fetchVariables).isEmpty()
        assertThat(coworkerValue.enabled).isEqualTo(resultEnabled)
        assertThat(coworkerValue.methodInfo).isEqualTo(methodInfo)
    }

    @Test
    fun `should map fetch variables without annotations`() {
        // given
        val mockType = "mockType"
        val mockForceFetchAllVariables = "mockForceFetchAllVariables"
        val mockName = "mockName"
        val mockTimeout = "mockTimeout"
        val mockMaxJobsActive = "mockMaxJobsActive"
        val mockRequestTimeout = "mockRequestTimeout"
        val mockPollInterval = "mockPollInterval"
        val mockFetchVariables = "mockFetchVariables"
        val mockEnabled = "mockEnabled"
        val coworker =
            mockk<Coworker>(relaxed = true) {
                every { type } returns mockType
                every { name } returns mockName
                every { timeout } returns mockTimeout
                every { maxJobsActive } returns mockMaxJobsActive
                every { requestTimeout } returns mockRequestTimeout
                every { pollInterval } returns mockPollInterval
                every { forceFetchAllVariables } returns mockForceFetchAllVariables
                every { fetchVariables } returns mockFetchVariables
                every { enabled } returns mockEnabled
            }
        val methodInfo =
            mockk<MethodInfo> {
                every { getParametersFilteredByAnnotation(or(Variable::class.java, ZeebeVariable::class.java)) } returns emptyList()
            }
        val methodInfoContextMap = mapOf("methodInfo" to methodInfo)
        val resultType = "resultType"
        every { annotationValueEvaluator.evaluate<String>(mockType, methodInfoContextMap) } returns resultType
        val commonContextMap = mapOf("type" to resultType) + methodInfoContextMap
        every { annotationValueEvaluator.evaluate<Boolean>(mockForceFetchAllVariables, commonContextMap) } returns false
        every { annotationValueEvaluator.evaluate<String>(mockName, commonContextMap) } returns ""
        every {
            annotationValueEvaluator.evaluate<Duration>(
                or(mockTimeout, or(mockRequestTimeout, mockPollInterval)),
                commonContextMap,
            )
        } returns Duration.ZERO
        every { annotationValueEvaluator.evaluate<Int>(mockMaxJobsActive, commonContextMap) } returns 0
        every { annotationValueEvaluator.evaluate<Boolean>(mockEnabled, commonContextMap) } returns true
        val resultFetchVariables = arrayOf("one", "two")
        every {
            annotationValueEvaluator.evaluate<Array<String>>(
                mockFetchVariables,
                commonContextMap,
            )
        } returns resultFetchVariables

        // when
        val coworkerValue = coworkerToCoworkerValueMapperImpl.map(coworker, methodInfo)

        // then
        assertThat(coworkerValue.fetchVariables).isEqualTo(resultFetchVariables.toList())
    }
}
