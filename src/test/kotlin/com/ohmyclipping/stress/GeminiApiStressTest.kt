package com.ohmyclipping.stress

import com.ohmyclipping.resilience.TokenBucketRateLimiter
import com.ohmyclipping.model.Language
import io.kotest.matchers.doubles.shouldBeLessThan
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import com.ohmyclipping.ai.ClippingSummarizer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * 실제 Gemini API를 호출하는 소규모 스트레스 테스트.
 * GEMINI_API_KEY 환경변수가 설정되어 있을 때만 실행된다.
 * Slack 발송은 절대 하지 않는다.
 */
@Tag("stress")
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiApiStressTest {

    @Autowired
    private lateinit var summarizer: ClippingSummarizer

    @Test
    fun `50건 기사 요약 시 Rate Limiter가 429 에러를 방지한다`() {
        val totalArticles = 50
        val rateLimiter = TokenBucketRateLimiter("gemini-test", permitsPerMinute = 50, maxBurst = 5)

        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        val responseTimes = mutableListOf<Long>()

        val totalElapsed = measureTimeMillis {
            (1..totalArticles).forEach { i ->
                rateLimiter.acquire()
                val title = "Tech Industry Update $i: AI Developments"
                val content = """
                    The technology sector continues to evolve rapidly with new developments in
                    artificial intelligence and cloud computing. Article number $i in the series.
                """.trimIndent()

                val elapsed = measureTimeMillis {
                    try {
                        val result = summarizer.summarizeArticle(title, content, Language.FOREIGN, null)
                        if (result != null) successCount.incrementAndGet()
                        else errorCount.incrementAndGet()
                    } catch (e: Exception) {
                        errorCount.incrementAndGet()
                        println("Error on article $i: ${e.message}")
                    }
                }
                responseTimes.add(elapsed)
            }
        }

        val sorted = responseTimes.sorted()
        println("=== Gemini API Stress Test ===")
        println("Total: $totalArticles | Success: ${successCount.get()} | Error: ${errorCount.get()}")
        println("Total elapsed: ${totalElapsed}ms")
        if (sorted.isNotEmpty()) {
            println("p50: ${sorted[sorted.size / 2]}ms | p95: ${sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.lastIndex)]}ms")
        }

        // Kotest matcher — 에러율 10% 이하
        (errorCount.get().toDouble() / totalArticles) shouldBeLessThan 0.10
    }
}
