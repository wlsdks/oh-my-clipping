package com.ohmyclipping.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.io.IOException
import java.security.MessageDigest

private val log = KotlinLogging.logger {}

/** 쿼리 값 최대 길이 — 초과 시 잘라내고 truncated 표시를 붙인다. */
private const val QUERY_MAX_LENGTH = 200

/** SHA-256 해시에서 사용할 접두 길이 (12자). */
private const val HASH_PREFIX_LENGTH = 12

/**
 * MCP 도구 호출 인자에서 민감 정보를 제거하거나 마스킹한다.
 *
 * 레드액션 규칙:
 * - `SlackUserId`를 포함하는 키 → SHA-256 해시 앞 12자로 치환
 * - `slackChannelId` → 그대로 유지 (감사 추적용)
 * - `query`이고 값이 200자 초과 → 잘라내기 + `...(truncated)` 접미
 * - 민감 키(비밀번호/토큰/시크릿/자격증명/웹훅/PEM 키 등) → `[REDACTED]`
 *   확장된 키워드: `password`, `passwd`, `secret`, `token`, `apikey`, `api_key`,
 *   `authorization`, `credential`, `credentials`, `webhook`, `bearer`,
 *   `privatekey`, `private_key`. 대소문자 무시.
 * - 값 안에 `password=...`, `token=...` 등 키-밸류 형태의 유출 패턴이
 *   포함돼 있으면 `key=***REDACTED***` 로 치환한다.
 * - 그 외 → 원본 유지
 */
@Component
class McpArgsRedactor(private val objectMapper: ObjectMapper) {

    /**
     * JSON-RPC 요청 본문에서 인자를 추출하여 레드액션 처리된 JSON 문자열을 반환한다.
     * 파싱 실패 시 에러 마커 JSON을 반환한다.
     *
     * @param toolName 호출된 도구 이름 (향후 도구별 규칙 확장 지점)
     * @param bodyBytes JSON-RPC 요청 본문 바이트 배열
     * @return 레드액션 처리된 인자 JSON 문자열
     */
    fun redact(toolName: String, bodyBytes: ByteArray): String {
        return try {
            val root = objectMapper.readTree(bodyBytes)
            // JSON-RPC params.arguments 경로에서 인자를 추출한다
            val args = root.path("params").path("arguments")
            if (args.isMissingNode || args.isNull) {
                return "{}"
            }
            val redacted = redactNode(args)
            objectMapper.writeValueAsString(redacted)
        } catch (e: IOException) {
            log.debug { "MCP 인자 파싱 실패: ${e.message}" }
            """{"_redactError":"invalid-json"}"""
        } catch (e: RuntimeException) {
            log.debug { "MCP 인자 파싱 실패: ${e.message}" }
            """{"_redactError":"invalid-json"}"""
        }
    }

    /**
     * JSON 노드를 재귀적으로 순회하며 키 이름에 따라 레드액션 규칙을 적용한다.
     * ObjectNode만 키 기반 레드액션 대상이고, 배열은 원소를 재귀 처리한다.
     */
    private fun redactNode(node: JsonNode): JsonNode {
        if (!node.isObject) return node
        val obj = node as ObjectNode
        val result = objectMapper.createObjectNode()
        obj.properties().forEach { (key, value) ->
            result.set<JsonNode>(key, redactField(key, value))
        }
        return result
    }

    /**
     * 단일 필드에 레드액션 규칙을 적용한다.
     * 중첩 오브젝트는 재귀 처리하고, 텍스트 값에 대해서만 키 기반 마스킹을 수행한다.
     */
    private fun redactField(key: String, value: JsonNode): JsonNode {
        // 중첩 객체는 재귀 처리
        if (value.isObject) return redactNode(value)

        // 배열 원소 재귀 처리
        if (value.isArray) {
            val arr = objectMapper.createArrayNode()
            value.forEach { arr.add(redactField(key, it)) }
            return arr
        }

        val keyLower = key.lowercase()
        return when {
            // 비밀 필드 — 완전 마스킹
            SENSITIVE_PATTERN.containsMatchIn(keyLower) ->
                TextNode("[REDACTED]")

            // Slack 사용자 ID — SHA-256 해시 접두어로 치환
            keyLower.contains("slackuserid") ->
                TextNode(sha256Prefix(value.asText()))

            // Slack 채널 ID — 감사 추적용으로 원본 유지
            key == "slackChannelId" -> value

            // 긴 쿼리 — 값 내부의 키-밸류 형태 유출을 마스킹한 뒤 길이 제한을 적용한다.
            key == "query" && value.isTextual -> {
                val masked = scrubEmbeddedSecrets(value.asText())
                TextNode(
                    if (masked.length > QUERY_MAX_LENGTH) {
                        masked.take(QUERY_MAX_LENGTH) + "...(truncated)"
                    } else {
                        masked
                    },
                )
            }

            // 그 외 텍스트 값에서도 임베디드 secret 패턴을 마스킹한다.
            value.isTextual -> {
                val raw = value.asText()
                val masked = scrubEmbeddedSecrets(raw)
                if (masked === raw) value else TextNode(masked)
            }

            else -> value
        }
    }

    /**
     * 문자열 값 내부에 포함된 `password=xxx`, `token: yyy` 같은 키-밸류 형태의
     * secret 유출 패턴을 `key=***REDACTED***` 로 치환한다.
     *
     * 매칭이 없으면 **원본 문자열 인스턴스를 그대로 반환**한다 (불필요한 TextNode
     * 생성 회피).
     */
    internal fun scrubEmbeddedSecrets(input: String): String {
        if (!VALUE_SECRET_PATTERN.containsMatchIn(input)) return input
        return VALUE_SECRET_PATTERN.replace(input) { match ->
            val keyName = match.groupValues[1]
            "$keyName=***REDACTED***"
        }
    }

    private companion object {
        /**
         * 민감 키 감지 패턴 — 광범위하게 매칭되는 하위 문자열 기준.
         *
         * 키 이름에 포함될 수 있는 모든 표기(camelCase, snake_case, 공백 없음)를
         * 하위 문자열 매칭으로 한 번에 커버한다. 예: `apiKey`, `API_KEY`,
         * `userPrivateKey`, `slack_webhook_url` 등 모두 매칭.
         */
        val SENSITIVE_PATTERN: Regex = Regex(
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

        /**
         * 문자열 값 안의 key-value 형태 secret 유출 패턴.
         * 예: `password=abc`, `api_key: xyz`, `token="bar"`, `authorization bearer xxx`,
         * `Authorization: Bearer xxx`.
         *
         * 캡처 그룹 1 = 키 이름 (마스킹 시 `$1=***REDACTED***` 로 교체).
         * 캡처 그룹 2 = 구분자. authorization 값 앞의 Bearer scheme 은 값 일부로 함께 소비한다.
         * 값 부분은 공백/따옴표/쉼표/개행 직전까지 greedy 매칭.
         */
        val VALUE_SECRET_PATTERN: Regex = Regex(
            "(password|passwd|secret|token|apikey|api_key|authorization|bearer|credential|credentials|privatekey|private_key)" +
                "([\"':= ]+)" +
                "(?:bearer\\s+)?" +
                "[^\"',\\s]+",
            RegexOption.IGNORE_CASE,
        )

        /** SHA-256 해시의 hex 인코딩 앞 12자를 반환한다. */
        fun sha256Prefix(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
                .take(HASH_PREFIX_LENGTH)
        }
    }
}
