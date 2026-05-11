package com.ohmyclipping.store

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * per-category 기능 플래그 저장소.
 * 글로벌 플래그는 `FeatureFlagsService` 가 application properties 에서 읽으며,
 * 이 store 는 카테고리 단위 override 만 담당.
 *
 * 대상 테이블: `category_feature_flags` (V134 생성, V137 에서 legend/shadow 컬럼 추가)
 */
@Repository
class CategoryFeatureFlagStore(private val jdbc: JdbcTemplate) {

    /**
     * 카테고리 플래그가 true 로 저장되어 있으면 true, 그 외 false (미저장 포함).
     *
     * @param categoryId 조회 대상 카테고리 ID
     */
    fun isAccountBasedDigestEnabled(categoryId: String): Boolean {
        // category_feature_flags 에서 해당 행을 조회 — 없으면 기본값 false
        val result = jdbc.query(
            "SELECT account_based_digest_enabled FROM category_feature_flags WHERE category_id = ?",
            { rs, _ -> rs.getBoolean("account_based_digest_enabled") },
            categoryId
        )
        return result.firstOrNull() ?: false
    }

    /**
     * 플래그 값을 race-safe 하게 upsert — H2 호환을 위해 UPDATE 후 영향 row 가 0 이면 INSERT.
     * `ON CONFLICT (col) DO ...` 은 H2 MODE=PostgreSQL 에서 미지원 (AGENTS.md §1.5).
     *
     * @param categoryId 대상 카테고리 ID
     * @param enabled 설정할 플래그 값
     */
    fun setAccountBasedDigestEnabled(categoryId: String, enabled: Boolean) {
        // 기존 행 업데이트 시도 — row 가 없으면 updated = 0
        val updated = jdbc.update(
            """
            UPDATE category_feature_flags
               SET account_based_digest_enabled = ?, updated_at = CURRENT_TIMESTAMP
             WHERE category_id = ?
            """.trimIndent(),
            enabled, categoryId
        )
        // 업데이트된 row 가 없으면 신규 삽입
        if (updated == 0) {
            try {
                jdbc.update(
                    """
                    INSERT INTO category_feature_flags(category_id, account_based_digest_enabled)
                    VALUES (?, ?)
                    """.trimIndent(),
                    categoryId, enabled
                )
            } catch (_: DataIntegrityViolationException) {
                jdbc.update(
                    """
                    UPDATE category_feature_flags
                       SET account_based_digest_enabled = ?, updated_at = CURRENT_TIMESTAMP
                     WHERE category_id = ?
                    """.trimIndent(),
                    enabled, categoryId
                )
            }
        }
    }

    /**
     * Shadow mode 활성 여부를 조회. 미저장이면 false.
     */
    fun isShadowModeEnabled(categoryId: String): Boolean {
        val result = jdbc.query(
            "SELECT shadow_mode_enabled FROM category_feature_flags WHERE category_id = ?",
            { rs, _ -> rs.getBoolean("shadow_mode_enabled") },
            categoryId
        )
        return result.firstOrNull() ?: false
    }

    /**
     * Shadow mode 플래그를 race-safe 하게 upsert.
     * - UPDATE 시도 → row 가 없으면 INSERT 시도.
     * - 두 동시 호출이 INSERT 경합 시 `DataIntegrityViolationException` catch 해서 UPDATE 재시도.
     * - `shadow_enabled_at` 은 `enabled=true` 로 최초 전환할 때만 기록; 이후 OFF 해도 유지.
     */
    fun setShadowModeEnabled(categoryId: String, enabled: Boolean) {
        val updated = jdbc.update(
            """
            UPDATE category_feature_flags
               SET shadow_mode_enabled = ?,
                   shadow_enabled_at = CASE
                       WHEN ? AND shadow_enabled_at IS NULL THEN CURRENT_TIMESTAMP
                       ELSE shadow_enabled_at
                   END,
                   updated_at = CURRENT_TIMESTAMP
             WHERE category_id = ?
            """.trimIndent(),
            enabled, enabled, categoryId
        )
        if (updated == 0) {
            try {
                jdbc.update(
                    """
                    INSERT INTO category_feature_flags(
                        category_id, account_based_digest_enabled,
                        shadow_mode_enabled, shadow_enabled_at
                    )
                    VALUES (?, FALSE, ?, CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)
                    """.trimIndent(),
                    categoryId, enabled, enabled
                )
            } catch (e: DataIntegrityViolationException) {
                // 동시 INSERT race — 상대 스레드가 먼저 INSERT 완료. UPDATE 재시도로 수렴.
                jdbc.update(
                    """
                    UPDATE category_feature_flags
                       SET shadow_mode_enabled = ?,
                           shadow_enabled_at = CASE
                               WHEN ? AND shadow_enabled_at IS NULL THEN CURRENT_TIMESTAMP
                               ELSE shadow_enabled_at
                           END,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE category_id = ?
                    """.trimIndent(),
                    enabled, enabled, categoryId
                )
            }
        }
    }

    /**
     * Shadow mode 가 최초 활성된 시각. 한 번도 켠 적 없으면 null.
     * UI 에서 "Shadow 시작: N일 전" 힌트 렌더링에 사용.
     */
    fun getShadowEnabledAt(categoryId: String): Instant? {
        return jdbc.query(
            "SELECT shadow_enabled_at FROM category_feature_flags WHERE category_id = ?",
            { rs, _ -> rs.getTimestamp("shadow_enabled_at")?.toInstant() },
            categoryId
        ).firstOrNull()
    }
}
