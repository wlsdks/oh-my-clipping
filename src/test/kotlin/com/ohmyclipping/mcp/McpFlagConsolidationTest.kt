package com.ohmyclipping.mcp

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.AnnotationUtils

/**
 * McpServerConfig의 @ConditionalOnProperty 어노테이션이
 * clipping.mcp.server.enabled 프로퍼티를 올바르게 참조하는지 검증한다.
 *
 * 전체 Spring 컨텍스트 기반 테스트는 McpServerConfigDisabledTest,
 * McpServerConfigEnabledTest, McpServerHalfGatedTest에서 이미 검증한다.
 * 본 테스트는 어노테이션 메타데이터 수준의 단위 테스트이다.
 */
class McpFlagConsolidationTest {

    @Nested
    inner class `MCP 플래그 통합` {

        @Test
        fun `McpServerConfig는 clipping_mcp_server_enabled 프로퍼티로 게이트된다`() {
            val annotation = AnnotationUtils.findAnnotation(
                McpServerConfig::class.java,
                ConditionalOnProperty::class.java
            )

            annotation shouldBe annotation // 어노테이션 존재 확인
            annotation!!.name shouldBe arrayOf("clipping.mcp.server.enabled")
            annotation.havingValue shouldBe "true"
        }

        @Test
        fun `havingValue가 true여서 미설정 시 빈이 비활성화된다`() {
            val annotation = AnnotationUtils.findAnnotation(
                McpServerConfig::class.java,
                ConditionalOnProperty::class.java
            )!!

            // matchIfMissing 기본값은 false — 프로퍼티 미설정 시 빈이 등록되지 않는다
            annotation.matchIfMissing shouldBe false
        }
    }
}
