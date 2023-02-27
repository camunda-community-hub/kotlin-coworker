package org.camunda.community.extension.coworker.spring.annotation.scope

import mu.KLogging
import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.config.Scope

data class ImmutableMapScope(
    private val map: Map<String, Any>
): Scope {
    override fun get(name: String, objectFactory: ObjectFactory<*>): Any {
        return map[name] ?: objectFactory.`object`
    }

    override fun remove(name: String): Any? {
        throw UnsupportedOperationException("Right now there is no need to implement this method")
    }

    override fun registerDestructionCallback(name: String, callback: Runnable) {
        logger.warn { "MethodInfoScope does not support destruction callbacks." }
    }

    override fun resolveContextualObject(key: String): Any? {
        return map[key]
    }

    override fun getConversationId(): String {
        return map.toList().joinToString(separator = ", ") { "${it.first}:${it.second}" }
    }

    companion object: KLogging()
}
