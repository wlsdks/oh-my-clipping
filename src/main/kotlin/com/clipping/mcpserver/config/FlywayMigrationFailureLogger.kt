package com.clipping.mcpserver.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.api.callback.Callback
import org.flywaydb.core.api.callback.Context
import org.flywaydb.core.api.callback.Event
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * Flyway 마이그레이션 실패를 애플리케이션 로그로 올려 운영자가 `/tmp/clipping-server.log` 에서
 * 즉시 원인을 볼 수 있게 한다. Spring Boot 의 FlywayAutoConfiguration 이 Callback 타입의 빈을
 * 자동으로 Flyway 에 등록한다.
 *
 * 성공 경로는 INFO 로 가볍게만 남기고, AFTER_MIGRATE_ERROR / AFTER_EACH_MIGRATE_ERROR 에서
 * ERROR 레벨로 version + description + stacktrace 를 찍어 롤백 판단을 빠르게 한다.
 */
@Component
class FlywayMigrationFailureLogger : Callback {

    override fun supports(event: Event, context: Context?): Boolean {
        return event == Event.AFTER_MIGRATE_ERROR ||
            event == Event.AFTER_EACH_MIGRATE_ERROR ||
            event == Event.AFTER_EACH_MIGRATE
    }

    override fun canHandleInTransaction(event: Event, context: Context?): Boolean = true

    override fun handle(event: Event, context: Context?) {
        val info = context?.migrationInfo
        val version = info?.version?.version ?: "?"
        val description = info?.description ?: "(unknown)"
        val script = info?.script ?: "(unknown)"

        when (event) {
            Event.AFTER_MIGRATE_ERROR -> {
                log.error {
                    "Flyway migration batch FAILED — last version V$version ($description) script=$script"
                }
            }
            Event.AFTER_EACH_MIGRATE_ERROR -> {
                log.error {
                    "Flyway migration step FAILED — V$version ($description) script=$script — " +
                        "app 기동이 중단됩니다. DB 상태를 확인하고 필요 시 flyway repair 를 검토하세요."
                }
            }
            Event.AFTER_EACH_MIGRATE -> {
                // 성공 스텝은 디버그 수준으로만
                log.debug { "Flyway migration applied: V$version ($description)" }
            }
            else -> Unit
        }
    }

    override fun getCallbackName(): String = "FlywayMigrationFailureLogger"
}
