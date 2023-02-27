package org.camunda.community.extension.coworker.spring.annotation.scope

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ImmutableMapScopeTest {
    @Test
    fun `should return contextual object from map`() {
        // given
        val key = "test"
        val value = "testy"
        // when
        // then
        assertThat(ImmutableMapScope(mapOf(key to value)).resolveContextualObject(key)).isEqualTo(value)
    }
}
