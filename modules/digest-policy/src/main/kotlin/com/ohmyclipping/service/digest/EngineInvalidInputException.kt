package com.ohmyclipping.service.digest

class EngineInvalidInputException(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
