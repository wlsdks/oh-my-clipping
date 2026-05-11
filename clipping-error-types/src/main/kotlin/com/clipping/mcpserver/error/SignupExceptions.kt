package com.clipping.mcpserver.error

import com.clipping.mcpserver.error.ErrorCode

class SignupException(
    message: String,
    errorCode: ErrorCode
) : InvalidInputException(message, errorCode)
