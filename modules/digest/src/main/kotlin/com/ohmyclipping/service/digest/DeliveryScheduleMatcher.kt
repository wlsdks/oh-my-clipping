package com.ohmyclipping.service.digest

import com.ohmyclipping.model.CategoryRule
import org.springframework.scheduling.support.CronExpression
import java.time.ZonedDateTime

/**
 * Slack 자동 다이제스트 워커가 "지금 이 카테고리는 발송 대상인가" 를 판단하는 순수 정책.
 *
 * [SlackDigestWorker] 는 store/시각 조회 같은 I/O 만 담당하고, 비교 로직은 본 객체에 위임한다.
 * 모든 메서드가 순수 함수다 — 내부에서 `Instant.now()` / DB 조회를 하지 않으므로 H2 없이 표 기반
 * 테스트로 모든 분기를 검증할 수 있다.
 *
 * 발송 우선순위:
 * 1. **카테고리 preset 스케줄** — `CategoryRule.deliveryPreset` 이 설정돼 있으면 `deliveryDays` /
 *    `deliveryHour` 와 현재 요일/시각을 비교한다. 일치하면 발송 대상.
 * 2. **개인 스케줄 사전 계산 결과** — 호출자가 미리 계산한 `dueCategoryIds` 에 카테고리가 포함되면
 *    발송 대상. (개인 일정 매칭 → 승인된 구독 카테고리 집합으로 묶기까지의 흐름은 워커가 담당)
 * 3. **글로벌 cron 폴백** — 위 1, 2 가 결정되지 않은 카테고리는 [matchesGlobalCron] 결과에 따른다.
 */
object DeliveryScheduleMatcher {

    /**
     * 발송 우선순위를 따라 카테고리의 발송 대상 여부를 결정한다.
     */
    fun isDeliveryDue(
        categoryId: String,
        categoryRule: CategoryRule?,
        dayOfWeek: String,
        currentHour: Int,
        dueCategoryIds: Set<String>,
        globalCronDue: Boolean,
    ): Boolean {
        // 1. 카테고리별 preset 스케줄이 있으면 그것만 사용한다 (개인 스케줄/글로벌 cron 무시).
        if (categoryRule?.deliveryPreset != null) {
            return matchesCategoryRule(categoryRule, dayOfWeek, currentHour)
        }
        // 2. 개인 스케줄 매칭으로 사전 계산된 카테고리 집합 — 워커가 N+1 회피용으로 미리 채워준다.
        if (dueCategoryIds.contains(categoryId)) {
            return true
        }
        // 3. 글로벌 cron 폴백.
        return globalCronDue
    }

    /**
     * 카테고리별 개별 스케줄(`deliveryDays` + `deliveryHour`) 과 현재 시각이 일치하는지 검사한다.
     * `deliveryPreset != null` 인 경우에만 호출되도록 [isDeliveryDue] 가 가드한다.
     */
    fun matchesCategoryRule(
        rule: CategoryRule,
        dayOfWeek: String,
        currentHour: Int,
    ): Boolean {
        val ruleDays = rule.deliveryDays ?: return false
        val ruleHour = rule.deliveryHour ?: return false
        return dayOfWeek in ruleDays && currentHour == ruleHour
    }

    /**
     * Spring [CronExpression] 이 현재 시각(시 단위) 과 매칭되는지 확인한다.
     *
     * [now] 를 파라미터로 받아 순수 함수로 유지한다. `Invalid cron` 은 false 를 반환하며 호출자가
     * 별도로 로깅한다 (matcher 는 로깅 책임을 지지 않는다).
     */
    fun matchesGlobalCron(cronExpression: String, now: ZonedDateTime): Boolean {
        val trimmed = cronExpression.trim()
        if (trimmed.isBlank() || trimmed == "-") return false

        val expr = try {
            CronExpression.parse(trimmed)
        } catch (_: IllegalArgumentException) {
            return false
        }

        // 현재 시(hour) 의 시작에서 1 초 뒤로 가서 cron.next() 결과가 현재 시(hour) 안에 있는지 본다.
        val startOfHour = now.withMinute(0).withSecond(0).withNano(0)
        val nextRun = expr.next(startOfHour.minusSeconds(1)) ?: return false
        return nextRun.hour == now.hour && nextRun.toLocalDate() == now.toLocalDate()
    }

    /**
     * [matchesGlobalCron] 의 파싱 가능 여부만 별도로 노출. 워커는 false 가 잘못된 cron 때문인지를
     * 구분해 한 번만 로깅하기 위해 사용한다.
     */
    fun isValidCronExpression(cronExpression: String): Boolean {
        val trimmed = cronExpression.trim()
        if (trimmed.isBlank() || trimmed == "-") return false
        return runCatching { CronExpression.parse(trimmed) }.isSuccess
    }
}
