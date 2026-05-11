package com.ohmyclipping.service.source

import java.net.URI

interface SourceVerificationClient {
    fun verify(sourceUri: URI): VerificationResult
}
