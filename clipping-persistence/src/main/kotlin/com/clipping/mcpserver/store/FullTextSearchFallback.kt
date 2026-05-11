package com.clipping.mcpserver.store

import org.springframework.dao.DataAccessException
import org.springframework.jdbc.BadSqlGrammarException

internal fun shouldFallbackFromFullTextSearch(e: DataAccessException): Boolean =
    e is BadSqlGrammarException

internal fun buildWebsearchAnyQuery(terms: List<String>): String =
    terms
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" OR ") { "\"${it.replace("\"", " ").trim()}\"" }

