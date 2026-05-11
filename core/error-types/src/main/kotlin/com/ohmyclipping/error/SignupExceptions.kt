package com.ohmyclipping.error

import com.ohmyclipping.error.ErrorCode

class SignupException(
    message: String,
    errorCode: ErrorCode
) : InvalidInputException(message, errorCode)
