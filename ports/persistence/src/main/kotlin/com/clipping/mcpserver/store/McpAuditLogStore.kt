package com.clipping.mcpserver.store

import java.time.Instant

/**
 * MCP 도구 호출 감사 로그의 저장소 인터페이스.
 * append-only 테이블에 기록하고, 보존 기간 초과 항목만 삭제할 수 있다.
 */
interface McpAuditLogStore {

    /**
     * 감사 로그 항목을 기록한다.
     *
     * @param entry 기록할 감사 항목 — id, requestId 등이 모두 채워져 있어야 한다.
     */
    fun insert(entry: McpAuditEntry)

    /**
     * 지정 시각보다 오래된 감사 로그를 삭제한다.
     * PostgreSQL append-only 트리거를 우회하기 위해 세션 변수를 설정한 뒤 삭제한다.
     *
     * @param cutoff 이 시각 이전의 로그가 삭제 대상
     * @return 삭제된 건수
     */
    fun deleteOlderThan(cutoff: Instant): Int
}

/**
 * MCP 감사 로그 한 건의 불변 데이터 모델.
 *
 * @property id 고유 식별자 (UUID)
 * @property requestId JSON-RPC 요청 식별자
 * @property actor 호출자 식별 정보 (서비스 토큰 이름 등)
 * @property toolName 호출된 MCP 도구 이름
 * @property argsJson 레드액션 처리된 인자 JSON 문자열
 * @property resultStatus 결과 상태 (OK, ERROR 등)
 * @property resultCode 결과 코드 (도구 정의 코드, nullable)
 * @property httpStatusCode HTTP 상태 코드 (프록시 호출 시, nullable)
 * @property durationMs 처리 소요 시간 (밀리초)
 * @property errorMessage 에러 발생 시 메시지 (nullable)
 */
data class McpAuditEntry(
    val id: String,
    val requestId: String,
    val actor: String,
    val toolName: String,
    val argsJson: String?,
    val resultStatus: String,
    val resultCode: Int?,
    val httpStatusCode: Int?,
    val durationMs: Int,
    val errorMessage: String?,
)
