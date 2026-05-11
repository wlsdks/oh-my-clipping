package com.clipping.mcpserver.model

import java.time.Instant

enum class AsyncJobType {
    COLLECT,
    SUMMARIZE
}

enum class AsyncJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
}

data class AsyncJob(
    val id: String,
    val jobType: AsyncJobType,
    val payloadJson: String,
    val status: AsyncJobStatus,
    val attempts: Int,
    val maxAttempts: Int,
    val nextRunAt: Instant,
    val lastError: String? = null,
    val resultJson: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class AsyncJobQueuedResult(
    val jobId: String,
    val jobType: String,
    val status: String
)

data class AsyncJobStatusResult(
    val id: String,
    val jobType: String,
    val status: String,
    val attempts: Int,
    val maxAttempts: Int,
    val nextRunAt: String,
    val lastError: String?,
    val resultJson: String?,
    val createdAt: String,
    val updatedAt: String
)
