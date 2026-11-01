package com.duitku.app.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Money
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/** Pemetaan kunci ikon (disimpan di database) ke ImageVector. */
object IconCatalog {
    private val icons: Map<String, ImageVector> = linkedMapOf(
        "food" to Icons.Rounded.Restaurant,
        "coffee" to Icons.Rounded.LocalCafe,
        "groceries" to Icons.Rounded.ShoppingCart,
        "transport" to Icons.Rounded.DirectionsCar,
        "fuel" to Icons.Rounded.LocalGasStation,
        "bus" to Icons.Rounded.DirectionsBus,
        "motorcycle" to Icons.Rounded.TwoWheeler,
        "shopping" to Icons.Rounded.ShoppingBag,
        "clothes" to Icons.Rounded.Checkroom,
        "electronics" to Icons.Rounded.Devices,
        "home" to Icons.Rounded.Home,
        "bill" to Icons.Rounded.Receipt,
        "electric" to Icons.Rounded.Bolt,
        "water" to Icons.Rounded.WaterDrop,
        "internet" to Icons.Rounded.Wifi,
        "phone" to Icons.Rounded.PhoneAndroid,
        "health" to Icons.Rounded.LocalHospital,
        "fitness" to Icons.Rounded.FitnessCenter,
        "beauty" to Icons.Rounded.Spa,
        "insurance" to Icons.Rounded.Security,
        "education" to Icons.Rounded.School,
        "book" to Icons.AutoMirrored.Rounded.MenuBook,
        "entertainment" to Icons.Rounded.Movie,
        "game" to Icons.Rounded.SportsEsports,
        "subscription" to Icons.Rounded.Subscriptions,
        "travel" to Icons.Rounded.Flight,
        "family" to Icons.Rounded.FamilyRestroom,
        "child" to Icons.Rounded.ChildCare,
        "pet" to Icons.Rounded.Pets,
        "gift" to Icons.Rounded.CardGiftcard,
        "donation" to Icons.Rounded.VolunteerActivism,
        "installment" to Icons.Rounded.CreditCard,
        "salary" to Icons.Rounded.Payments,
        "bonus" to Icons.Rounded.Stars,
        "business" to Icons.Rounded.Storefront,
        "investment" to Icons.AutoMirrored.Rounded.TrendingUp,
        "savings" to Icons.Rounded.Savings,
        "bank" to Icons.Rounded.AccountBalance,
        "wallet" to Icons.Rounded.AccountBalanceWallet,
        "cash" to Icons.Rounded.Money,
        "ewallet" to Icons.Rounded.PhoneIphone,
        "card" to Icons.Rounded.CreditCard,
        "transfer" to Icons.Rounded.SwapHoriz,
        "other" to Icons.Rounded.MoreHoriz,
    )

    val categoryKeys: List<String> = icons.keys.filter { it != "transfer" }

    val walletKeys: List<String> = listOf("cash", "bank", "ewallet", "card", "savings", "wallet", "investment", "home")

    fun get(key: String): ImageVector = icons[key] ?: Icons.Rounded.Category

    fun defaultIconFor(type: com.duitku.app.domain.model.WalletType): String = when (type) {
        com.duitku.app.domain.model.WalletType.CASH -> "cash"
        com.duitku.app.domain.model.WalletType.BANK -> "bank"
        com.duitku.app.domain.model.WalletType.E_WALLET -> "ewallet"
        com.duitku.app.domain.model.WalletType.CREDIT_CARD -> "card"
        com.duitku.app.domain.model.WalletType.SAVINGS -> "savings"
    }
}
