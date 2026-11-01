package com.duitku.app.data.mapper

import com.duitku.app.data.local.entity.CategoryEntity
import com.duitku.app.data.local.entity.TransactionEntity
import com.duitku.app.data.local.entity.WalletEntity
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.Wallet

fun WalletEntity.toDomain() = Wallet(
    id = id,
    name = name,
    type = type,
    initialBalance = initialBalance,
    iconKey = iconKey,
    colorArgb = colorArgb,
    isArchived = isArchived,
    sortOrder = sortOrder,
    openedAt = openedAt,
)

fun Wallet.toEntity(createdAt: Long) = WalletEntity(
    id = id,
    name = name,
    type = type,
    initialBalance = initialBalance,
    iconKey = iconKey,
    colorArgb = colorArgb,
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    openedAt = openedAt,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    kind = kind,
    parentId = parentId,
    iconKey = iconKey,
    colorArgb = colorArgb,
    isDefault = isDefault,
    isArchived = isArchived,
    sortOrder = sortOrder,
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    kind = kind,
    parentId = parentId,
    iconKey = iconKey,
    colorArgb = colorArgb,
    isDefault = isDefault,
    isArchived = isArchived,
    sortOrder = sortOrder,
)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    type = type,
    amount = amount,
    walletId = walletId,
    toWalletId = toWalletId,
    categoryId = categoryId,
    subCategoryId = subCategoryId,
    note = note,
    tags = tags,
    occurredAt = occurredAt,
    recurringId = recurringId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    type = type,
    amount = amount,
    walletId = walletId,
    toWalletId = toWalletId,
    categoryId = categoryId,
    subCategoryId = subCategoryId,
    note = note,
    tags = tags,
    occurredAt = occurredAt,
    recurringId = recurringId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
