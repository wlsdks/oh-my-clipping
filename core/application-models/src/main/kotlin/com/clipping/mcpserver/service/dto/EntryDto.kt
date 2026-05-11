package com.clipping.mcpserver.service.dto

import jakarta.validation.constraints.Size

/** 위자드 entry 한 건. type 은 "keyword" 또는 "company". */
data class EntryDto(
    @field:Size(min = 1, max = 200, message = "항목 값은 1~200자여야 합니다")
    val value: String,
    val type: String,
    @field:Size(max = 20, message = "종목코드는 20자 이내여야 합니다")
    val stockCode: String? = null
)
