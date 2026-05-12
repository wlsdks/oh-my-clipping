package com.ohmyclipping.service.digest

private const val ESCALATION_THRESHOLD_DAYS = 3

/** 섹션이 비었을 때 노출할 카피와 액션. actionUrl 은 3일 이상일 때만 채워진다. */
data class EscalationCopy(
    val text: String,
    val actionLabel: String? = null,
    val actionUrl: String? = null,
)

fun sectionSilenceCopy(
    emptyDays: Int,
    sectionLabel: String,
    actionUrl: String,
): EscalationCopy {
    if (emptyDays < 0) {
        throw EngineInvalidInputException("emptyDays must be non-negative")
    }
    val normalizedLabel = sectionLabel.trim()
    if (normalizedLabel.isBlank()) {
        throw EngineInvalidInputException("sectionLabel must not be blank")
    }

    return if (emptyDays >= ESCALATION_THRESHOLD_DAYS) {
        EscalationCopy(
            text = "$normalizedLabel 뉴스가 ${emptyDays}일째 없어요.",
            actionLabel = "주제 수정하기",
            actionUrl = actionUrl,
        )
    } else {
        EscalationCopy(text = "오늘 $normalizedLabel 관련 뉴스는 없었어요")
    }
}
