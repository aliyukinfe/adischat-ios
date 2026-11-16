package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopSellerOptionValueInput
import com.adischat.app.feature.shop.data.ShopSellerProductOptionInput
import com.adischat.app.feature.shop.data.ShopSellerSpecificationInput
import com.adischat.app.feature.shop.data.ShopSellerVariantInput
import org.junit.Assert.assertEquals
import org.junit.Test

class SellerVariantDraftPersistenceTest {
    @Test fun deletedProductImageCannotRemainInVariantPayload() {
        val variant = ShopSellerVariantInput(
            title = "Default", options = emptyMap(), priceMinor = 10000, quantityAvailable = 2,
            imageIds = listOf("removed-image", "current-image"),
        )
        assertEquals(
            listOf(variant.copy(imageIds = listOf("current-image"))),
            removeUnavailableVariantImages(listOf(variant), setOf("current-image")),
        )
        assertEquals(
            emptyList<String>(),
            removeUnavailableVariantImages(listOf(variant), emptySet()).single().imageIds,
        )
    }

    @Test fun singleDefaultVariantUsesBasePriceAndStockWithoutLosingIdentity() {
        val default = ShopSellerVariantInput(
            id = "existing-default", title = "Default", sku = "QA-SKU",
            options = emptyMap(), priceMinor = 0, quantityAvailable = 0,
            imageIds = listOf("image-one"),
        )
        assertEquals(
            listOf(default.copy(priceMinor = 10000, quantityAvailable = 2)),
            syncSingleDefaultVariant(emptyList(), listOf(default), 10000, 2),
        )
    }

    @Test fun selectableVariantsKeepTheirOwnPricesAndStock() {
        val option = ShopSellerProductOptionInput("COLOR", "Color", 0,
            listOf(ShopSellerOptionValueInput("BLACK", "Black", "Black")))
        val black = ShopSellerVariantInput(
            title = "Black", options = mapOf("COLOR" to "BLACK"),
            priceMinor = 120000, quantityAvailable = 4,
        )
        assertEquals(listOf(black), syncSingleDefaultVariant(listOf(option), listOf(black), 10000, 2))
        assertEquals(emptyList<ShopSellerVariantInput>(), syncSingleDefaultVariant(emptyList(), emptyList(), 10000, 2))
    }

    @Test fun snapshotPreservesOptionsSkuPriceStockAndUncommittedInput() {
        val snapshot = SellerVariantEditorSnapshot(
            specificationText = mapOf("MODEL" to "X100"),
            optionValues = mapOf("COLOR" to listOf("Black", "Blue")),
            optionDrafts = mapOf("COLOR" to "Green"),
            enabledOptions = mapOf("COLOR" to true),
            variantDrafts = mapOf("COLOR=BLACK" to SellerVariantDraft(
                title = "Black edition", sku = "X100-BLK", price = "1200", stock = "4",
                active = true, compareAtPriceMinor = 140000, imageIds = listOf("image-one"),
            )),
        )
        val saved = putSellerVariantSnapshot("{}", "phones", snapshot)
        assertEquals(snapshot, decodeSellerVariantSnapshot(decodeSellerVariantSnapshots(saved)["phones"]))
        assertEquals(emptyMap<String, String>(), decodeSellerVariantSnapshots("invalid"))
    }

    @Test fun categorySnapshotsStaySeparateAndPayloadRoundTrips() {
        val first = SellerVariantEditorSnapshot(specificationText = mapOf("MODEL" to "X100"))
        val second = SellerVariantEditorSnapshot(optionValues = mapOf("SIZE" to listOf("M")))
        val saved = putSellerVariantSnapshot(putSellerVariantSnapshot("{}", "phones", first), "shirts", second)
        assertEquals(first, decodeSellerVariantSnapshot(decodeSellerVariantSnapshots(saved)["phones"]))
        assertEquals(second, decodeSellerVariantSnapshot(decodeSellerVariantSnapshots(saved)["shirts"]))

        val outputs = SellerVariantOutputs(
            specifications = listOf(ShopSellerSpecificationInput("MODEL", valueText = "X100")),
            options = listOf(ShopSellerProductOptionInput("COLOR", "Color", 0,
                listOf(ShopSellerOptionValueInput("BLACK", "Black", "Black")))),
            variants = listOf(ShopSellerVariantInput(title = "Black", sku = "X100-BLK",
                options = mapOf("COLOR" to "BLACK"), priceMinor = 120000, quantityAvailable = 4)),
        )
        assertEquals(outputs, decodeSellerVariantOutputs(encodeSellerVariantOutputs(outputs)))
        assertEquals(true, hasSellerCategoryDraft(encodeSellerVariantOutputs(outputs)))
        assertEquals(false, hasSellerCategoryDraft(""))
        assertEquals(true, hasSellerCategoryDraft("", SellerVariantEditorSnapshot(
            variantDrafts = mapOf("COLOR=BLACK" to SellerVariantDraft(sku = "UNSAVED")),
        )))
        assertEquals(false, hasSellerCategoryDraft(encodeSellerVariantOutputs(SellerVariantOutputs(
            variants = listOf(ShopSellerVariantInput(title = "Default", options = emptyMap(), priceMinor = 0, quantityAvailable = 0)),
        ))))
        assertEquals(SellerVariantOutputs(), decodeSellerVariantOutputs("invalid"))
    }
}
