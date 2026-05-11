package com.ohmyclipping.store

import com.ohmyclipping.model.BudgetSetting

/**
 * 월 예산 설정을 관리하는 저장소.
 * 단일 행(id='default')으로 운영한다.
 */
interface BudgetSettingStore {
    /** 현재 예산 설정을 조회한다. 항상 non-null (default row 보장). */
    fun get(): BudgetSetting

    /** 예산 설정을 저장(upsert)한다. */
    fun save(setting: BudgetSetting): BudgetSetting
}
