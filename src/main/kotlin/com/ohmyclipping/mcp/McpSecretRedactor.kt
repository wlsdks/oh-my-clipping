package com.ohmyclipping.mcp

/**
 * MCP 표면에 노출되는 문자열에서 secret-like 값을 마스킹하는 공통 정책.
 *
 * 요청 감사 로그와 도구 오류 응답이 같은 기준을 사용해야, 새 secret 표기를 추가할 때
 * 한쪽만 갱신되는 drift 를 피할 수 있다.
 */
internal object McpSecretRedactor {

    private val sensitiveKeyPattern: Regex = Regex(
        listOf(
            "password",
            "passwd",
            "secret",
            "token",
            "apikey",
            "api_key",
            "authorization",
            "credential",
            "credentials",
            "webhook",
            "bearer",
            "privatekey",
            "private_key",
        ).joinToString("|"),
        RegexOption.IGNORE_CASE,
    )

    private val embeddedSecretPattern: Regex = Regex(
        "(password|passwd|secret|token|apikey|api_key|authorization|bearer|credential|credentials|privatekey|private_key)" +
            "([\"':= ]+)" +
            "(?:bearer\\s+)?" +
            "[^\"',\\s]+",
        RegexOption.IGNORE_CASE,
    )

    fun isSensitiveKey(key: String): Boolean =
        sensitiveKeyPattern.containsMatchIn(key)

    /**
     * 문자열 값 내부에 포함된 `password=xxx`, `token: yyy` 같은 key-value 형태의
     * secret 유출 패턴을 `key=***REDACTED***` 로 치환한다.
     *
     * 매칭이 없으면 원본 문자열 인스턴스를 그대로 반환한다.
     */
    fun scrubEmbeddedSecrets(input: String): String {
        if (!embeddedSecretPattern.containsMatchIn(input)) return input
        return embeddedSecretPattern.replace(input) { match ->
            val keyName = match.groupValues[1]
            "$keyName=***REDACTED***"
        }
    }
}
