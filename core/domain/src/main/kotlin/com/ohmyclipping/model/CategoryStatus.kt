package com.ohmyclipping.model

enum class CategoryStatus {
    ACTIVE, PAUSED;
    val isOperational: Boolean get() = this == ACTIVE
    val occupiesChannel: Boolean get() = this == ACTIVE || this == PAUSED
}
