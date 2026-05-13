package com.naaammme.bbspace.core.domain

import com.naaammme.bbspace.core.model.ImPage
import com.naaammme.bbspace.core.model.ImPaginationParams
import com.naaammme.bbspace.core.model.ImSessionTab

interface ImRepository {
    suspend fun fetchSessions(
        tab: ImSessionTab = ImSessionTab.DEFAULT,
        paginationParams: ImPaginationParams? = null
    ): ImPage
}
