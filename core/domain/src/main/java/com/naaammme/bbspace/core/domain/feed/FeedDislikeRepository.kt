package com.naaammme.bbspace.core.domain.feed

import com.naaammme.bbspace.core.model.FeedDislikeContext
import com.naaammme.bbspace.core.model.ThreePointReason

data class FeedActionResult(
    val toast: String,
    val removed: Boolean = false
)

interface FeedDislikeRepository {
    suspend fun dislike(context: FeedDislikeContext, reason: ThreePointReason): FeedActionResult
    suspend fun cancelDislike(context: FeedDislikeContext): FeedActionResult
}
