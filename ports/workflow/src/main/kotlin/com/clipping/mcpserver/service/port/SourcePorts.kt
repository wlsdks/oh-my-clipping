package com.clipping.mcpserver.service.port

import java.net.URI

interface SourceUrlSafetyPort {
    fun validatePublicHttpUrl(rawUrl: String): URI
}

interface SourceSchedulerMetricsPort {
    fun <T> recordSourceSchedulerRun(schedulerName: String, block: () -> T): T
}

interface SourceOpsNotificationPort {
    fun sendOps(event: OpsNotificationEvent, message: String, params: Map<String, String> = emptyMap())
}

interface SourceSlaSettingsPort {
    fun currentSourceSlaSettings(): SourceSlaSettings
}

data class SourceSlaSettings(
    val enabled: Boolean,
    val sourceRequestStaleDays: Int,
)

interface SourceOrganizationPort {
    fun findSourceOrganizationsByCategoryId(categoryId: String): List<SourceOrganization>
}

data class SourceOrganization(
    val name: String,
    val aliases: List<String> = emptyList(),
)
