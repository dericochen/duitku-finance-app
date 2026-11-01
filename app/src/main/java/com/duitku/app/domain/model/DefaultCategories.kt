package com.duitku.app.domain.model

/** Palet warna untuk dompet & kategori (ARGB). */
object Palette {
    val colors: List<Int> = listOf(
        0xFF0B6B4F, 0xFF2E7D32, 0xFF00897B, 0xFF0277BD, 0xFF3949AB, 0xFF6A1B9A,
        0xFFAD1457, 0xFFC62828, 0xFFE65100, 0xFFF9A825, 0xFF6D4C41, 0xFF546E7A,
    ).map { it.toInt() }

    val default: Int get() = colors.first()
}

/** Definisi kategori bawaan beserta subkategorinya. Dipilih pengguna saat onboarding. */
data class DefaultCategoryGroup(
    val name: String,
    val kind: CategoryKind,
    val iconKey: String,
    val colorArgb: Int,
    val subcategories: List<Pair<String, String>>, // nama to iconKey
)

object DefaultCategories {
    private val c = Palette.colors

    val expense: List<DefaultCategoryGroup> = listOf(
        DefaultCategoryGroup(
            "Makanan", CategoryKind.EXPENSE, "food", c[8],
            listOf("Makan di luar" to "food", "Bahan makanan" to "groceries", "Kopi & jajan" to "coffee"),
        ),
        DefaultCategoryGroup(
            "Transport", CategoryKind.EXPENSE, "transport", c[3],
            listOf("Bensin" to "fuel", "Parkir & tol" to "transport", "Angkutan umum" to "bus", "Ojek online" to "motorcycle"),
        ),
        DefaultCategoryGroup(
            "Belanja", CategoryKind.EXPENSE, "shopping", c[6],
            listOf("Pakaian" to "clothes", "Elektronik" to "electronics", "Kebutuhan rumah" to "home"),
        ),
        DefaultCategoryGroup(
            "Tagihan", CategoryKind.EXPENSE, "bill", c[7],
            listOf("Listrik" to "electric", "Air" to "water", "Internet" to "internet", "Pulsa" to "phone", "Sewa/KPR" to "home"),
        ),
        DefaultCategoryGroup(
            "Kesehatan", CategoryKind.EXPENSE, "health", c[2],
            listOf("Dokter & obat" to "health", "Olahraga" to "fitness", "Asuransi" to "insurance"),
        ),
        DefaultCategoryGroup(
            "Pendidikan", CategoryKind.EXPENSE, "education", c[4],
            listOf("Biaya sekolah" to "education", "Buku & kursus" to "book"),
        ),
        DefaultCategoryGroup(
            "Hiburan", CategoryKind.EXPENSE, "entertainment", c[5],
            listOf("Langganan" to "subscription", "Wisata" to "travel", "Game" to "game"),
        ),
        DefaultCategoryGroup(
            "Keluarga", CategoryKind.EXPENSE, "family", c[9],
            listOf("Anak" to "child", "Hewan peliharaan" to "pet", "Hadiah" to "gift"),
        ),
        DefaultCategoryGroup("Cicilan", CategoryKind.EXPENSE, "installment", c[11], emptyList()),
        DefaultCategoryGroup("Donasi", CategoryKind.EXPENSE, "donation", c[1], emptyList()),
        DefaultCategoryGroup("Lain-lain", CategoryKind.EXPENSE, "other", c[11], emptyList()),
    )

    val income: List<DefaultCategoryGroup> = listOf(
        DefaultCategoryGroup("Gaji", CategoryKind.INCOME, "salary", c[0], emptyList()),
        DefaultCategoryGroup("Bonus", CategoryKind.INCOME, "bonus", c[9], emptyList()),
        DefaultCategoryGroup("Usaha", CategoryKind.INCOME, "business", c[8], emptyList()),
        DefaultCategoryGroup("Investasi", CategoryKind.INCOME, "investment", c[3], emptyList()),
        DefaultCategoryGroup("Hadiah", CategoryKind.INCOME, "gift", c[6], emptyList()),
        DefaultCategoryGroup("Lain-lain", CategoryKind.INCOME, "other", c[11], emptyList()),
    )

    val all: List<DefaultCategoryGroup> get() = expense + income
}
