package com.ohmyclipping.service

import com.ohmyclipping.model.EntityRevision
import com.ohmyclipping.model.EntityRevisionResourceType
import com.ohmyclipping.model.Persona
import com.ohmyclipping.store.EntityRevisionStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [EntityRevisionRecorder] 직접 단위 테스트.
 *
 * 간접 커버는 AdminPersonaServiceVersionTest 에 있지만, 최근 추가된 read 경로
 * (`listRecent`, `findById`) 와 editor 이름 익명화 규칙을 직접 잠근다.
 */
class EntityRevisionRecorderTest {

    private val entityRevisionStore = mockk<EntityRevisionStore>(relaxed = true)
    private val recorder = EntityRevisionRecorder(entityRevisionStore)

    private fun revision(
        id: String = "rev-1",
        resourceType: EntityRevisionResourceType = EntityRevisionResourceType.PERSONA,
        resourceId: String = "p-1",
        revisionNumber: Long = 1L
    ): EntityRevision = EntityRevision(
        id = id,
        resourceType = resourceType.wire,
        resourceId = resourceId,
        revisionNumber = revisionNumber,
        editorId = "admin",
        editorDisplayName = "관리자",
        changedFields = listOf("name"),
        snapshot = "{}",
        createdAt = Instant.parse("2026-04-10T00:00:00Z")
    )

    @Nested
    inner class `record - JSON 직렬화 & append 위임` {

        @Test
        fun `도메인 객체를 JSON 으로 직렬화해 store append 에 전달한다`() {
            val persona = Persona(
                id = "p-1",
                name = "테스트 페르소나",
                systemPrompt = "프롬프트",
                currentVersion = 1
            )
            val snapshotSlot = slot<String>()
            every {
                entityRevisionStore.append(
                    resourceType = any(),
                    resourceId = any(),
                    editorId = any(),
                    editorDisplayName = any(),
                    changedFields = any(),
                    snapshot = capture(snapshotSlot)
                )
            } returns revision()

            recorder.record(
                resourceType = EntityRevisionResourceType.PERSONA,
                resourceId = "p-1",
                editorId = "admin",
                editorDisplayName = "관리자A",
                changedFields = listOf("name"),
                entity = persona
            )

            // snapshot JSON 안에 엔티티 필드가 포함되어야 한다 — round-trip 검증을 위한 smoke.
            snapshotSlot.captured shouldContain "\"name\":\"테스트 페르소나\""
            snapshotSlot.captured shouldContain "\"systemPrompt\":\"프롬프트\""
        }

        @Test
        fun `editorDisplayName 이 null 이면 editorId 를 익명화해 store 에 전달한다`() {
            val persona = Persona(id = "p-1", name = "n", systemPrompt = "p")
            val displayNameSlot = slot<String>()
            every {
                entityRevisionStore.append(
                    resourceType = any(),
                    resourceId = any(),
                    editorId = any(),
                    editorDisplayName = capture(displayNameSlot),
                    changedFields = any(),
                    snapshot = any()
                )
            } returns revision()

            // editorId 가 UUID 이면 "관리자" 로 대체된다.
            recorder.record(
                resourceType = EntityRevisionResourceType.PERSONA,
                resourceId = "p-1",
                editorId = "12345678-1234-1234-1234-123456789abc",
                editorDisplayName = null,
                changedFields = emptyList(),
                entity = persona
            )

            displayNameSlot.captured shouldBe "관리자"
        }
    }

    @Nested
    inner class `listRecent - 최근 리비전 조회 (read 경로)` {

        @Test
        fun `resourceType 의 wire 값으로 store 에 위임한다`() {
            val expected = listOf(revision(id = "r1"), revision(id = "r2", revisionNumber = 2L))
            every { entityRevisionStore.listRecent("persona", "p-1", 20) } returns expected

            val result = recorder.listRecent(EntityRevisionResourceType.PERSONA, "p-1", 20)

            result shouldBe expected
            verify(exactly = 1) { entityRevisionStore.listRecent("persona", "p-1", 20) }
        }

        @Test
        fun `다른 resourceType 에 대해서도 wire 값 매핑이 정확하다`() {
            every { entityRevisionStore.listRecent("category_rule", "r-1", 5) } returns emptyList()

            recorder.listRecent(EntityRevisionResourceType.CATEGORY_RULE, "r-1", 5)

            verify(exactly = 1) { entityRevisionStore.listRecent("category_rule", "r-1", 5) }
        }

        @Test
        fun `limit=1 경계값 호출도 그대로 전달되고 결과가 비어 있으면 emptyList 를 돌려준다`() {
            every { entityRevisionStore.listRecent(any(), any(), 1) } returns emptyList()

            val result = recorder.listRecent(EntityRevisionResourceType.RSS_SOURCE, "src-1", 1)

            result shouldBe emptyList()
        }
    }

    @Nested
    inner class `findById - revision 단건 조회` {

        @Test
        fun `store 에 매칭 값이 있으면 그대로 반환한다`() {
            val rev = revision(id = "rev-42")
            every { entityRevisionStore.findById("rev-42") } returns rev

            val result = recorder.findById("rev-42")

            result shouldBe rev
        }

        @Test
        fun `store 에 매칭이 없으면 null 을 돌려준다 (NotFound 로 승격하지 않음)`() {
            // 호출자 (admin 컨트롤러) 가 null 처리를 결정하도록 설계되어 있음.
            every { entityRevisionStore.findById("missing") } returns null

            val result = recorder.findById("missing")

            result shouldBe null
        }
    }

    @Nested
    inner class `anonymizeEditorName - actor 정규화 규칙` {

        @Test
        fun `UUID 형식이면 '관리자' 로 익명화한다`() {
            recorder.anonymizeEditorName("12345678-1234-1234-1234-123456789abc") shouldBe "관리자"
        }

        @Test
        fun `빈 문자열과 null 모두 '관리자' 로 폴백한다`() {
            recorder.anonymizeEditorName(null) shouldBe "관리자"
            recorder.anonymizeEditorName("") shouldBe "관리자"
            recorder.anonymizeEditorName("   ") shouldBe "관리자"
        }

        @Test
        fun `'system' 은 대소문자 관계없이 '시스템' 으로 대체한다`() {
            recorder.anonymizeEditorName("system") shouldBe "시스템"
            recorder.anonymizeEditorName("SYSTEM") shouldBe "시스템"
            recorder.anonymizeEditorName("System") shouldBe "시스템"
        }

        @Test
        fun `일반 사용자 이름은 그대로 보존한다`() {
            recorder.anonymizeEditorName("eddy") shouldBe "eddy"
        }
    }

    @Nested
    inner class `deserialize - snapshot 복원` {

        @Test
        fun `record 가 쓴 JSON 을 deserialize 로 원본 타입으로 되돌릴 수 있다 (round trip)`() {
            val persona = Persona(id = "p-1", name = "원본", systemPrompt = "프롬프트", currentVersion = 7)
            val snapshotSlot = slot<String>()
            every {
                entityRevisionStore.append(any(), any(), any(), any(), any(), capture(snapshotSlot))
            } returns revision()

            recorder.record(
                resourceType = EntityRevisionResourceType.PERSONA,
                resourceId = "p-1",
                editorId = "admin",
                editorDisplayName = "관리자",
                changedFields = listOf("name"),
                entity = persona
            )
            val restored = recorder.deserialize(snapshotSlot.captured, Persona::class.java)

            restored.id shouldBe "p-1"
            restored.name shouldBe "원본"
            restored.systemPrompt shouldBe "프롬프트"
            restored.currentVersion shouldBe 7
        }
    }
}
