package com.ohmyclipping.service

import com.ohmyclipping.model.EntityRevisionResourceType
import com.ohmyclipping.model.PersonaVersionDetail
import com.ohmyclipping.model.PersonaVersionSummary
import com.ohmyclipping.model.Persona
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.PersonaStore
import com.ohmyclipping.store.PersonaVersionStore
import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.StaleEditInfo
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.support.InputSanitizer
import com.ohmyclipping.support.SlackMentionGuard
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AdminPersonaService(
    private val personaStore: PersonaStore,
    private val personaVersionStore: PersonaVersionStore,
    private val auditLogStore: AuditLogStore,
    private val entityRevisionRecorder: EntityRevisionRecorder,
    private val auditActorResolver: AuditActorResolver
) {

    companion object {
        /** 페르소나 이름 최대 길이 — DB: personas.name VARCHAR(200) */
        const val PERSONA_NAME_MAX = 200

        /** 페르소나 설명 최대 길이 — DB: personas.description TEXT */
        const val PERSONA_DESCRIPTION_MAX = 1000

        /** 시스템 프롬프트 최대 길이 — LLM maxInputTokens 보호용 */
        const val PERSONA_SYSTEM_PROMPT_MAX = 5000

        /** 요약 스타일 최대 길이 */
        const val PERSONA_SUMMARY_STYLE_MAX = 1000

        /** 대상 독자 최대 길이 */
        const val PERSONA_TARGET_AUDIENCE_MAX = 1000

        /** 미리보기 본문 최대 길이 — 관리자 예시 텍스트 */
        const val PERSONA_PREVIEW_BODY_MAX = 2000

        /** 미리보기 제목 최대 길이 */
        const val PERSONA_PREVIEW_TITLE_MAX = 200

        /** 미리보기 출처 최대 길이 */
        const val PERSONA_PREVIEW_SOURCE_MAX = 200
    }

    fun listPersonas(): List<Persona> =
        personaStore.list()

    /** 활성 프리셋 목록을 반환한다. */
    fun listPresets(): List<Persona> =
        personaStore.listPresets()

    fun getPersona(id: String): Persona =
        personaStore.findById(id) ?: throw NotFoundException("Persona not found: $id")

    fun createPersona(
        name: String,
        description: String?,
        systemPrompt: String,
        summaryStyle: String?,
        targetAudience: String?,
        maxItems: Int,
        language: String
    ): Persona {
        // 저장 경계에서 길이 + 제어 문자 정규화를 InputSanitizer로 일괄 적용한다.
        val cleanName = InputSanitizer.sanitizeRequired(name, "이름", maxLength = PERSONA_NAME_MAX)
        val cleanSystemPrompt =
            InputSanitizer.sanitizeRequired(systemPrompt, "시스템 프롬프트", maxLength = PERSONA_SYSTEM_PROMPT_MAX)
        val cleanDescription = InputSanitizer.sanitizeOptional(description, "설명", maxLength = PERSONA_DESCRIPTION_MAX)
        val cleanSummaryStyle =
            InputSanitizer.sanitizeOptional(summaryStyle, "요약 스타일", maxLength = PERSONA_SUMMARY_STYLE_MAX)
        val cleanTargetAudience =
            InputSanitizer.sanitizeOptional(targetAudience, "대상 독자", maxLength = PERSONA_TARGET_AUDIENCE_MAX)
        ensureValid(maxItems in UserDeliveryScheduleService.ALLOWED_MAX_ITEMS) {
            "maxItems는 ${UserDeliveryScheduleService.ALLOWED_MAX_ITEMS.sorted().joinToString(", ")} 중 하나여야 합니다."
        }
        // Slack 집단 멘션 패턴을 저장 시점에 중립화해 다이제스트/발송 단계에서 악의적 알림을 차단한다.
        return personaStore.save(
            Persona(
                id = "",
                name = SlackMentionGuard.neutralize(cleanName),
                description = cleanDescription?.let { SlackMentionGuard.neutralize(it) },
                systemPrompt = cleanSystemPrompt,
                summaryStyle = cleanSummaryStyle?.let { SlackMentionGuard.neutralize(it) },
                targetAudience = cleanTargetAudience?.let { SlackMentionGuard.neutralize(it) },
                maxItems = maxItems,
                language = language
            )
        )
    }

    fun updatePersona(
        id: String,
        name: String? = null,
        description: String? = null,
        systemPrompt: String? = null,
        summaryStyle: String? = null,
        targetAudience: String? = null,
        maxItems: Int? = null,
        language: String? = null,
        isActive: Boolean? = null,
        previewTitle: String? = null,
        previewSource: String? = null,
        previewBody: String? = null,
        expectedUpdatedAt: Instant? = null,
        actorUsername: String? = null
    ): Persona {
        val existing = getPersona(id)
        // 프리셋 페르소나는 비활성화할 수 없다
        if (existing.isPreset && isActive == false) {
            throw ConflictException("기본 제공 프리셋은 비활성화할 수 없어요")
        }
        if (maxItems != null) {
            ensureValid(maxItems in UserDeliveryScheduleService.ALLOWED_MAX_ITEMS) {
                "maxItems는 ${UserDeliveryScheduleService.ALLOWED_MAX_ITEMS.sorted().joinToString(", ")} 중 하나여야 합니다."
            }
        }

        // 입력 필드별로 길이/제어 문자 정규화를 먼저 수행해 이후 버전 비교에서도 동일 값으로 판단한다.
        val cleanName = name?.let { InputSanitizer.sanitizeOptional(it, "이름", PERSONA_NAME_MAX) }
        val cleanSystemPrompt = systemPrompt?.let {
            InputSanitizer.sanitizeOptional(it, "시스템 프롬프트", PERSONA_SYSTEM_PROMPT_MAX)
        }
        val cleanDescription = description?.let {
            InputSanitizer.sanitizeOptional(it, "설명", PERSONA_DESCRIPTION_MAX)
        }
        val cleanSummaryStyle = summaryStyle?.let {
            InputSanitizer.sanitizeOptional(it, "요약 스타일", PERSONA_SUMMARY_STYLE_MAX)
        }
        val cleanTargetAudience = targetAudience?.let {
            InputSanitizer.sanitizeOptional(it, "대상 독자", PERSONA_TARGET_AUDIENCE_MAX)
        }
        val cleanPreviewTitle = previewTitle?.let {
            InputSanitizer.sanitizeOptional(it, "미리보기 제목", PERSONA_PREVIEW_TITLE_MAX)
        }
        val cleanPreviewSource = previewSource?.let {
            InputSanitizer.sanitizeOptional(it, "미리보기 출처", PERSONA_PREVIEW_SOURCE_MAX)
        }
        val cleanPreviewBody = previewBody?.let {
            InputSanitizer.sanitizeOptional(it, "미리보기 본문", PERSONA_PREVIEW_BODY_MAX)
        }

        // 변경된 필드 요약을 미리 산출 (스냅샷 저장 시 사용)
        val changeSummary = buildChangeSummary(
            existing,
            cleanName,
            cleanDescription,
            cleanSystemPrompt,
            cleanSummaryStyle,
            cleanTargetAudience,
            maxItems,
            language,
            cleanPreviewTitle,
            cleanPreviewSource,
            cleanPreviewBody
        )

        // 모든 필드를 동일하게 업데이트 (프리셋 보호 제거)
        // 사용자 입력 필드는 저장 전에 Slack 멘션 패턴을 중립화한다.
        val updated = existing.copy(
            name = cleanName?.let { SlackMentionGuard.neutralize(it) } ?: existing.name,
            description = if (description != null) cleanDescription?.let { SlackMentionGuard.neutralize(it) } else existing.description,
            systemPrompt = cleanSystemPrompt ?: existing.systemPrompt,
            summaryStyle = if (summaryStyle != null) cleanSummaryStyle?.let { SlackMentionGuard.neutralize(it) } else existing.summaryStyle,
            targetAudience = if (targetAudience != null) cleanTargetAudience?.let { SlackMentionGuard.neutralize(it) } else existing.targetAudience,
            maxItems = maxItems ?: existing.maxItems,
            language = language ?: existing.language,
            isActive = isActive ?: existing.isActive,
            previewTitle = if (previewTitle != null) cleanPreviewTitle else existing.previewTitle,
            previewSource = if (previewSource != null) cleanPreviewSource else existing.previewSource,
            previewBody = if (previewBody != null) cleanPreviewBody else existing.previewBody,
            currentVersion = existing.currentVersion + 1,
            updatedAt = Instant.now()
        )
        // 편집 충돌 감지를 위한 변경 필드 목록을 미리 추출한다 (StaleEditInfo 구성용).
        val changedFields = collectChangedFieldNames(
            existing,
            cleanName, cleanDescription, cleanSystemPrompt,
            cleanSummaryStyle, cleanTargetAudience, maxItems, language,
            isActive, cleanPreviewTitle, cleanPreviewSource, cleanPreviewBody,
            descriptionProvided = description != null,
            summaryStyleProvided = summaryStyle != null,
            targetAudienceProvided = targetAudience != null,
            previewTitleProvided = previewTitle != null,
            previewSourceProvided = previewSource != null,
            previewBodyProvided = previewBody != null
        )
        // expectedUpdatedAt이 있으면 낙관적 잠금으로 편집 충돌을 감지한다.
        val saved = if (expectedUpdatedAt == null) {
            personaStore.update(updated)
        } else {
            personaStore.updateWithExpectedUpdatedAt(updated, expectedUpdatedAt)
                ?: run {
                    // 충돌 시 최신 상태를 다시 읽어와 StaleEditInfo에 실어 프론트로 전달한다.
                    val latest = personaStore.findById(id) ?: existing
                    throw ConflictException(
                        message = "페르소나가 다른 관리자에 의해 변경되었습니다. " +
                            "새로고침 후 다시 저장해주세요.",
                        staleEditInfo = StaleEditInfo(
                            latestUpdatedAt = latest.updatedAt,
                            latestEditorName = "관리자",
                            changedFieldNames = changedFields
                        )
                    )
                }
        }
        // 저장 성공 후 새 버전 스냅샷을 persona_versions 에 append.
        // 의미: persona_versions[N]은 persona가 version N 일 때의 상태(post-edit)를 보관한다.
        // V54 seed가 초기 상태를 version=1 로 INSERT 한 것과 동일한 규약이며,
        // pre-edit 상태를 OLD version 번호로 저장하면 첫 update 시 seed 와 충돌(off-by-one).
        saveCurrentSnapshot(saved, changeSummary)

        // 저장 성공 시 통합 revision 이력에 append — 되돌리기/히스토리 UI의 공통 소스.
        if (changedFields.isNotEmpty()) {
            entityRevisionRecorder.record(
                resourceType = EntityRevisionResourceType.PERSONA,
                resourceId = saved.id,
                editorId = actorUsername ?: "system",
                editorDisplayName = null,
                changedFields = changedFields,
                entity = saved
            )
        }
        return saved
    }

    /**
     * 특정 revision snapshot 값으로 페르소나를 복원한다.
     *
     * 낙관적 잠금은 [expectedUpdatedAt]으로 강제해 다른 관리자의 동시 편집을 덮어쓰지 않는다.
     * 복원 결과도 새 revision으로 append되므로 "되돌리기"도 이력에 남는다.
     *
     * updatePersona의 "null=유지" 의미 때문에 partial update를 사용할 수 없어 직접 복원한다.
     */
    fun restoreFromSnapshot(
        id: String,
        snapshot: Persona,
        expectedUpdatedAt: Instant,
        actorUsername: String
    ): Persona {
        val existing = getPersona(id)
        // 프리셋 비활성화 금지 정책을 복원 경로에서도 재확인한다.
        if (existing.isPreset && !snapshot.isActive) {
            throw ConflictException("기본 제공 프리셋은 비활성화할 수 없어요")
        }
        // 변경 필드 목록을 현재 상태 대비로 계산해 changedFields와 이력에 기록한다.
        val changedFields = diffPersonaFields(existing, snapshot)
        if (changedFields.isEmpty()) return existing

        val candidate = existing.copy(
            name = snapshot.name,
            description = snapshot.description,
            systemPrompt = snapshot.systemPrompt,
            summaryStyle = snapshot.summaryStyle,
            targetAudience = snapshot.targetAudience,
            maxItems = snapshot.maxItems,
            language = snapshot.language,
            isActive = snapshot.isActive,
            previewTitle = snapshot.previewTitle,
            previewSource = snapshot.previewSource,
            previewBody = snapshot.previewBody,
            currentVersion = existing.currentVersion + 1,
            updatedAt = Instant.now()
        )
        val saved = personaStore.updateWithExpectedUpdatedAt(candidate, expectedUpdatedAt)
            ?: run {
                val latest = personaStore.findById(id) ?: existing
                throw ConflictException(
                    message = "페르소나가 다른 관리자에 의해 변경되었습니다. " +
                        "새로고침 후 다시 저장해주세요.",
                    staleEditInfo = StaleEditInfo(
                        latestUpdatedAt = latest.updatedAt,
                        latestEditorName = "관리자",
                        changedFieldNames = changedFields
                    )
                )
            }
        entityRevisionRecorder.record(
            resourceType = EntityRevisionResourceType.PERSONA,
            resourceId = saved.id,
            editorId = actorUsername,
            editorDisplayName = null,
            changedFields = changedFields,
            entity = saved
        )
        return saved
    }

    /** 복원 시 현재 상태와 스냅샷 사이에 실제로 바뀌는 필드 목록만 계산한다. */
    private fun diffPersonaFields(current: Persona, snapshot: Persona): List<String> {
        val changes = mutableListOf<String>()
        if (current.name != snapshot.name) changes += "name"
        if (current.description != snapshot.description) changes += "description"
        if (current.systemPrompt != snapshot.systemPrompt) changes += "systemPrompt"
        if (current.summaryStyle != snapshot.summaryStyle) changes += "summaryStyle"
        if (current.targetAudience != snapshot.targetAudience) changes += "targetAudience"
        if (current.maxItems != snapshot.maxItems) changes += "maxItems"
        if (current.language != snapshot.language) changes += "language"
        if (current.isActive != snapshot.isActive) changes += "isActive"
        if (current.previewTitle != snapshot.previewTitle) changes += "previewTitle"
        if (current.previewSource != snapshot.previewSource) changes += "previewSource"
        if (current.previewBody != snapshot.previewBody) changes += "previewBody"
        return changes
    }

    /** 변경 필드 이름 목록을 추출한다. StaleEditInfo의 changedFieldNames로 사용. */
    @Suppress("LongParameterList")
    private fun collectChangedFieldNames(
        existing: Persona,
        name: String?,
        description: String?,
        systemPrompt: String?,
        summaryStyle: String?,
        targetAudience: String?,
        maxItems: Int?,
        language: String?,
        isActive: Boolean?,
        previewTitle: String?,
        previewSource: String?,
        previewBody: String?,
        descriptionProvided: Boolean,
        summaryStyleProvided: Boolean,
        targetAudienceProvided: Boolean,
        previewTitleProvided: Boolean,
        previewSourceProvided: Boolean,
        previewBodyProvided: Boolean
    ): List<String> {
        val changes = mutableListOf<String>()
        if (name != null && name != existing.name) changes += "name"
        if (descriptionProvided && description != existing.description) changes += "description"
        if (systemPrompt != null && systemPrompt != existing.systemPrompt) changes += "systemPrompt"
        if (summaryStyleProvided && summaryStyle != existing.summaryStyle) changes += "summaryStyle"
        if (targetAudienceProvided && targetAudience != existing.targetAudience) changes += "targetAudience"
        if (maxItems != null && maxItems != existing.maxItems) changes += "maxItems"
        if (language != null && language != existing.language) changes += "language"
        if (isActive != null && isActive != existing.isActive) changes += "isActive"
        if (previewTitleProvided && previewTitle != existing.previewTitle) changes += "previewTitle"
        if (previewSourceProvided && previewSource != existing.previewSource) changes += "previewSource"
        if (previewBodyProvided && previewBody != existing.previewBody) changes += "previewBody"
        return changes
    }

    /**
     * 페르소나의 `isActive` 상태를 지정한 값으로 설정한다.
     * 전체 필드 업데이트가 불필요한 경량 부분 변경 경로.
     *
     * - 존재하지 않는 `id` → [NotFoundException]
     * - 프리셋 페르소나를 비활성화 시도 → [ConflictException]
     * - 이미 같은 상태면 저장소 호출 없이 즉시 반환 (no-op)
     * - 실제 변경 시에만 감사 로그 1건 기록
     *
     * @param actorUsername 감사 로그 주체 (인증된 관리자명)
     */
    fun setActive(id: String, isActive: Boolean, actorUsername: String): Persona {
        // 존재 여부 확인
        val existing = personaStore.findById(id) ?: throw NotFoundException("Persona $id not found")
        // 프리셋 페르소나는 비활성화할 수 없다 (updatePersona와 동일한 보호 정책)
        if (existing.isPreset && !isActive) {
            throw ConflictException("기본 제공 프리셋은 비활성화할 수 없어요")
        }
        // 이미 동일 상태면 쓰기 없이 그대로 반환 (no-op)
        if (existing.isActive == isActive) return existing
        // 실제 상태 변경 + 감사 로그 기록
        val updated = personaStore.update(existing.copy(isActive = isActive))
        val activateActor = auditActorResolver.resolve(actorUsername)
        auditLogStore.log(
            actorId = activateActor.id,
            actorName = activateActor.name,
            action = if (isActive) "ACTIVATE_PERSONA" else "DEACTIVATE_PERSONA",
            targetType = "PERSONA",
            targetId = id,
            targetName = existing.name
        )
        return updated
    }

    @Transactional
    fun deletePersona(id: String, deletedByUsername: String? = null) {
        val existing = getPersona(id)
        // 프리셋 페르소나는 삭제할 수 없다
        if (existing.isPreset) {
            throw ConflictException("기본 제공 프리셋은 삭제할 수 없어요")
        }
        // 활성 구독이 있으면 삭제 불가
        val count = personaStore.countActiveSubscriptions(id)
        if (count > 0) {
            throw ConflictException("현재 ${count}명이 사용 중입니다. 먼저 비활성화하세요.")
        }
        personaStore.delete(id)
        val actor = auditActorResolver.resolve(deletedByUsername)
        auditLogStore.log(
            actorId = actor.id, actorName = actor.name,
            action = "DELETE", targetType = "PERSONA",
            targetId = id, targetName = existing.name
        )
    }

    /**
     * 특정 버전으로 롤백한다.
     * 현재 상태를 스냅샷으로 저장한 뒤, 대상 버전의 스냅샷으로 복원한다.
     */
    fun rollbackToVersion(personaId: String, targetVersion: Int): Persona {
        val persona = personaStore.findById(personaId)
            ?: throw NotFoundException("Persona not found: $personaId")
        val snapshot = personaVersionStore.findByPersonaIdAndVersion(personaId, targetVersion)
            ?: throw NotFoundException("Version $targetVersion not found for persona: $personaId")

        val restored = persona.copy(
            name = snapshot.name,
            description = snapshot.description,
            systemPrompt = snapshot.systemPrompt,
            summaryStyle = snapshot.summaryStyle,
            targetAudience = snapshot.targetAudience,
            maxItems = snapshot.maxItems,
            language = snapshot.language,
            previewTitle = snapshot.previewTitle,
            previewSource = snapshot.previewSource,
            previewBody = snapshot.previewBody,
            currentVersion = persona.currentVersion + 1,
            updatedAt = Instant.now()
        )
        val saved = personaStore.update(restored)
        // 롤백 결과 상태(= snapshot 데이터)를 새 버전 번호로 persona_versions 에 append.
        saveCurrentSnapshot(saved, "v${targetVersion}에서 롤백")
        return saved
    }

    /** 특정 페르소나의 버전 히스토리 목록을 반환한다. */
    fun getVersions(personaId: String): List<PersonaVersionSummary> {
        personaStore.findById(personaId)
            ?: throw NotFoundException("Persona not found: $personaId")
        return personaVersionStore.listByPersonaId(personaId)
    }

    /** 특정 페르소나의 특정 버전 스냅샷 상세를 반환한다. */
    fun getVersionDetail(personaId: String, version: Int): PersonaVersionDetail {
        return personaVersionStore.findByPersonaIdAndVersion(personaId, version)
            ?: throw NotFoundException("Version $version not found for persona: $personaId")
    }

    /**
     * 페르소나의 현재 상태를 버전 스냅샷으로 저장한다.
     */
    private fun saveCurrentSnapshot(persona: Persona, changeSummary: String) {
        val detail = PersonaVersionDetail(
            version = persona.currentVersion,
            name = persona.name,
            description = persona.description,
            systemPrompt = persona.systemPrompt,
            summaryStyle = persona.summaryStyle,
            targetAudience = persona.targetAudience,
            maxItems = persona.maxItems,
            language = persona.language,
            previewTitle = persona.previewTitle,
            previewSource = persona.previewSource,
            previewBody = persona.previewBody,
            changeSummary = changeSummary,
            createdAt = Instant.now()
        )
        personaVersionStore.saveSnapshot(persona.id, persona.currentVersion, detail, changeSummary)
    }

    /**
     * 변경된 필드를 비교하여 변경 요약 문자열을 생성한다.
     * 예: "시스템 프롬프트, 요약 스타일 수정"
     */
    private fun buildChangeSummary(
        existing: Persona,
        name: String?,
        description: String?,
        systemPrompt: String?,
        summaryStyle: String?,
        targetAudience: String?,
        maxItems: Int?,
        language: String?,
        previewTitle: String?,
        previewSource: String?,
        previewBody: String?
    ): String {
        val changes = mutableListOf<String>()
        // 입력값은 이미 sanitize 되어 있다고 가정하고 기존 값과 직접 비교한다.
        if (name != null && name != existing.name) changes.add("이름")
        if (description != null && description != existing.description) changes.add("설명")
        if (systemPrompt != null && systemPrompt != existing.systemPrompt) changes.add("시스템 프롬프트")
        if (summaryStyle != null && summaryStyle != existing.summaryStyle) changes.add("요약 스타일")
        if (targetAudience != null && targetAudience != existing.targetAudience) changes.add("대상 독자")
        if (maxItems != null && maxItems != existing.maxItems) changes.add("최대 항목 수")
        if (language != null && language != existing.language) changes.add("언어")
        if (previewTitle != null && previewTitle != existing.previewTitle) changes.add("미리보기 제목")
        if (previewSource != null && previewSource != existing.previewSource) changes.add("미리보기 출처")
        if (previewBody != null && previewBody != existing.previewBody) changes.add("미리보기 본문")
        return if (changes.isEmpty()) "변경 없음" else "${changes.joinToString(", ")} 수정"
    }
}
