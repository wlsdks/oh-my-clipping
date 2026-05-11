package com.ohmyclipping.config

import com.ohmyclipping.service.LocalDevSupportService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate

private val log = KotlinLogging.logger {}

/**
 * 로컬 개발용 seed를 애플리케이션 시작 시 적용하는 설정입니다.
 *
 * ⚠️ 파괴적 동작: `DEV_BOOTSTRAP=true`는 `local-bootstrap.sql`을 실행하며,
 * 이 SQL은 `clipping_user_requests`, `user_delivery_schedules`, `summary_feedback` 등
 * **유저 데이터 테이블을 DELETE한다**. 실 사용자 row가 남아있는 DB에서 true로 기동하면
 * 데이터가 소실된다. 아래 pre-flight guard가 이를 hard-abort로 차단한다.
 */
@Configuration
@Profile("local")
class LocalDevBootstrapConfig {

    @Value("\${DEV_BOOTSTRAP:true}")
    private var devBootstrapEnabled: Boolean = true

    @Value("\${spring.profiles.active:}")
    private var activeProfiles: String = ""

    /**
     * 로컬 개발에서만 mock 데이터와 계정을 자동 부트스트랩합니다.
     * - `DEV_BOOTSTRAP=false`이면 기존 데이터를 유지한다.
     * - 실 사용자 row(seed ID 패턴이 아닌 `admin_users`)가 존재하면 abort한다 — 재발방지 가드.
     * - Spring `test` profile 활성 시 guard 스킵(ApplicationContext 재사용으로 seed가 누적됨).
     */
    @Bean
    fun localDevBootstrapRunner(
        localDevSupportService: LocalDevSupportService,
        jdbc: JdbcTemplate
    ): ApplicationRunner =
        ApplicationRunner {
            if (!devBootstrapEnabled) {
                log.info { "Local dev bootstrap SKIPPED (DEV_BOOTSTRAP=false)" }
                return@ApplicationRunner
            }

            // 통합 테스트는 공유 H2 context 에 이전 실행의 non-seed row가 누적된다. 가드는 local profile 단독에서만 적용.
            val inTestProfile = activeProfiles.split(",").map { it.trim() }.contains("test")
            if (inTestProfile) {
                log.info { "Local dev bootstrap guard SKIPPED under test profile." }
                localDevSupportService.bootstrap()
                return@ApplicationRunner
            }

            // Guard: 실 사용자 데이터 있는 DB에서 bootstrap을 돌리면 DELETE로 모두 소실된다.
            val realUserCount = countNonSeedAdminUsers(jdbc)
            if (realUserCount > 0) {
                val message = """
                    |⛔ DEV_BOOTSTRAP ABORTED — real user data detected.
                    |
                    |Found $realUserCount admin_users row(s) whose ID does NOT match the seed pattern
                    |'00000000-0000-0000-0000-%'. Running bootstrap would DELETE user data in:
                    |  - clipping_user_requests (subscriptions)
                    |  - user_delivery_schedules (delivery times)
                    |  - summary_feedback, clipping_review_items, rss_items, etc.
                    |
                    |If this is a shared DB (e.g. docker-compose postgres used with prod-like data),
                    |set DEV_BOOTSTRAP=false and restart. DO NOT force bootstrap.
                    |
                    |If you TRULY intend to wipe this DB, manually TRUNCATE/DELETE the real users
                    |first, then restart. The guard only trips on row-level presence, not on flags.
                """.trimMargin()
                log.error { message }
                throw IllegalStateException(
                    "DEV_BOOTSTRAP aborted: $realUserCount real user(s) present. " +
                        "See log for recovery instructions."
                )
            }

            localDevSupportService.bootstrap()
        }

    /** admin_users 테이블에서 seed ID 패턴이 아닌 row의 수를 집계한다. */
    private fun countNonSeedAdminUsers(jdbc: JdbcTemplate): Int {
        return try {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_users WHERE id NOT LIKE '00000000-0000-0000-0000-%'",
                Int::class.java
            ) ?: 0
        } catch (e: BadSqlGrammarException) {
            // admin_users 테이블이 아직 없는 상황(첫 Flyway migration 이전)은 정상 — bootstrap 진행.
            log.info { "admin_users table not yet present — skipping pre-flight guard check." }
            0
        }
    }
}
