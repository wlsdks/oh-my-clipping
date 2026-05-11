package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.RuntimeSetting
import com.clipping.mcpserver.store.RuntimeSettingStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock

private val log = KotlinLogging.logger {}

/**
 * 리뷰 정책 룰 엔진이 기대하는 `RuntimeSetting` 기본값을 서버 기동 시 한 번 seed 한다.
 *
 * 현재 seed 대상:
 *  - [ZERO_SIGNAL_KEY] — zero-signal 룰 on/off. 기본값 `"true"` (룰 활성).
 *
 * 멱등성:
 *  - 이미 키가 존재하면 절대 덮어쓰지 않는다. 운영 중에 값을 `false` 로 바꿔도 재기동에서
 *    복구되지 않는다 (관리자가 의도적으로 끈 상태를 존중한다).
 *
 * 실행 시점:
 *  - [ApplicationReadyEvent] — Flyway 마이그레이션과 AdminUsers bootstrap 이후.
 *  - 실패해도 서버 시작은 막지 않는다 (룰 엔진은 setting 이 없으면 `false` 로 해석).
 */
@Component
class RuleEngineSettingsBootstrap(
    private val settingStore: RuntimeSettingStore,
    private val clock: Clock,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun seedDefaultsOnStartup() {
        // zero-signal 룰 기본값 seed (멱등)
        if (settingStore.findByKey(ZERO_SIGNAL_KEY) == null) {
            runCatching {
                settingStore.save(
                    RuntimeSetting(
                        key = ZERO_SIGNAL_KEY,
                        value = "true",
                        updatedAt = clock.instant(),
                    )
                )
                log.info { "Seeded default runtime setting: $ZERO_SIGNAL_KEY=true" }
            }.onFailure { ex ->
                log.warn(ex) { "Failed to seed $ZERO_SIGNAL_KEY — rule engine will treat it as disabled" }
            }
        }
    }

    companion object {
        /** zero-signal 자동 EXCLUDE 룰 on/off 플래그. 값은 `"true"`/`"false"`. */
        const val ZERO_SIGNAL_KEY = "policy.rule.zero_signal_exclude.enabled"
    }
}
