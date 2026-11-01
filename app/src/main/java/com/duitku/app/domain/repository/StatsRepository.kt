package com.duitku.app.domain.repository

import com.duitku.app.domain.model.StatsData
import com.duitku.app.domain.model.StatsRequest
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    suspend fun load(request: StatsRequest): StatsData

    /** Memancarkan [load] ulang setiap kali data transaksi berubah. */
    fun observe(request: StatsRequest): Flow<StatsData>
}
