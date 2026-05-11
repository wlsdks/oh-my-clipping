package com.ohmyclipping.ai

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class JsonContentNormalizerTest {

    @Test
    fun `should escape raw newline inside json string`() {
        val raw = """
            {
              "translatedTitle": null,
              "summary": "첫 줄
            둘째 줄",
              "keywords": ["a", "b", "c"],
              "importanceScore": 0.7
            }
        """.trimIndent()

        val normalized = JsonContentNormalizer.escapeControlCharsInStrings(raw)
        normalized.contains("첫 줄\\n") shouldBe true
        normalized.contains("\n둘째 줄\"") shouldBe false
    }

    @Test
    fun `should keep escaped sequences intact`() {
        val raw = """{"summary":"line1\\nline2","keywords":["k1"],"importanceScore":0.3}"""
        val normalized = JsonContentNormalizer.escapeControlCharsInStrings(raw)
        normalized shouldBe raw
    }
}
