package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * AI 비용 예산 설정 엔티티.
 * cost_budget_settings 테이블에 매핑된다.
 */
@Entity
@Table(name = "cost_budget_settings")
class BudgetSettingEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "monthly_budget_usd")
    var monthlyBudgetUsd: Double? = null,

    @Column(name = "alert_threshold_percent", nullable = false)
    var alertThresholdPercent: Int = 80,

    @Column(name = "slack_alert_enabled", nullable = false)
    var slackAlertEnabled: Boolean = true,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
