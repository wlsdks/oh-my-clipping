package com.clipping.mcpserver.service.digest

class EngineInvalidInputException(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
