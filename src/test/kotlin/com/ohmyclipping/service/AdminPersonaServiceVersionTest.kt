package com.ohmyclipping.service

import com.ohmyclipping.service.dto.PersonaVersionDetail
import com.ohmyclipping.service.dto.PersonaVersionSummary
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.Persona
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.EntityRevisionStore
import com.ohmyclipping.store.PersonaStore
import com.ohmyclipping.store.PersonaVersionStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class AdminPersonaServiceVersionTest {

    private val personaStore = mockk<PersonaStore>()
    private val personaVersionStore = mockk<PersonaVersionStore>(relaxed = true)
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val entityRevisionStore = mockk<EntityRevisionStore>(relaxed = true)
    private val entityRevisionRecorder = EntityRevisionRecorder(entityRevisionStore)
    private val service = AdminPersonaService(
        personaStore, personaVersionStore, auditLogStore, entityRevisionRecorder, mockk(relaxed = true)
    )

    @Nested
    inner class `수정 시 버전 관리` {

        @Test
        fun `수정 시 새 버전(N+1) 으로 post-edit 스냅샷이 persona_versions에 저장된다`() {
            // 의미: persona_versions[N]은 persona가 version N 일 때의 상태(post-edit)를 보관한다.
            // V54 seed가 초기 상태를 version=1 로 INSERT 한 규약과 일치한다.
            val existing = Persona(
                id = "p1",
                name = "원본이름",
                systemPrompt = "원본프롬프트",
                currentVersion = 3,
                isPreset = true
            )
            every { personaStore.findById("p1") } returns existing
            every { personaStore.update(any()) } answers { firstArg() }

            service.updatePersona(id = "p1", name = "새이름")

            // 새 버전(4)으로 post-edit 상태가 저장되어야 한다 (이름은 새이름, 변경 안 한 systemPrompt 는 그대로 유지)
            verify(exactly = 1) {
                personaVersionStore.saveSnapshot(
                    personaId = "p1",
                    version = 4,
                    detail = match { it.name == "새이름" && it.systemPrompt == "원본프롬프트" && it.version == 4 },
                    changeSummary = match { it.contains("이름") }
                )
            }
        }

        @Test
        fun `seed 가 persona_versions(version=1) 을 갖고 있어도 첫 update 가 unique constraint 를 위반하지 않는다`() {
            // 회귀 테스트: V54 seed → persona_versions[1] 존재 + persona.currentVersion=1 상태에서
            // 첫 update 호출 시 saveSnapshot(version=1) 시도하면 unique constraint 위반 (이전 버그).
            // fix 후에는 saveSnapshot(version=2) 로 호출해야 한다.
            val existing = Persona(
                id = "p-seed",
                name = "seed 이름",
                systemPrompt = "seed 프롬프트",
                currentVersion = 1
            )
            every { personaStore.findById("p-seed") } returns existing
            every { personaStore.update(any()) } answers { firstArg() }

            service.updatePersona(id = "p-seed", systemPrompt = "수정된 프롬프트")

            verify(exactly = 1) {
                personaVersionStore.saveSnapshot(
                    personaId = "p-seed",
                    version = 2,
                    detail = match { it.systemPrompt == "수정된 프롬프트" && it.version == 2 },
                    changeSummary = any()
                )
            }
            // version=1 로는 저장되지 않아야 한다 (seed 와 충돌)
            verify(exactly = 0) {
                personaVersionStore.saveSnapshot(
                    personaId = "p-seed",
                    version = 1,
                    detail = any(),
                    changeSummary = any()
                )
            }
        }

        @Test
        fun `update 를 두 번 연속 호출해도 version 이 1→2→3 순으로 증가하며 충돌하지 않는다`() {
            // 멱등 회귀 테스트: 다회 update 시 version 시퀀스 보장
            var current = Persona(
                id = "p2",
                name = "초기",
                systemPrompt = "프롬프트0",
                currentVersion = 1
            )
            every { personaStore.findById("p2") } answers { current }
            every { personaStore.update(any()) } answers {
                current = firstArg()
                current
            }

            service.updatePersona(id = "p2", systemPrompt = "프롬프트1")
            service.updatePersona(id = "p2", systemPrompt = "프롬프트2")

            verify(exactly = 1) {
                personaVersionStore.saveSnapshot(
                    personaId = "p2", version = 2,
                    detail = match { it.systemPrompt == "프롬프트1" && it.version == 2 },
                    changeSummary = any()
                )
            }
            verify(exactly = 1) {
                personaVersionStore.saveSnapshot(
                    personaId = "p2", version = 3,
                    detail = match { it.systemPrompt == "프롬프트2" && it.version == 3 },
                    changeSummary = any()
                )
            }
        }

        @Test
        fun `수정 시 currentVersion이 증가한다`() {
            val existing = Persona(
                id = "p1",
                name = "원본",
                systemPrompt = "test",
                currentVersion = 5
            )
            every { personaStore.findById("p1") } returns existing
            val captured = slot<Persona>()
            every { personaStore.update(capture(captured)) } answers { firstArg() }

            service.updatePersona(id = "p1", systemPrompt = "새 프롬프트")

            captured.captured.currentVersion shouldBe 6
        }

        @Test
        fun `변경 요약이 변경된 필드를 포함한다`() {
            val existing = Persona(
                id = "p1",
                name = "원본",
                systemPrompt = "test",
                summaryStyle = "기존",
                currentVersion = 1
            )
            every { personaStore.findById("p1") } returns existing
            every { personaStore.update(any()) } answers { firstArg() }

            service.updatePersona(id = "p1", systemPrompt = "변경됨", summaryStyle = "변경됨")

            verify {
                personaVersionStore.saveSnapshot(
                    any(), any(),
                    any(),
                    changeSummary = match { it.contains("시스템 프롬프트") && it.contains("요약 스타일") }
                )
            }
        }

        @Test
        fun `수정 성공 시 entity_revision_history에 append된다`() {
            val existing = Persona(
                id = "p1",
                name = "원본",
                systemPrompt = "test",
                currentVersion = 1
            )
            every { personaStore.findById("p1") } returns existing
            every { personaStore.update(any()) } answers { firstArg() }

            service.updatePersona(id = "p1", systemPrompt = "변경됨", actorUsername = "admin-user")

            verify(exactly = 1) {
                entityRevisionStore.append(
                    resourceType = "persona",
                    resourceId = "p1",
                    editorId = "admin-user",
                    editorDisplayName = any(),
                    changedFields = match { it.contains("systemPrompt") },
                    snapshot = match { it.contains("변경됨") }
                )
            }
        }

        @Test
        fun `실제로 바뀐 필드가 없으면 entity_revision_history에 append되지 않는다`() {
            val existing = Persona(
                id = "p1",
                name = "동일",
                systemPrompt = "동일프롬프트",
                currentVersion = 1
            )
            every { personaStore.findById("p1") } returns existing
            every { personaStore.update(any()) } answers { firstArg() }

            service.updatePersona(id = "p1", name = "동일", actorUsername = "admin-user")

            verify(exactly = 0) { entityRevisionStore.append(any(), any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class `삭제 정책` {

        @Test
        fun `구독이 있는 페르소나는 삭제할 수 없다`() {
            val persona = Persona(id = "p1", name = "테스트", systemPrompt = "test")
            every { personaStore.findById("p1") } returns persona
            every { personaStore.countActiveSubscriptions("p1") } returns 5

            val ex = shouldThrow<com.ohmyclipping.error.ConflictException> {
                service.deletePersona("p1")
            }
            ex.message shouldContain "5명이 사용 중"
        }

        @Test
        fun `구독이 0건인 페르소나는 삭제할 수 있다`() {
            val persona = Persona(id = "p1", name = "테스트", systemPrompt = "test")
            every { personaStore.findById("p1") } returns persona
            every { personaStore.countActiveSubscriptions("p1") } returns 0
            every { personaStore.delete("p1") } just runs

            service.deletePersona("p1")
            verify(exactly = 1) { personaStore.delete("p1") }
        }
    }

    @Nested
    inner class `롤백` {

        @Test
        fun `롤백 시 해당 버전 스냅샷으로 복원되고 새 버전이 생성된다`() {
            val persona = Persona(
                id = "p1",
                name = "현재이름",
                systemPrompt = "현재프롬프트",
                summaryStyle = "현재스타일",
                language = "en",
                maxItems = 10,
                currentVersion = 5
            )
            val snapshot = PersonaVersionDetail(
                version = 2,
                name = "과거이름",
                description = "과거설명",
                systemPrompt = "과거프롬프트",
                summaryStyle = "과거스타일",
                targetAudience = "과거독자",
                maxItems = 3,
                language = "ko",
                previewTitle = "과거제목",
                previewSource = "과거출처",
                previewBody = "과거본문",
                changeSummary = "이전 변경",
                createdAt = Instant.now()
            )

            every { personaStore.findById("p1") } returns persona
            every { personaVersionStore.findByPersonaIdAndVersion("p1", 2) } returns snapshot
            val captured = slot<Persona>()
            every { personaStore.update(capture(captured)) } answers { firstArg() }

            val result = service.rollbackToVersion("p1", 2)

            // 새 버전(6)으로 롤백된 상태가 스냅샷으로 저장됨 (post-rollback state, snapshot 데이터와 동일)
            verify(exactly = 1) {
                personaVersionStore.saveSnapshot(
                    personaId = "p1",
                    version = 6,
                    detail = match { it.name == "과거이름" && it.version == 6 },
                    changeSummary = "v2에서 롤백"
                )
            }

            // 복원된 값 검증
            captured.captured.name shouldBe "과거이름"
            captured.captured.systemPrompt shouldBe "과거프롬프트"
            captured.captured.summaryStyle shouldBe "과거스타일"
            captured.captured.language shouldBe "ko"
            captured.captured.maxItems shouldBe 3
            captured.captured.currentVersion shouldBe 6
        }

        @Test
        fun `존재하지 않는 버전으로 롤백 시 NotFoundException이 발생한다`() {
            val persona = Persona(id = "p1", name = "테스트", systemPrompt = "test")
            every { personaStore.findById("p1") } returns persona
            every { personaVersionStore.findByPersonaIdAndVersion("p1", 99) } returns null

            shouldThrow<NotFoundException> {
                service.rollbackToVersion("p1", 99)
            }
        }

        @Test
        fun `존재하지 않는 페르소나 롤백 시 NotFoundException이 발생한다`() {
            every { personaStore.findById("unknown") } returns null

            shouldThrow<NotFoundException> {
                service.rollbackToVersion("unknown", 1)
            }
        }
    }

    @Nested
    inner class `restoreFromSnapshot` {

        @Test
        fun `스냅샷 값으로 복원하고 revision을 append한다`() {
            val current = Persona(
                id = "p1",
                name = "현재이름",
                systemPrompt = "현재프롬프트",
                currentVersion = 5,
                updatedAt = Instant.parse("2026-04-17T00:00:00Z")
            )
            val snapshot = current.copy(name = "과거이름", systemPrompt = "과거프롬프트")
            every { personaStore.findById("p1") } returns current
            every {
                personaStore.updateWithExpectedUpdatedAt(any(), Instant.parse("2026-04-17T00:00:00Z"))
            } answers { firstArg() }

            val restored = service.restoreFromSnapshot(
                id = "p1",
                snapshot = snapshot,
                expectedUpdatedAt = Instant.parse("2026-04-17T00:00:00Z"),
                actorUsername = "admin"
            )

            restored.name shouldBe "과거이름"
            restored.systemPrompt shouldBe "과거프롬프트"
            verify(exactly = 1) {
                entityRevisionStore.append(
                    resourceType = "persona",
                    resourceId = "p1",
                    editorId = "admin",
                    editorDisplayName = any(),
                    changedFields = match { it.contains("name") && it.contains("systemPrompt") },
                    snapshot = any()
                )
            }
        }

        @Test
        fun `동시 편집 충돌 시 ConflictException을 던진다`() {
            val current = Persona(
                id = "p1",
                name = "현재",
                systemPrompt = "프롬프트",
                currentVersion = 2,
                updatedAt = Instant.parse("2026-04-17T00:00:00Z")
            )
            val snapshot = current.copy(name = "과거")
            every { personaStore.findById("p1") } returns current
            every {
                personaStore.updateWithExpectedUpdatedAt(any(), any())
            } returns null

            shouldThrow<com.ohmyclipping.error.ConflictException> {
                service.restoreFromSnapshot(
                    id = "p1",
                    snapshot = snapshot,
                    expectedUpdatedAt = Instant.parse("2026-04-16T00:00:00Z"),
                    actorUsername = "admin"
                )
            }
        }

        @Test
        fun `프리셋 페르소나를 비활성 상태로 복원하려 하면 ConflictException을 던진다`() {
            val preset = Persona(
                id = "p-preset",
                name = "프리셋",
                systemPrompt = "p",
                isPreset = true,
                isActive = true,
                updatedAt = Instant.parse("2026-04-17T00:00:00Z")
            )
            val snapshot = preset.copy(isActive = false)
            every { personaStore.findById("p-preset") } returns preset

            shouldThrow<com.ohmyclipping.error.ConflictException> {
                service.restoreFromSnapshot(
                    id = "p-preset",
                    snapshot = snapshot,
                    expectedUpdatedAt = Instant.parse("2026-04-17T00:00:00Z"),
                    actorUsername = "admin"
                )
            }
        }
    }

    @Nested
    inner class `버전 조회` {

        @Test
        fun `버전 히스토리 목록을 반환한다`() {
            val persona = Persona(id = "p1", name = "테스트", systemPrompt = "test")
            val versions = listOf(
                PersonaVersionSummary(version = 2, changeSummary = "이름 수정", createdAt = Instant.now()),
                PersonaVersionSummary(version = 1, changeSummary = "최초 생성", createdAt = Instant.now())
            )
            every { personaStore.findById("p1") } returns persona
            every { personaVersionStore.listByPersonaId("p1") } returns versions

            val result = service.getVersions("p1")
            result.size shouldBe 2
            result[0].version shouldBe 2
        }

        @Test
        fun `존재하지 않는 페르소나의 버전 조회 시 NotFoundException이 발생한다`() {
            every { personaStore.findById("unknown") } returns null

            shouldThrow<NotFoundException> {
                service.getVersions("unknown")
            }
        }

        @Test
        fun `특정 버전 상세를 반환한다`() {
            val detail = PersonaVersionDetail(
                version = 1,
                name = "테스트",
                description = null,
                systemPrompt = "프롬프트",
                summaryStyle = null,
                targetAudience = null,
                maxItems = 5,
                language = "ko",
                previewTitle = null,
                previewSource = null,
                previewBody = null,
                changeSummary = "최초 생성",
                createdAt = Instant.now()
            )
            every { personaVersionStore.findByPersonaIdAndVersion("p1", 1) } returns detail

            val result = service.getVersionDetail("p1", 1)
            result.version shouldBe 1
            result.systemPrompt shouldBe "프롬프트"
        }

        @Test
        fun `존재하지 않는 버전 상세 조회 시 NotFoundException이 발생한다`() {
            every { personaVersionStore.findByPersonaIdAndVersion("p1", 99) } returns null

            shouldThrow<NotFoundException> {
                service.getVersionDetail("p1", 99)
            }
        }
    }
}
