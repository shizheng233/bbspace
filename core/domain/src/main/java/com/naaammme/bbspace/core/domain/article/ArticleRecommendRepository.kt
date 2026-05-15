package com.naaammme.bbspace.core.domain.article

import com.naaammme.bbspace.core.model.article.ArticleRecommendPage

interface ArticleRecommendRepository {
    suspend fun fetchHomePage(
        page: Int,
        aids: String?
    ): ArticleRecommendPage
}
