package com.ohmyclipping.service.pipeline

import com.ohmyclipping.error.ensureValid

/**
 * Per-run Ralph loop override guard.
 *
 * Runtime settings already validate the stored value; this policy keeps ad-hoc
 * API/MCP overrides on the same contract before a costly clipping run starts.
 */
object RalphLoopOverridePolicy {
    private val allowedIterations = 1..30

    fun validateMaxIterations(maxIterations: Int?) {
        if (maxIterations == null) return

        ensureValid(maxIterations in allowedIterations) {
            "ralphLoopMaxIterations must be between ${allowedIterations.first} and ${allowedIterations.last}"
        }
    }
}
