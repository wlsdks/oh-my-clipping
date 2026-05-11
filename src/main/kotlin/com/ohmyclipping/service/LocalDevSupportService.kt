package com.ohmyclipping.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import javax.sql.DataSource
import java.time.Instant
import java.util.Locale

private val localDevLog = KotlinLogging.logger {}

private const val LOCAL_DEV_PASSWORD = "LocalPass123!"
private const val LOCAL_DEV_NOTE = "개발용 임시 버튼, 추후 제거 예정"
private val localDevPlaceholderChannelIds = listOf("C1234567890", "C0123456789")

private val localDevAdminSeed = LocalDevAccountSeed(
    id = "00000000-0000-0000-0000-000000000001",
    username = "dev.admin@clipping.local",
    role = "ADMIN",
    displayName = "로컬 관리자",
    department = "운영",
    approvalStatus = "APPROVED",
    approvalNote = "로컬 개발용 고정 계정",
    approvedByUserId = null
)

private val localDevUserApprovedSeed = LocalDevAccountSeed(
    id = "00000000-0000-0000-0000-000000000002",
    username = "dev.user@clipping.local",
    role = "USER",
    displayName = "승인된 사용자",
    department = "플랫폼 전략팀",
    approvalStatus = "APPROVED",
    approvalNote = "개발용 로그인 shortcut 계정",
    approvedByUserId = localDevAdminSeed.id
)

private val localDevUserPendingSeed = LocalDevAccountSeed(
    id = "00000000-0000-0000-0000-000000000003",
    username = "dev.user.pending@clipping.local",
    role = "USER",
    displayName = "승인 대기 사용자",
    department = "디지털 운영팀",
    approvalStatus = "PENDING",
    approvalNote = "현업 승인 대기",
    approvedByUserId = null
)

private val localDevUserRejectedSeed = LocalDevAccountSeed(
    id = "00000000-0000-0000-0000-000000000004",
    username = "dev.user.rejected@clipping.local",
    role = "USER",
    displayName = "반려된 사용자",
    department = "신사업 TF",
    approvalStatus = "REJECTED",
    approvalNote = "부서 정보 재확인 후 다시 신청 필요",
    approvedByUserId = localDevAdminSeed.id
)

private val localDevUserFreshSeed = LocalDevAccountSeed(
    id = "00000000-0000-0000-0000-000000000005",
    username = "dev.user.fresh@clipping.local",
    role = "USER",
    displayName = "신규 가입자",
    department = "경영지원팀",
    approvalStatus = "APPROVED",
    approvalNote = "개발용 신규 가입 시나리오 계정",
    approvedByUserId = localDevAdminSeed.id
)

// -006, -007, -008 은 local-bootstrap.sql 의 user_events seed 가 참조하는 분석용 유저.
// 로그인 시나리오용이 아니라 대시보드/분석 화면에 풍성한 활동 데이터를 채우기 위한 고정 id.
// 이 계정들이 없으면 user_events 시드가 admin_users FK 를 위반한다.
private val localDevUserAnalystSeed = LocalDevAccountSeed(
    id = "00000000-0000-0000-0000-000000000006",
    username = "dev.user.analyst@clipping.local",
    role = "USER",
    displayName = "분석 담당자",
    department = "데이터팀",
    approvalStatus = "APPROVED",
    approvalNote = "대시보드 시드용 분석 활동 계정",
    approvedByUserId = localDevAdminSeed.id
)

private val localDevUserFinanceSeed = LocalDevAccountSeed(
    id = "00000000-0000-0000-0000-000000000007",
    username = "dev.user.finance@clipping.local",
    role = "USER",
    displayName = "재무 담당자",
    department = "재무팀",
    approvalStatus = "APPROVED",
    approvalNote = "대시보드 시드용 재무 활동 계정",
    approvedByUserId = localDevAdminSeed.id
)

private val localDevUserOpsSeed = LocalDevAccountSeed(
    id = "00000000-0000-0000-0000-000000000008",
    username = "dev.user.ops@clipping.local",
    role = "USER",
    displayName = "운영 담당자",
    department = "운영팀",
    approvalStatus = "APPROVED",
    approvalNote = "대시보드 시드용 운영 활동 계정",
    approvedByUserId = localDevAdminSeed.id
)

private val localDevAccountSeeds = listOf(
    localDevAdminSeed,
    localDevUserApprovedSeed,
    localDevUserPendingSeed,
    localDevUserRejectedSeed,
    localDevUserFreshSeed,
    localDevUserAnalystSeed,
    localDevUserFinanceSeed,
    localDevUserOpsSeed
)

private val localDevLoginShortcuts = listOf(
    LocalDevLoginShortcut(
        key = "admin",
        label = "관리자 로그인",
        scope = "admin",
        username = localDevAdminSeed.username,
        password = LOCAL_DEV_PASSWORD,
        note = LOCAL_DEV_NOTE
    ),
    LocalDevLoginShortcut(
        key = "user",
        label = "회원 로그인",
        scope = "user",
        username = localDevUserApprovedSeed.username,
        password = LOCAL_DEV_PASSWORD,
        note = LOCAL_DEV_NOTE
    ),
    LocalDevLoginShortcut(
        key = "fresh",
        label = "신규 가입자 로그인",
        scope = "new-user",
        username = localDevUserFreshSeed.username,
        password = LOCAL_DEV_PASSWORD,
        note = LOCAL_DEV_NOTE
    )
)

/**
 * 로컬 개발용 계정 seed 정의입니다.
 */
data class LocalDevAccountSeed(
    val id: String,
    val username: String,
    val role: String,
    val displayName: String,
    val department: String?,
    val approvalStatus: String,
    val approvalNote: String,
    val approvedByUserId: String?
)

/**
 * 로그인 화면에서 사용하는 로컬 개발용 shortcut 모델입니다.
 */
data class LocalDevLoginShortcut(
    val key: String,
    val label: String,
    val scope: String,
    val username: String,
    val password: String,
    val note: String
)

/**
 * 로컬 환경에서만 사용하는 계정/SQL mock 데이터를 관리합니다.
 */
@Service
@Profile("local")
class LocalDevSupportService(
    private val jdbc: JdbcTemplate,
    private val passwordEncoder: PasswordEncoder,
    private val dataSource: DataSource,
    private val runtimeSettingService: RuntimeSettingService
) {

    /**
     * 로컬 개발용 고정 계정과 SQL mock 데이터를 현재 DB에 반영합니다.
     */
    fun bootstrap() {
        // login shortcut이 참조하는 계정을 먼저 고정 상태로 맞춘다.
        seedAccounts()
        // 운영 화면을 채우는 나머지 mock 데이터는 SQL로 일괄 반영한다.
        applySqlSeed()
        // DB 종류에 맞춰 audit_log PK 증가 지점을 현재 seed 이후로 맞춘다.
        alignAuditLogSequence()
        // 실제 Slack 연결 검증이 가능한 기본 채널이 있으면 placeholder를 현재 값으로 맞춘다.
        alignSeedSlackTargets()
        localDevLog.info { "Local dev bootstrap applied" }
    }

    /**
     * 로그인 화면에서 노출할 로컬 개발용 shortcut 목록을 반환합니다.
     */
    fun loginShortcuts(): List<LocalDevLoginShortcut> = localDevLoginShortcuts

    /**
     * 고정 dev 계정의 비밀번호와 상태를 매번 동일하게 맞춥니다.
     */
    private fun seedAccounts() {
        val encodedPassword = passwordEncoder.encode(LOCAL_DEV_PASSWORD)
        // 승인자 참조가 필요한 사용자 계정 전에 관리자 계정을 먼저 맞춘다.
        localDevAccountSeeds.forEach { seed ->
            upsertAccount(seed, encodedPassword)
        }
    }

    /**
     * seed SQL을 현재 데이터소스에 적용합니다.
     */
    private fun applySqlSeed() {
        // classpath SQL을 그대로 실행해 로컬 점검용 화면 데이터를 채운다.
        ResourceDatabasePopulator(ClassPathResource("db/dev-seed/local-bootstrap.sql"))
            .apply {
                setContinueOnError(false)
                setIgnoreFailedDrops(true)
            }
            .execute(dataSource)
    }

    /**
     * seed SQL 이후 audit_log의 다음 PK가 충돌하지 않도록 DB별 시퀀스를 보정한다.
     */
    private fun alignAuditLogSequence() {
        val nextAuditId = (jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM audit_log", Long::class.java) ?: 1L)
            .coerceAtLeast(9016L)
        val databaseProduct = dataSource.connection.use { connection ->
            connection.metaData.databaseProductName.lowercase(Locale.ROOT)
        }

        // H2와 PostgreSQL이 사용하는 시퀀스 제어 문법이 달라 DB별 분기를 둔다.
        when {
            databaseProduct.contains("h2") -> {
                jdbc.execute("ALTER TABLE audit_log ALTER COLUMN id RESTART WITH $nextAuditId")
            }
            databaseProduct.contains("postgresql") -> {
                jdbc.execute("SELECT setval('audit_log_id_seq', $nextAuditId, false)")
            }
        }
    }

    /**
     * 로컬 seed에 남아 있는 placeholder Slack 채널을 현재 런타임 기본 채널로 치환합니다.
     */
    private fun alignSeedSlackTargets() {
        // 운영 로그 채널이 비어 있으면 placeholder를 유지하고 Slack 검증 테스트만 건너뛴다.
        val opsLogChannelId = runtimeSettingService.current().opsLogChannelId.trim()
        if (opsLogChannelId.isBlank()) return

        // 카테고리/요청 양쪽의 placeholder 채널을 실제 로컬 운영 로그 채널로 맞춘다.
        jdbc.update(
            """
            UPDATE batch_categories
            SET slack_channel_id = ?, updated_at = CURRENT_TIMESTAMP
            WHERE slack_channel_id IN (?, ?)
            """.trimIndent(),
            opsLogChannelId,
            localDevPlaceholderChannelIds[0],
            localDevPlaceholderChannelIds[1]
        )
        jdbc.update(
            """
            UPDATE clipping_user_requests
            SET slack_channel_id = ?, updated_at = CURRENT_TIMESTAMP
            WHERE slack_channel_id IN (?, ?)
            """.trimIndent(),
            opsLogChannelId,
            localDevPlaceholderChannelIds[0],
            localDevPlaceholderChannelIds[1]
        )
    }

    /**
     * 단일 dev 계정을 삭제/재삽입 없이 동일 ID로 갱신하거나 새로 생성합니다.
     *
     * username은 service 내부 고정값만 허용하며 외부 입력으로 사용하지 않습니다.
     */
    private fun upsertAccount(seed: LocalDevAccountSeed, encodedPassword: String) {
        // 동일 사용자명으로 남아 있을 수 있는 이전 로컬 계정 충돌을 먼저 정리한다.
        cleanupConflictingDevUsernames(seed)
        // 고정 ID 계정이 있으면 상태를 덮어쓰고, 없으면 새로 생성한다.
        val updated = jdbc.update(
            """
            UPDATE admin_users
            SET username = ?,
                password_hash = ?,
                role = ?,
                display_name = ?,
                department = ?,
                is_active = TRUE,
                approval_status = ?,
                approval_note = ?,
                approved_by_user_id = ?,
                approved_at = ?,
                last_login_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """.trimIndent(),
            seed.username,
            encodedPassword,
            seed.role,
            seed.displayName,
            seed.department,
            seed.approvalStatus,
            seed.approvalNote,
            seed.approvedByUserId,
            seed.approvedAtTimestamp(),
            seed.id
        )
        if (updated > 0) return

        // 고정 계정이 없을 때만 새 row를 삽입한다.
        jdbc.update(
            """
            INSERT INTO admin_users
            (id, username, password_hash, role, display_name, department, is_active, approval_status,
             approval_note, approved_by_user_id, approved_at, last_login_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            seed.id,
            seed.username,
            encodedPassword,
            seed.role,
            seed.displayName,
            seed.department,
            seed.approvalStatus,
            seed.approvalNote,
            seed.approvedByUserId,
            seed.approvedAtTimestamp()
        )
    }

    /**
     * 같은 사용자명으로 남아 있는 이전 로컬 계정 잔여물을 정리합니다.
     */
    private fun cleanupConflictingDevUsernames(seed: LocalDevAccountSeed) {
        // seed 계정과 다른 ID를 가진 이전 테스트 요청은 먼저 제거한다.
        jdbc.update(
            """
            DELETE FROM clipping_user_requests
            WHERE requester_user_id IN (SELECT id FROM admin_users WHERE username = ? AND id <> ?)
               OR reviewed_by_user_id IN (SELECT id FROM admin_users WHERE username = ? AND id <> ?)
            """.trimIndent(),
            seed.username,
            seed.id,
            seed.username,
            seed.id
        )
        // 다른 계정이 참조하던 승인자 연결은 고정 계정으로 다시 맞출 예정이므로 비워둔다.
        jdbc.update(
            """
            UPDATE admin_users
            SET approved_by_user_id = NULL
            WHERE approved_by_user_id IN (SELECT id FROM admin_users WHERE username = ? AND id <> ?)
            """.trimIndent(),
            seed.username,
            seed.id
        )
        // 동일 username으로 남아 있는 이전 row는 삭제해 고정 ID를 보장한다.
        jdbc.update(
            "DELETE FROM admin_users WHERE username = ? AND id <> ?",
            seed.username,
            seed.id
        )
    }

    /**
     * 승인 완료/반려 상태 계정에만 승인 시각을 부여합니다.
     */
    private fun LocalDevAccountSeed.approvedAtTimestamp(): java.sql.Timestamp? =
        if (approvalStatus == "APPROVED" || approvalStatus == "REJECTED") {
            java.sql.Timestamp.from(Instant.parse("2026-03-06T08:30:00Z"))
        } else {
            null
        }
}
