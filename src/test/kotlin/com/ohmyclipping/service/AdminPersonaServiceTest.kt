package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.Persona
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.EntityRevisionStore
import com.ohmyclipping.store.PersonaStore
import com.ohmyclipping.store.PersonaVersionStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdminPersonaServiceTest {

    private val personaStore = mockk<PersonaStore>()
    private val personaVersionStore = mockk<PersonaVersionStore>(relaxed = true)
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val entityRevisionStore = mockk<EntityRevisionStore>(relaxed = true)
    private val entityRevisionRecorder = EntityRevisionRecorder(entityRevisionStore)
    private val auditActorResolver = mockk<AuditActorResolver>().apply {
        every { resolve(any()) } answers {
            val arg = firstArg<String?>()
            ResolvedActor(id = arg, name = arg ?: "system")
        }
    }
    private val service = AdminPersonaService(
        personaStore, personaVersionStore, auditLogStore, entityRevisionRecorder, auditActorResolver
    )

    @Nested
    inner class `삭제 정책` {

        @Test
        fun `구독이 있는 페르소나는 삭제할 수 없다`() {
            val persona = Persona(id = "p1", name = "커스텀", systemPrompt = "test", isPreset = false)
            every { personaStore.findById("p1") } returns persona
            every { personaStore.countActiveSubscriptions("p1") } returns 3

            shouldThrow<com.ohmyclipping.error.ConflictException> {
                service.deletePersona("p1")
            }.message shouldContain "3명이 사용 중"
        }

        @Test
        fun `구독이 0건인 페르소나는 삭제할 수 있다`() {
            val custom = Persona(id = "p2", name = "커스텀", systemPrompt = "test", isPreset = false)
            every { personaStore.findById("p2") } returns custom
            every { personaStore.countActiveSubscriptions("p2") } returns 0
            every { personaStore.delete("p2") } just runs

            service.deletePersona("p2")
            verify(exactly = 1) { personaStore.delete("p2") }
        }

        @Test
        fun `프리셋 페르소나 삭제 시도 시 ConflictException을 던진다`() {
            val preset = Persona(id = "p3", name = "경영진 브리핑", systemPrompt = "test", isPreset = true)
            every { personaStore.findById("p3") } returns preset

            shouldThrow<com.ohmyclipping.error.ConflictException> {
                service.deletePersona("p3")
            }.message shouldContain "프리셋"

            verify(exactly = 0) { personaStore.delete(any()) }
        }
    }

    @Nested
    inner class `비활성화 보호` {

        @Test
        fun `프리셋 페르소나 비활성화 시도 시 ConflictException을 던진다`() {
            val preset = Persona(id = "p1", name = "프리셋", systemPrompt = "test", isPreset = true)
            every { personaStore.findById("p1") } returns preset

            shouldThrow<com.ohmyclipping.error.ConflictException> {
                service.updatePersona(id = "p1", isActive = false)
            }.message shouldContain "프리셋"
        }

        @Test
        fun `일반 페르소나는 비활성화할 수 있다`() {
            val custom = Persona(id = "p2", name = "커스텀", systemPrompt = "test", isPreset = false)
            every { personaStore.findById("p2") } returns custom
            every { personaStore.update(any()) } answers { firstArg() }

            val result = service.updatePersona(id = "p2", isActive = false)
            result.isActive shouldBe false
        }
    }

    @Nested
    inner class `수정 시 통합 업데이트` {

        @Test
        fun `프리셋 수정 시 summaryStyle, maxItems, language 변경이 반영된다`() {
            val preset = Persona(
                id = "p1",
                name = "프리셋",
                systemPrompt = "test",
                isPreset = true,
                language = "ko",
                maxItems = 5,
                summaryStyle = "원본",
                targetAudience = "원본"
            )
            every { personaStore.findById("p1") } returns preset
            val captured = slot<Persona>()
            every { personaStore.update(capture(captured)) } answers { firstArg() }

            service.updatePersona(
                id = "p1",
                name = "새이름",
                summaryStyle = "변경값",
                targetAudience = "변경값",
                maxItems = 3,
                language = "en"
            )

            verify { personaStore.update(any()) }
            captured.captured.name shouldBe "새이름"
            captured.captured.summaryStyle shouldBe "변경값"
            captured.captured.targetAudience shouldBe "변경값"
            captured.captured.maxItems shouldBe 3
            captured.captured.language shouldBe "en"
        }

        @Test
        fun `수정 시 previewTitle, previewSource, previewBody가 반영된다`() {
            val preset = Persona(id = "p1", name = "프리셋", systemPrompt = "test", isPreset = true)
            every { personaStore.findById("p1") } returns preset
            val captured = slot<Persona>()
            every { personaStore.update(capture(captured)) } answers { firstArg() }

            service.updatePersona(
                id = "p1",
                previewTitle = "새 제목",
                previewSource = "새 출처",
                previewBody = "새 본문"
            )

            verify { personaStore.update(any()) }
            captured.captured.previewTitle shouldBe "새 제목"
            captured.captured.previewSource shouldBe "새 출처"
            captured.captured.previewBody shouldBe "새 본문"
        }

        @Test
        fun `일반 페르소나도 summaryStyle, maxItems, language 변경이 반영된다`() {
            val custom = Persona(
                id = "p2",
                name = "커스텀",
                systemPrompt = "test",
                isPreset = false,
                language = "ko",
                maxItems = 5,
                summaryStyle = "원본"
            )
            every { personaStore.findById("p2") } returns custom
            val captured = slot<Persona>()
            every { personaStore.update(capture(captured)) } answers { firstArg() }

            service.updatePersona(
                id = "p2",
                summaryStyle = "변경값",
                maxItems = 3,
                language = "en"
            )

            verify { personaStore.update(any()) }
            captured.captured.summaryStyle shouldBe "변경값"
            captured.captured.maxItems shouldBe 3
            captured.captured.language shouldBe "en"
        }
    }

    @Nested
    inner class `입력 제약 검증` {

        @Test
        fun `systemPrompt가 5000자 초과면 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.createPersona(
                    name = "테스트",
                    description = null,
                    systemPrompt = "a".repeat(5001),
                    summaryStyle = null,
                    targetAudience = null,
                    maxItems = 5,
                    language = "ko"
                )
            }.message shouldContain "시스템 프롬프트"
        }

        @Test
        fun `name이 200자 초과면 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.createPersona(
                    name = "a".repeat(201),
                    description = null,
                    systemPrompt = "test",
                    summaryStyle = null,
                    targetAudience = null,
                    maxItems = 5,
                    language = "ko"
                )
            }.message shouldContain "이름"
        }

        @Test
        fun `name 공백은 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.createPersona(
                    name = "  ",
                    description = null,
                    systemPrompt = "test",
                    summaryStyle = null,
                    targetAudience = null,
                    maxItems = 5,
                    language = "ko"
                )
            }
        }

        @Test
        fun `description이 1000자 초과면 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.createPersona(
                    name = "테스트",
                    description = "a".repeat(1001),
                    systemPrompt = "test",
                    summaryStyle = null,
                    targetAudience = null,
                    maxItems = 5,
                    language = "ko"
                )
            }.message shouldContain "설명"
        }

        @Test
        fun `updatePersona에서 systemPrompt 5001자 초과도 거부된다`() {
            val existing = Persona(id = "p1", name = "기존", systemPrompt = "기존 프롬프트", isPreset = false)
            every { personaStore.findById("p1") } returns existing

            shouldThrow<InvalidInputException> {
                service.updatePersona(id = "p1", systemPrompt = "a".repeat(5001))
            }.message shouldContain "시스템 프롬프트"
        }

        @Test
        fun `createPersona 입력에 null byte가 있으면 제거되어 저장된다`() {
            val captured = slot<Persona>()
            every { personaStore.save(capture(captured)) } answers { firstArg() }

            service.createPersona(
                name = "정\u0000상",
                description = null,
                systemPrompt = "프롬프트",
                summaryStyle = null,
                targetAudience = null,
                maxItems = 5,
                language = "ko"
            )

            // null byte는 제거되어야 한다.
            captured.captured.name shouldBe "정상"
        }
    }

    @Nested
    inner class `낙관적 잠금` {

        @Test
        fun `expectedUpdatedAt이 null이면 store update 경로를 사용한다`() {
            val persona = Persona(id = "p-lock-1", name = "기존", systemPrompt = "p", isPreset = false)
            every { personaStore.findById("p-lock-1") } returns persona
            every { personaStore.update(any()) } answers { firstArg() }

            service.updatePersona(id = "p-lock-1", name = "새이름", expectedUpdatedAt = null)

            verify(exactly = 1) { personaStore.update(any()) }
            verify(exactly = 0) { personaStore.updateWithExpectedUpdatedAt(any(), any()) }
        }

        @Test
        fun `expectedUpdatedAt이 일치하면 저장이 성공한다`() {
            val updatedAt = java.time.Instant.parse("2026-04-10T00:00:00Z")
            val persona = Persona(
                id = "p-lock-2", name = "기존", systemPrompt = "p", isPreset = false, updatedAt = updatedAt
            )
            every { personaStore.findById("p-lock-2") } returns persona
            every {
                personaStore.updateWithExpectedUpdatedAt(any(), updatedAt)
            } answers { firstArg() }

            val result = service.updatePersona(
                id = "p-lock-2", name = "새이름", expectedUpdatedAt = updatedAt
            )
            result.name shouldBe "새이름"
            verify(exactly = 0) { personaStore.update(any()) }
            verify(exactly = 1) { personaStore.updateWithExpectedUpdatedAt(any(), updatedAt) }
        }

        @Test
        fun `store가 null을 반환하면 StaleEditInfo를 담은 ConflictException을 던진다`() {
            val stale = java.time.Instant.parse("2026-04-09T00:00:00Z")
            val latestUpdatedAt = java.time.Instant.parse("2026-04-10T00:00:00Z")
            val existing = Persona(
                id = "p-lock-3", name = "기존", systemPrompt = "p", isPreset = false,
                updatedAt = stale
            )
            val latest = existing.copy(name = "다른편집자의이름", updatedAt = latestUpdatedAt)
            // 첫 findById는 충돌 직전의 상태, 두 번째(충돌 후)는 최신 상태를 반환한다.
            every { personaStore.findById("p-lock-3") } returnsMany listOf(existing, latest)
            every {
                personaStore.updateWithExpectedUpdatedAt(any(), stale)
            } returns null

            val ex = shouldThrow<com.ohmyclipping.error.ConflictException> {
                service.updatePersona(id = "p-lock-3", name = "내이름", expectedUpdatedAt = stale)
            }

            ex.staleEditInfo shouldBe com.ohmyclipping.error.StaleEditInfo(
                code = "STALE_EDIT",
                latestUpdatedAt = latestUpdatedAt,
                latestEditorName = "관리자",
                changedFieldNames = listOf("name")
            )
        }
    }

    @Nested
    inner class `setActive 테스트` {

        @Test
        fun `기존 페르소나의 isActive를 업데이트하고 감사 로그를 남긴다`() {
            val existing = Persona(id = "p1", name = "커스텀", systemPrompt = "test", isActive = true)
            every { personaStore.findById("p1") } returns existing
            every { personaStore.update(any()) } answers { firstArg() }

            val result = service.setActive(id = "p1", isActive = false, actorUsername = "admin")

            result.isActive shouldBe false
            verify { personaStore.update(match { !it.isActive && it.id == "p1" }) }
            verify(exactly = 1) {
                auditLogStore.log(
                    actorId = "admin",
                    actorName = "admin",
                    action = "DEACTIVATE_PERSONA",
                    targetType = "PERSONA",
                    targetId = "p1",
                    targetName = "커스텀"
                )
            }
        }

        @Test
        fun `존재하지 않는 페르소나 ID는 NotFoundException`() {
            every { personaStore.findById("missing") } returns null

            shouldThrow<com.ohmyclipping.error.NotFoundException> {
                service.setActive(id = "missing", isActive = false, actorUsername = "admin")
            }
        }

        @Test
        fun `이미 동일 상태면 저장소와 감사 로그 호출 없이 그대로 반환한다`() {
            val existing = Persona(id = "p1", name = "커스텀", systemPrompt = "test", isActive = false)
            every { personaStore.findById("p1") } returns existing

            val result = service.setActive(id = "p1", isActive = false, actorUsername = "admin")

            result.isActive shouldBe false
            result shouldBeSameInstanceAs existing
            verify(exactly = 0) { personaStore.update(any()) }
            verify(exactly = 0) { auditLogStore.log(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `프리셋 페르소나는 setActive로 비활성화할 수 없다`() {
            val preset = Persona(id = "p1", name = "프리셋", systemPrompt = "test", isPreset = true, isActive = true)
            every { personaStore.findById("p1") } returns preset

            shouldThrow<com.ohmyclipping.error.ConflictException> {
                service.setActive(id = "p1", isActive = false, actorUsername = "admin")
            }
            verify(exactly = 0) { personaStore.update(any()) }
            verify(exactly = 0) { auditLogStore.log(any(), any(), any(), any(), any(), any(), any()) }
        }
    }
}
