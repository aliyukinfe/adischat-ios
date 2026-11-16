package com.adischat.app.feature.shop.presentation

internal const val SELLER_REGION_ADDIS_ABABA = "Addis Ababa City"

internal val SELLER_BUSINESS_TYPES = listOf(
    "individual" to "Individual",
    "plc" to "PLC",
)

internal val SELLER_ADDIS_ABABA_SUBCITIES = listOf(
    "Addis Ketema",
    "Akaki Kaliti",
    "Arada",
    "Bole",
    "Gulele",
    "Kirkos",
    "Kolfe Keraniyo",
    "Lemi Kura",
    "Lideta",
    "Nifas Silk Lafto",
    "Yeka",
)

internal fun sellerFeaturesUnlocked(status: String): Boolean =
    status in setOf("approved", "suspended")

internal fun validSellerBusinessType(value: String): Boolean =
    SELLER_BUSINESS_TYPES.any { (code, _) -> code == value }

internal fun validSellerTin(value: String): Boolean =
    value.matches(Regex("^\\d{10}$"))

internal fun validSellerSubcity(value: String): Boolean =
    value in SELLER_ADDIS_ABABA_SUBCITIES
