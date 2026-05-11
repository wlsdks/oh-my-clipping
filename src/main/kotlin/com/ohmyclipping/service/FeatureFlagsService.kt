package com.ohmyclipping.service

import com.ohmyclipping.store.CategoryFeatureFlagStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 글로벌 + 카테고리 단위 기능 플래그 해석.
 * 글로벌이 true 면 카테고리 값과 무관하게 true (전체 on).
 * 글로벌이 false 면 카테고리 값을 사용 (per-category canary 운영).
 */
@Service
class FeatureFlagsService(
    private val categoryFlagStore: CategoryFeatureFlagStore,
    @Value("\${clipping.feature.account-based-digest.enabled:false}")
    private val globalAccountBasedDigestEnabled: Boolean
) {
    fun isAccountBasedDigestEnabled(categoryId: String?): Boolean {
        if (globalAccountBasedDigestEnabled) return true
        if (categoryId == null) return false
        return categoryFlagStore.isAccountBasedDigestEnabled(categoryId)
    }

    /**
     * Shadow mode 는 글로벌 플래그가 없음 — 카테고리 단위 토글만 존재.
     * null 카테고리면 항상 false (전역 digest 흐름에서는 shadow 미적용).
     */
    fun isShadowModeEnabled(categoryId: String?): Boolean {
        if (categoryId == null) return false
        return categoryFlagStore.isShadowModeEnabled(categoryId)
    }
}
