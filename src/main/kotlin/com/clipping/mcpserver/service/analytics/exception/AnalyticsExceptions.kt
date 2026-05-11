package com.clipping.mcpserver.service.analytics.exception

import java.time.LocalDate

/**
 * 페르소나 분석 레이어의 sealed 예외 계층.
 *
 * AGENTS.md 1.3 규칙에 따라 광범위 catch(Exception) 을 금지하고,
 * 배치 / 서비스에서는 이 sealed 계층의 구체 클래스만 catch 하도록 강제한다.
 * 예상 가능한 실패 유형을 enum 처럼 명시해 운영 디버깅 시 원인 분류가 쉬워진다.
 */
sealed class AnalyticsException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * 외부 API 호출 실패 (Gemini Embedding, Gemini LLM, Slack API 등).
 * 재시도 또는 해당 항목 스킵 후 진행이 가능하다.
 */
class AnalyticsExternalException(message: String, cause: Throwable? = null) :
    AnalyticsException(message, cause)

/**
 * 도메인 규칙 위반 (스냅샷 데이터 일관성 깨짐, 예상 값 누락 등).
 * 재시도해도 동일하게 실패하므로 즉시 배치 중단해야 한다.
 */
class AnalyticsDomainException(message: String, cause: Throwable? = null) :
    AnalyticsException(message, cause)

/**
 * 같은 주차 배치가 이미 RUNNING 상태일 때 발생한다.
 * Controller 는 이를 409 Conflict 로 변환한다.
 */
class BatchAlreadyRunningException(val weekStart: LocalDate) :
    AnalyticsException("이미 실행 중인 페르소나 주간 배치가 있습니다: weekStart=$weekStart")
