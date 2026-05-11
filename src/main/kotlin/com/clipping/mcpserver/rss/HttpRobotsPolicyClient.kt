package com.clipping.mcpserver.rss

import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.service.collection.RobotsPolicyClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException

private val log = KotlinLogging.logger {}

@Component
class HttpRobotsPolicyClient(
    private val properties: ClippingMcpServerProperties
) : RobotsPolicyClient {

    override fun isAllowed(targetUri: URI): Boolean {
        return try {
            val robotsUri = URI(targetUri.scheme, targetUri.authority, "/robots.txt", null, null)
            val conn = robotsUri.toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = properties.pageConnectionTimeoutMs
            conn.readTimeout = properties.pageReadTimeoutMs
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)

            if (conn.responseCode !in 200..299) return true

            val robotsText = conn.inputStream.bufferedReader().use { it.readText() }
            evaluate(targetUri.path.ifBlank { "/" }, robotsText)
        } catch (e: IOException) {
            allowOnRobotsFailure(targetUri, e)
        } catch (e: IllegalArgumentException) {
            allowOnRobotsFailure(targetUri, e)
        } catch (e: SecurityException) {
            allowOnRobotsFailure(targetUri, e)
        } catch (e: URISyntaxException) {
            allowOnRobotsFailure(targetUri, e)
        } catch (e: ClassCastException) {
            allowOnRobotsFailure(targetUri, e)
        }
    }

    private fun allowOnRobotsFailure(targetUri: URI, cause: Exception): Boolean {
        // 네트워크/파싱 실패는 수집을 막지 않도록 fail-open 하되, 반복 실패 진단이 가능하도록
        // debug 레벨로 stacktrace 를 남긴다. 운영 로그를 시끄럽게 하지 않는다.
        log.debug(cause) {
            "robots.txt fetch/parse failed for ${targetUri.authority}, falling back to allow: ${cause.message}"
        }
        return true
    }

    internal fun evaluate(path: String, robotsText: String): Boolean {
        val userAgent = USER_AGENT.lowercase()
        val rulesByAgent = parseRules(robotsText)
        val directRules = rulesByAgent
            .filterKeys { it != "*" && userAgent.contains(it) }
            .values
            .flatten()
        val wildcardRules = rulesByAgent["*"].orEmpty()
        val candidateRules = if (directRules.isNotEmpty()) directRules else wildcardRules
        if (candidateRules.isEmpty()) return true

        var bestRule: Rule? = null
        var bestLen = -1
        for (rule in candidateRules) {
            val pattern = rule.path
            if (pattern.isBlank()) continue
            if (!path.startsWith(pattern)) continue

            val len = pattern.length
            if (len > bestLen) {
                bestLen = len
                bestRule = rule
                continue
            }
            if (len == bestLen && bestRule != null && !bestRule.allow && rule.allow) {
                // RFC-like tie-break: prefer Allow when pattern lengths are equal.
                bestRule = rule
            }
        }
        return bestRule?.allow ?: true
    }

    private fun parseRules(robotsText: String): Map<String, List<Rule>> {
        val rulesByAgent = linkedMapOf<String, MutableList<Rule>>()
        var currentAgents = mutableListOf<String>()
        var seenRuleInGroup = false

        for (rawLine in robotsText.lineSequence()) {
            val line = rawLine.substringBefore("#").trim()
            if (line.isBlank()) continue

            val lower = line.lowercase()
            when {
                lower.startsWith("user-agent:") -> {
                    val agent = lower.substringAfter("user-agent:").trim()
                    if (agent.isBlank()) continue

                    if (seenRuleInGroup) {
                        currentAgents = mutableListOf(agent)
                        seenRuleInGroup = false
                    } else {
                        currentAgents.add(agent)
                    }
                }

                lower.startsWith("allow:") || lower.startsWith("disallow:") -> {
                    if (currentAgents.isEmpty()) continue
                    seenRuleInGroup = true

                    val allow = lower.startsWith("allow:")
                    val rulePath = normalizePath(line.substringAfter(":").trim())
                    for (agent in currentAgents) {
                        rulesByAgent.computeIfAbsent(agent) { mutableListOf() }
                            .add(Rule(path = rulePath, allow = allow))
                    }
                }
            }
        }

        return rulesByAgent
    }

    private fun normalizePath(path: String): String {
        if (path.isBlank()) return ""
        val normalized = if (path.startsWith("/")) path else "/$path"
        return normalized.substringBefore("*")
    }

    private data class Rule(
        val path: String,
        val allow: Boolean
    )

    private companion object {
        private const val USER_AGENT = "ClippingBot/1.0"
    }
}
