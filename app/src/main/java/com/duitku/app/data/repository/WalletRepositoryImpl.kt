package com.duitku.app.data.repository

import com.duitku.app.data.local.dao.TransactionDao
import com.duitku.app.data.local.dao.WalletDao
import com.duitku.app.data.mapper.toDomain
import com.duitku.app.data.mapper.toEntity
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.model.WalletWithBalance
import com.duitku.app.domain.repository.WalletRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val walletDao: WalletDao,
    private val transactionDao: TransactionDao,
) : WalletRepository {

    override fun observeWallets(): Flow<List<Wallet>> =
        walletDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeWalletsWithBalance(): Flow<List<WalletWithBalance>> =
        combine(walletDao.observeAll(), transactionDao.observeWalletBalances()) { wallets, balances ->
            val byId = balances.associate { it.walletId to it.balance }
            wallets.map { entity ->
                val wallet = entity.toDomain()
                WalletWithBalance(wallet, byId[wallet.id] ?: wallet.initialBalance)
            }
        }

    override suspend fun getWallet(id: Long): Wallet? = walletDao.getById(id)?.toDomain()

    override suspend fun count(): Int = walletDao.count()

    override suspend fun save(wallet: Wallet): Long {
        return if (wallet.id == 0L) {
            val order = walletDao.nextSortOrder()
            walletDao.insert(
                wallet.copy(sortOrder = order, openedAt = wallet.openedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
                    .toEntity(createdAt = System.currentTimeMillis()),
            )
        } else {
            val existing = walletDao.getById(wallet.id) ?: error("Dompet tidak ditemukan")
            walletDao.update(wallet.toEntity(createdAt = existing.createdAt))
            wallet.id
        }
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        val existing = walletDao.getById(id) ?: return
        walletDao.update(existing.copy(isArchived = archived))
    }

    override suspend fun transactionCount(id: Long): Int = transactionDao.countForWallet(id)

    override suspend fun delete(id: Long) {
        val existing = walletDao.getById(id) ?: return
        walletDao.delete(existing)
    }
}
