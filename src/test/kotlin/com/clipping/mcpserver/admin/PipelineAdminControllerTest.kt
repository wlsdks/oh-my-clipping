package com.clipping.mcpserver.admin

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.Persona
import com.clipping.mcpserver.service.dto.pipeline.PipelineRunEntity
import com.clipping.mcpserver.service.dto.pipeline.PipelineRunStatus
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.PersonaStore
import com.clipping.mcpserver.store.PipelineRunStore
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

/**
 * 파이프라인 관리자 API의 스모크 테스트.
 * personaId 쿼리 파라미터가 인식되어 정상 응답을 반환하는지 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class PipelineAdminControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var personaStore: PersonaStore

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var pipelineRunStore: PipelineRunStore

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    @Nested
    inner class `실행 이력 조회` {

        @Test
        fun `personaId 쿼리 파라미터 없이 200을 반환한다`() {
            adminClient().get()
                .uri("/api/admin/pipeline/runs")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
        }

        @Test
        fun `personaId 쿼리파라미터를 허용한다`() {
            // 존재하지 않는 페르소나면 빈 컨텐츠, 존재하는 페르소나면 해당 실행 이력을 반환한다.
            adminClient().get()
                .uri("/api/admin/pipeline/runs?personaId=non-existent-persona")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.totalCount").isEqualTo(0)
        }

        @Test
        fun `personaId로 필터하면 해당 페르소나 카테고리의 run만 반환한다`() {
            // 페르소나 + 해당 페르소나에 연결된 활성 카테고리 + 실행 이력 1건을 시드한다.
            val persona = personaStore.save(
                Persona(
                    id = "",
                    name = "Pipeline-FilterPersona-${System.nanoTime()}",
                    systemPrompt = "test"
                )
            )
            val category = categoryStore.save(
                Category(
                    id = "",
                    name = "Pipeline-FilterCategory-${System.nanoTime()}",
                    personaId = persona.id
                )
            )
            val run = pipelineRunStore.save(
                PipelineRunEntity(
                    categoryId = category.id,
                    categoryName = category.name,
                    triggeredBy = "test",
                    status = PipelineRunStatus.SUCCEEDED,
                    orchestrationMode = "SEQUENTIAL",
                    startedAt = Instant.now()
                )
            )

            adminClient().get()
                .uri("/api/admin/pipeline/runs?personaId=${persona.id}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.totalCount").isEqualTo(1)
                .jsonPath("$.content[0].id").isEqualTo(run.id)
                .jsonPath("$.content[0].categoryId").isEqualTo(category.id)
        }
    }
}
