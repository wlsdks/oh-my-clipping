package com.clipping.mcpserver.mcp

/**
 * MCP 도구 호출 시점의 호출자 컨텍스트를 현재 스레드에 잠시 바인딩하기 위한 홀더.
 *
 * Spring AI MCP 서버(WebFlux)는 `ToolCallback.call()` 을 동기 호출하며,
 * 해당 호출은 상위 [McpAuditFilter] 의 `chain.filter(decoratedExchange)` 안에서
 * 동일 리액터 워커 스레드 위에서 실행된다. 이 구간 동안 ThreadLocal 로
 * `tokenKid` 를 전달하면 [McpRateLimiter] 가 actor 로 자동 채택할 수 있다.
 *
 * 리액티브 스케줄러 hop 을 거쳐 다른 스레드로 넘어가면 값이 사라지므로,
 * 도구 내부가 스레드를 바꾸는 경우에는 복구해서 쓰지 않도록 주의한다.
 * 이 유틸은 `best-effort` 경량 컨텍스트로, 실패 시 rate-limit actor 는
 * "anonymous" 로 폴백해 기능 자체는 유지된다.
 */
object McpCallerContext {

    private val tokenKidHolder: ThreadLocal<String?> = ThreadLocal()

    /** 현재 스레드에 [tokenKid] 를 바인딩한다. */
    fun setTokenKid(tokenKid: String?) {
        if (tokenKid.isNullOrBlank()) {
            tokenKidHolder.remove()
        } else {
            tokenKidHolder.set(tokenKid)
        }
    }

    /** 현재 스레드에 바인딩된 토큰 지문을 반환한다. 없으면 null. */
    fun tokenKid(): String? = tokenKidHolder.get()

    /** 스레드 바인딩을 해제한다. 필터 종료 시점에 반드시 호출해 ThreadLocal 누수를 방지한다. */
    fun clear() {
        tokenKidHolder.remove()
    }
}
