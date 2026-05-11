package com.clipping.mcpserver.service.competitor

import com.clipping.mcpserver.model.Organization
import com.clipping.mcpserver.model.OrganizationType
import com.clipping.mcpserver.store.OrganizationStore
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component

/**
 * 경쟁사(CompetitorWatchlist) CRUD 를 Organizations 테이블로 mirror 하는 동기화기.
 *
 * 정책:
 * - 경쟁사 관리 화면이 단일 관리점(single source of truth) 이 되고,
 *   Organizations 의 COMPETITOR row 는 자동 유지된다.
 * - mirror 실패가 주 연산(Competitor CRUD)을 실패시키지 않도록
 *   narrow 한 [DataAccessException] 만 잡아 warn 로그 후 흡수한다.
 * - 이름 변경 시 기존 Organization 을 rename 하고, 동명(이미 존재)일 때는
 *   새로 만들지 않고 단순 description 을 갱신한다(멱등).
 *
 * 주의:
 * - 여기서 던지는 일반 예외는 상위로 전파돼 테스트 가능한 버그로 드러나게 둔다.
 *   오직 DB 관련 예외만 흡수해서 경쟁사 등록이 실패하지 않도록 한다.
 */
@Component
class CompetitorOrganizationSynchronizer(
    private val organizationStore: OrganizationStore,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** V131 backfill 과 동일한 설명 prefix — 사용자 수동 생성 레코드와 구분하기 위함. */
        const val SYNC_DESCRIPTION = "경쟁사 자동 동기"
    }

    /**
     * 경쟁사 신규 생성 시 호출. 동일 이름 Organization 이 이미 있으면 skip(멱등).
     *
     * @param competitorName 저장된 경쟁사 이름. 이미 sanitize/Slack neutralize 된 값이어야 한다.
     */
    fun onCompetitorCreated(competitorName: String) {
        // mirror 실패가 경쟁사 등록을 실패시키면 안 되므로 DB 예외만 흡수한다.
        runMirror("create", competitorName) {
            val existing = organizationStore.findByName(competitorName)
            if (existing != null) {
                // 동명 레코드가 이미 있으면 type 만 COMPETITOR 로 맞춰준다(idempotent).
                if (existing.type != OrganizationType.COMPETITOR) {
                    organizationStore.update(
                        existing.copy(
                            type = OrganizationType.COMPETITOR,
                            description = existing.description ?: SYNC_DESCRIPTION,
                        )
                    )
                }
                return@runMirror
            }
            // 신규 Organization 생성 — id 는 store 가 UUID 를 부여한다.
            organizationStore.save(
                Organization(
                    id = "",
                    name = competitorName,
                    type = OrganizationType.COMPETITOR,
                    domain = null,
                    description = SYNC_DESCRIPTION,
                )
            )
        }
    }

    /**
     * 경쟁사 이름 변경 시 호출. 기존 Organization 을 찾아 rename 한다.
     * 이름이 바뀌지 않았으면 아무 것도 하지 않는다.
     *
     * @param oldName 이전 경쟁사 이름(변경 전).
     * @param newName 새 경쟁사 이름(변경 후). oldName 과 같으면 no-op.
     */
    fun onCompetitorRenamed(oldName: String, newName: String) {
        if (oldName == newName) return
        runMirror("rename", "$oldName -> $newName") {
            val existing = organizationStore.findByName(oldName)
            if (existing == null) {
                // 기존 mirror 가 없으면 새로 생성한다 — 중복이 아니면 create 와 동일한 경로.
                onCompetitorCreated(newName)
                return@runMirror
            }
            // 새 이름이 이미 다른 Organization 으로 존재하면 충돌을 피해 skip 하고 로그만 남긴다.
            val duplicate = organizationStore.findByName(newName)
            if (duplicate != null && duplicate.id != existing.id) {
                log.warn(
                    "Organization rename skipped — target name already taken: old='{}' new='{}'",
                    oldName, newName
                )
                return@runMirror
            }
            organizationStore.update(existing.copy(name = newName))
        }
    }

    /**
     * 경쟁사 삭제 시 호출. 동명 Organization 을 찾아 함께 제거한다.
     * mirror 대상이 없으면 no-op.
     */
    fun onCompetitorDeleted(competitorName: String) {
        runMirror("delete", competitorName) {
            val existing = organizationStore.findByName(competitorName) ?: return@runMirror
            // 경쟁사 자동 동기 목적이 아니면(사용자가 type 을 바꿔뒀다면) 건드리지 않는다.
            if (existing.type != OrganizationType.COMPETITOR) {
                log.info(
                    "Organization '{}' kept — type changed to {} (no longer a competitor mirror)",
                    competitorName, existing.type
                )
                return@runMirror
            }
            organizationStore.delete(existing.id)
        }
    }

    /**
     * mirror 동작을 공통 가드 안에서 실행한다.
     * DB 예외는 warn 로그 후 흡수하여 원 연산(Competitor CRUD)이 성공 상태로 유지되게 한다.
     */
    private inline fun runMirror(op: String, label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: DataAccessException) {
            // 외부 조직 동기화 실패는 경쟁사 관리의 부수효과 — 원 연산은 이미 커밋됐다.
            log.warn("Organization mirror [{}] failed for '{}': {}", op, label, e.message, e)
        }
    }
}
