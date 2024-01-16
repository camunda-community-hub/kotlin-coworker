package org.camunda.community.extension.coworker.credentials

import io.camunda.zeebe.client.CredentialsProvider
import io.grpc.CallCredentials
import io.grpc.Metadata
import io.grpc.SecurityLevel
import io.grpc.Status
import mu.KLogging
import java.io.IOException
import java.util.concurrent.Executor

class ZeebeCoworkerClientCredentials(
    private val credentialsProvider: CredentialsProvider,
) : CallCredentials() {
    override fun applyRequestMetadata(
        requestInfo: RequestInfo,
        appExecutor: Executor,
        applier: MetadataApplier,
    ) {
        if (requestInfo.securityLevel.ordinal < SecurityLevel.PRIVACY_AND_INTEGRITY.ordinal) {
            logger.warn {
                "The request's security level does not guarantee that the credentials will be confidential."
            }
        }

        val headers = Metadata()
        appExecutor.execute {
            try {
                credentialsProvider.applyCredentials(headers)
                applier.apply(headers)
            } catch (e: IOException) {
                applier.fail(Status.CANCELLED.withCause(e))
            }
        }
    }

    override fun thisUsesUnstableApi() = Unit

    companion object : KLogging()
}
