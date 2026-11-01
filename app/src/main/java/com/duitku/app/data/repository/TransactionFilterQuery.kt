package com.duitku.app.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.duitku.app.domain.model.TransactionFilter

/**
 * Membangun SQL pencarian transaksi dari [TransactionFilter].
 * Seluruh nilai dari pengguna diikat sebagai argumen (?), tidak pernah disambung ke string SQL.
 */
object TransactionFilterQuery {

    data class Built(val sql: String, val args: List<Any>)

    fun build(filter: TransactionFilter, ascending: Boolean = false): Built {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        val keyword = filter.query.trim()
        if (keyword.isNotEmpty()) {
            val like = "%" + escapeLike(keyword) + "%"
            val clauses = mutableListOf(
                "note LIKE ? ESCAPE '\\'",
                "tags LIKE ? ESCAPE '\\'",
                "categoryId IN (SELECT id FROM categories WHERE name LIKE ? ESCAPE '\\')",
                "subCategoryId IN (SELECT id FROM categories WHERE name LIKE ? ESCAPE '\\')",
                "walletId IN (SELECT id FROM wallets WHERE name LIKE ? ESCAPE '\\')",
            )
            repeat(5) { args += like }
            val numeric = keyword.filter { it.isDigit() }.toLongOrNull()
            if (numeric != null && keyword.all { it.isDigit() || it == '.' || it == ' ' }) {
                clauses += "amount = ?"
                args += numeric
            }
            where += clauses.joinToString(" OR ", prefix = "(", postfix = ")")
        }

        filter.fromMillis?.let { where += "occurredAt >= ?"; args += it }
        filter.toMillis?.let { where += "occurredAt <= ?"; args += it }

        if (filter.types.isNotEmpty()) {
            where += "type IN (${placeholders(filter.types.size)})"
            args.addAll(filter.types.map { it.name })
        }
        if (filter.walletIds.isNotEmpty()) {
            val ph = placeholders(filter.walletIds.size)
            where += "(walletId IN ($ph) OR toWalletId IN ($ph))"
            args.addAll(filter.walletIds)
            args.addAll(filter.walletIds)
        }
        if (filter.categoryIds.isNotEmpty()) {
            val ph = placeholders(filter.categoryIds.size)
            where += "(categoryId IN ($ph) OR subCategoryId IN ($ph))"
            args.addAll(filter.categoryIds)
            args.addAll(filter.categoryIds)
        }
        filter.tags.forEach { tag ->
            where += "tags LIKE ? ESCAPE '\\'"
            args += "%|" + escapeLike(tag) + "|%"
        }
        filter.minAmount?.let { where += "amount >= ?"; args += it }
        filter.maxAmount?.let { where += "amount <= ?"; args += it }

        val direction = if (ascending) "ASC" else "DESC"
        val sql = buildString {
            append("SELECT * FROM transactions")
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY occurredAt $direction, id $direction")
        }
        return Built(sql, args)
    }

    fun toSupportQuery(filter: TransactionFilter, ascending: Boolean = false): SupportSQLiteQuery {
        val built = build(filter, ascending)
        return SimpleSQLiteQuery(built.sql, built.args.toTypedArray())
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
