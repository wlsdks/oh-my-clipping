package com.ohmyclipping.store

import com.ohmyclipping.model.BudgetSetting
import com.ohmyclipping.repository.BudgetSettingRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * 예산 설정 JPA 구현. JdbcBudgetSettingStore를 대체한다.
 */
@Repository
@Primary
class JpaBudgetSettingStore(
    private val repository: BudgetSettingRepository
) : BudgetSettingStore {

    override fun get(): BudgetSetting =
        repository.findById("default").orElseThrow().toModel()

    override fun save(setting: BudgetSetting): BudgetSetting {
        val entity = repository.findById("default").orElseThrow()
        entity.monthlyBudgetUsd = setting.monthlyBudgetUsd
        entity.alertThresholdPercent = setting.alertThresholdPercent
        entity.slackAlertEnabled = setting.slackAlertEnabled
        entity.updatedAt = Instant.now()
        return repository.save(entity).toModel()
    }

    private fun com.ohmyclipping.entity.BudgetSettingEntity.toModel() = BudgetSetting(
        id = id,
        monthlyBudgetUsd = monthlyBudgetUsd ?: 0.0,
        alertThresholdPercent = alertThresholdPercent,
        slackAlertEnabled = slackAlertEnabled,
        updatedAt = updatedAt
    )
}
