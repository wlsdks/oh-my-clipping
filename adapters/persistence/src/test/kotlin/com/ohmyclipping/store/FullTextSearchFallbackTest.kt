package com.ohmyclipping.store

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.dao.RecoverableDataAccessException
import org.springframework.jdbc.BadSqlGrammarException
import java.sql.SQLException

class FullTextSearchFallbackTest {

    @Test
    fun `FTS 문법 미지원 예외만 LIKE fallback 대상으로 본다`() {
        val grammar = BadSqlGrammarException(
            "search",
            "SELECT websearch_to_tsquery('simple', ?)",
            SQLException("function not found"),
        )
        val recoverable = RecoverableDataAccessException("connection interrupted")

        shouldFallbackFromFullTextSearch(grammar) shouldBe true
        shouldFallbackFromFullTextSearch(recoverable) shouldBe false
    }

    @Test
    fun `키워드 목록은 websearch OR 쿼리로 안전하게 변환한다`() {
        val query = buildWebsearchAnyQuery(
            listOf("AI platform", "C++", "M&A", "  ", "\"quoted\""),
        )

        query shouldBe "\"AI platform\" OR \"C++\" OR \"M&A\" OR \"quoted\""
    }
}

