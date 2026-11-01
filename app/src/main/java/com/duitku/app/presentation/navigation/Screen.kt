package com.duitku.app.presentation.navigation

/** Seluruh rute aplikasi. Argumen ditulis sebagai query agar opsional. */
sealed class Screen(val route: String) {
    // Alur awal
    data object Onboarding : Screen("onboarding")

    // Tab utama (bottom navigation)
    data object Home : Screen("home")
    data object Statement : Screen("statement")
    data object Budget : Screen("budget")
    data object Stats : Screen("stats")
    data object More : Screen("more")

    // Transaksi
    data object TransactionEdit : Screen("transaction/edit?id={id}&type={type}") {
        const val ARG_ID = "id"
        const val ARG_TYPE = "type"
        fun create(id: Long? = null, type: String? = null): String =
            "transaction/edit?id=${id ?: -1L}&type=${type.orEmpty()}"
    }

    data object TransactionSearch : Screen("transaction/search")

    // Dompet & kategori
    data object Wallets : Screen("wallets")
    data object WalletEdit : Screen("wallets/edit?id={id}") {
        const val ARG_ID = "id"
        fun create(id: Long? = null): String = "wallets/edit?id=${id ?: -1L}"
    }

    data object Categories : Screen("categories")
    data object CategoryEdit : Screen("categories/edit?id={id}&parentId={parentId}&kind={kind}") {
        const val ARG_ID = "id"
        const val ARG_PARENT = "parentId"
        const val ARG_KIND = "kind"
        fun create(id: Long? = null, parentId: Long? = null, kind: String = "EXPENSE"): String =
            "categories/edit?id=${id ?: -1L}&parentId=${parentId ?: -1L}&kind=$kind"
    }

    // Arsip mutasi & ekspor
    data object Archive : Screen("archive")
    data object StatementDetail : Screen("archive/detail/{periodYm}") {
        const val ARG_PERIOD = "periodYm"
        fun create(periodYm: Int): String = "archive/detail/$periodYm"
    }

    data object ExportCenter : Screen("export?periodYm={periodYm}") {
        const val ARG_PERIOD = "periodYm"
        fun create(periodYm: Int = -1): String = "export?periodYm=$periodYm"
    }

    data object ExportHistory : Screen("export/history")
    data object PdfPreview : Screen("export/preview/{exportId}") {
        const val ARG_ID = "exportId"
        fun create(exportId: Long): String = "export/preview/$exportId"
    }

    // Perencanaan
    data object Recurring : Screen("recurring")
    data object RecurringEdit : Screen("recurring/edit?id={id}") {
        const val ARG_ID = "id"
        fun create(id: Long? = null): String = "recurring/edit?id=${id ?: -1L}"
    }

    data object Goals : Screen("goals")
    data object Debts : Screen("debts")

    // Pengaturan & data
    data object Settings : Screen("settings")
    data object Backup : Screen("backup")
    data object Retention : Screen("retention")
    data object AuditLog : Screen("audit-log")

    companion object {
        /** Rute tab utama yang menampilkan bottom navigation. */
        val topLevelRoutes: Set<String> = setOf(Home.route, Statement.route, Budget.route, Stats.route, More.route)

        /** Rute yang menampilkan tombol FAB "Tambah transaksi". */
        val fabRoutes: Set<String> = setOf(Home.route, Statement.route)
    }
}
