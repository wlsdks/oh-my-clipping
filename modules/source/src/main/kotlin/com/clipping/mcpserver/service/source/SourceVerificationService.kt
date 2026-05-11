package com.clipping.mcpserver.service.source

import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.service.port.SourceUrlSafetyPort
import com.clipping.mcpserver.store.RssSourceStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.io.IOException

private val log = KotlinLogging.logger {}

enum class VerificationResult {
    VERIFIED, FEED_ERROR, ROBOTS_BLOCKED, TIMEOUT, BLOCKED_URL
}

@Service
class SourceVerificationService(
    private val sourceStore: RssSourceStore,
    private val urlSafetyValidator: SourceUrlSafetyPort,
    private val sourceVerificationClient: SourceVerificationClient
) {

    fun verify(sourceId: String): VerificationResult {
        val source = sourceStore.findById(sourceId)
            ?: throw NotFoundException("Source not found: $sourceId")

        val result = try {
            val sourceUri = urlSafetyValidator.validatePublicHttpUrl(source.url)
            sourceVerificationClient.verify(sourceUri)
        } catch (e: IllegalArgumentException) {
            log.warn(e) { "Blocked source URL for ${source.name}: ${e.message}" }
            VerificationResult.BLOCKED_URL
        } catch (e: java.net.SocketTimeoutException) {
            log.warn(e) { "Timeout verifying source ${source.name}: ${e.message}" }
            VerificationResult.TIMEOUT
        } catch (e: IOException) {
            log.warn(e) { "Error verifying source ${source.name}: ${e.message}" }
            VerificationResult.FEED_ERROR
        } catch (e: SecurityException) {
            log.warn(e) { "Error verifying source ${source.name}: ${e.message}" }
            VerificationResult.FEED_ERROR
        }

        sourceStore.updateVerificationStatus(sourceId, result.name)
        return result
    }
}
