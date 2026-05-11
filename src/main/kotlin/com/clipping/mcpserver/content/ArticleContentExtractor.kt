package com.clipping.mcpserver.content

import com.clipping.mcpserver.model.Language

data class ExtractedArticle(
    val title: String,
    val content: String,
    val language: Language
)

interface ArticleContentExtractor {
    fun extract(url: String): ExtractedArticle?
}
