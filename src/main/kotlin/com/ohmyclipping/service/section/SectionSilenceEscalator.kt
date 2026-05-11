package com.ohmyclipping.service.section

import com.ohmyclipping.service.digest.EscalationCopy
import com.ohmyclipping.service.digest.sectionSilenceCopy
import com.ohmyclipping.store.CategorySectionSilenceLogStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 섹션별 연속 empty 일수를 관리하며, 3일 이상이면 escalation CTA 를 포함한 copy 를 반환한다.
 * digest 생성 파이프라인에서 섹션이 비었을 때 `onEmptySection`, 매치가 있을 때 `onSectionMatch` 를 호출한다.
 */
@Service
class SectionSilenceEscalator(
    private val store: CategorySectionSilenceLogStore,
    @Value("\${clipping.app.base-url:http://localhost:8086}")
    private val appBaseUrl: String
) {
    /**
     * 섹션이 빈 경우 호출한다.
     * 연속 empty 일수를 증가시키고, 3일 이상이면 구독 수정 딥링크를 포함한 escalation copy 를 반환한다.
     */
    fun onEmptySection(categoryId: String, sectionKey: String, sectionLabel: String): EscalationCopy {
        val days = store.incrementAndGet(categoryId, sectionKey)
        return sectionSilenceCopy(
            emptyDays = days,
            sectionLabel = sectionLabel,
            actionUrl = "$appBaseUrl/user/subscriptions/$categoryId/edit",
        )
    }

    /**
     * 섹션에 매치 기사가 등장했을 때 호출한다. 연속 empty 일수를 0으로 초기화한다.
     */
    fun onSectionMatch(categoryId: String, sectionKey: String) {
        store.reset(categoryId, sectionKey)
    }
}
