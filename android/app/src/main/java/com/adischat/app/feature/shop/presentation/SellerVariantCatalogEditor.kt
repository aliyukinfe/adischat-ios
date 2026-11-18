package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.Alignment
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopCategoryAttributeDto
import com.adischat.app.feature.shop.data.ShopProductImageDto
import com.adischat.app.feature.shop.data.ShopSellerOptionValueInput
import com.adischat.app.feature.shop.data.ShopSellerProductDto
import com.adischat.app.feature.shop.data.ShopSellerProductOptionInput
import com.adischat.app.feature.shop.data.ShopSellerSpecificationInput
import com.adischat.app.feature.shop.data.ShopSellerVariantInput
import com.adischat.app.ui.theme.AdisGebeyaColors
import coil.compose.AsyncImage
import com.adischat.app.feature.shop.data.SellerVariantDraft
import com.adischat.app.feature.shop.data.SellerVariantEditorSnapshot
import com.adischat.app.feature.shop.data.SellerVariantOutputs
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

internal fun variantValueCode(value: String): String = value.trim().uppercase()
    .replace(Regex("[^A-Z0-9]+"), "_").trim('_').take(64).ifBlank { "VALUE" }

internal object CategoryVariantPresets {
    val SHOE_EU_SIZES = listOf("36", "37", "38", "39", "40", "41", "42", "43", "44", "45", "46")
    val KIDS_SHOE_SIZES = listOf("28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38")
    val TROUSER_SIZES = listOf(
        "28", "30", "32", "34", "36", "38", "40", "42",
        "30/30", "32/30", "32/32", "34/32", "36/32",
    )
    val APPAREL_ALPHA_SIZES = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
    val FURNITURE_SIZES = listOf("Single", "Double", "Queen", "King", "Custom")

    val KIDS_SHOE_CATEGORY_IDS = setOf(
        "2ba3dd18-474b-4544-8ea1-c1b3a3e97279", // KIDS_SHOES
    )
    val FOOTWEAR_CATEGORY_IDS = setOf(
        "b9b2956d-425b-4049-850e-9b2a88125acc", // MENS_SHOES
        "08b0d893-223c-42c5-86cd-1e01e07bc82b", // WOMENS_SHOES
        "83af6f95-e949-489e-8a11-f6d68602e1b1", // SPORTS_SHOES
        "586ab99f-66fc-4c03-8464-ee2f902eccdd", // SHOES_BAGS_ACCESSORIES
    )
    val TROUSER_CATEGORY_IDS = setOf(
        "7a56e0f2-2b60-4543-8a52-512e969cc253", // TROUSERS
    )
    val APPAREL_CATEGORY_IDS = setOf(
        "db8c93f5-e3a0-46eb-8778-713d4c9b23c3", // JACKETS
        "4b75f4e5-3b88-46e2-8069-33082443228c", // SHIRTS
        "68f28559-2640-4b65-84d8-e36532c82931", // T_SHIRTS
        "9035a12e-ad49-455f-82b8-9ffaf02655ca", // DRESSES
        "679f286d-3bdb-48ea-8a0f-593fdca66c50", // MENS_CLOTHING
        "c185bdc9-f022-44de-8fa7-6158e177b070", // WOMENS_CLOTHING
        "00000000-0000-4000-8000-000000000102", // FASHION_CLOTHING
    )
    val FURNITURE_CATEGORY_IDS = setOf(
        "97385dba-3aaf-4fcd-8bf7-6cd7f54e8e6f", // FURNITURE
        "5abfc134-cb63-4006-83a4-bf8940695771", // BEDDING
        "00000000-0000-4000-8000-000000000103", // HOME_FURNITURE_GARDEN
    )

    fun resolveSuggestions(
        categoryId: String,
        categoryCode: String?,
        attributeCode: String,
        defaultSuggestions: List<String>,
    ): List<String> {
        val attr = attributeCode.trim().uppercase()
        if (attr != "SIZE" && attr != "SHOE_SIZE" && attr != "CLOTHING_SIZE" && attr != "DIMENSIONS") {
            return defaultSuggestions
        }
        val id = categoryId.trim().lowercase()
        val code = categoryCode?.trim()?.uppercase().orEmpty()

        return when {
            id in KIDS_SHOE_CATEGORY_IDS || code == "KIDS_SHOES" || code.contains("KIDS_SHOE") -> KIDS_SHOE_SIZES
            id in FOOTWEAR_CATEGORY_IDS || code.contains("SHOE") || code.contains("FOOTWEAR") || attr == "SHOE_SIZE" -> SHOE_EU_SIZES
            id in TROUSER_CATEGORY_IDS || code == "TROUSERS" || code.contains("PANTS") || code.contains("JEANS") -> TROUSER_SIZES
            id in FURNITURE_CATEGORY_IDS || code.contains("FURNITURE") || code.contains("BEDDING") || code.contains("MATTRESS") -> FURNITURE_SIZES
            id in APPAREL_CATEGORY_IDS || code.contains("CLOTHING") || code.contains("JACKET") || code.contains("SHIRT") || code.contains("DRESS") || code.contains("APPAREL") || attr == "CLOTHING_SIZE" -> APPAREL_ALPHA_SIZES
            defaultSuggestions.isNotEmpty() -> defaultSuggestions
            else -> APPAREL_ALPHA_SIZES
        }
    }
}

internal fun generateVariantCombinations(options: List<ShopSellerProductOptionInput>): List<Map<String, String>> {
    if (options.isEmpty()) return listOf(emptyMap())
    return options.fold(listOf(emptyMap())) { combinations, option ->
        combinations.flatMap { existing ->
            option.values.map { value -> existing + (option.attributeCode to value.code) }
        }
    }
}

internal fun encodeSellerVariantSnapshot(snapshot: SellerVariantEditorSnapshot): String = Gson().toJson(snapshot)

internal fun decodeSellerVariantSnapshot(json: String?): SellerVariantEditorSnapshot? =
    json?.takeIf { it.isNotBlank() }?.let {
        runCatching {
            Gson().fromJson<SellerVariantEditorSnapshot>(it, object : TypeToken<SellerVariantEditorSnapshot>() {}.type)
        }.getOrNull()
    }

internal fun decodeSellerVariantSnapshots(json: String): Map<String, String> =
    if (json.isBlank()) emptyMap()
    else runCatching {
        Gson().fromJson<Map<String, String>>(json, object : TypeToken<Map<String, String>>() {}.type).orEmpty()
    }.getOrDefault(emptyMap())

internal fun putSellerVariantSnapshot(json: String, categoryId: String, snapshot: SellerVariantEditorSnapshot): String =
    Gson().toJson(decodeSellerVariantSnapshots(json) + (categoryId to encodeSellerVariantSnapshot(snapshot)))

internal fun encodeSellerVariantOutputs(outputs: SellerVariantOutputs): String = Gson().toJson(outputs)

internal fun decodeSellerVariantOutputs(json: String): SellerVariantOutputs {
    if (json.isBlank()) return SellerVariantOutputs()
    return try {
        val root = JsonParser.parseString(json).asJsonObject
        val specs = runCatching {
            root.getAsJsonArray("specifications")?.mapNotNull { el ->
                Gson().fromJson(el, ShopSellerSpecificationInput::class.java)
            }
        }.getOrNull().orEmpty()
        val options = runCatching {
            root.getAsJsonArray("options")?.mapNotNull { el ->
                Gson().fromJson(el, ShopSellerProductOptionInput::class.java)
            }
        }.getOrNull().orEmpty()
        val variants = runCatching {
            root.getAsJsonArray("variants")?.mapNotNull { el ->
                Gson().fromJson(el, ShopSellerVariantInput::class.java)
            }
        }.getOrNull().orEmpty()
        SellerVariantOutputs(specifications = specs, options = options, variants = variants)
    } catch (_: Exception) {
        runCatching {
            Gson().fromJson<SellerVariantOutputs>(json, object : TypeToken<SellerVariantOutputs>() {}.type)
        }.getOrNull() ?: SellerVariantOutputs()
    }
}

internal fun hasSellerCategoryDraft(json: String, snapshot: SellerVariantEditorSnapshot? = null): Boolean {
    val outputs = decodeSellerVariantOutputs(json)
    return outputs.specifications.isNotEmpty() || outputs.options.isNotEmpty() || outputs.variants.any { variant ->
        variant.id != null || !variant.sku.isNullOrBlank() || variant.priceMinor > 0 ||
            variant.quantityAvailable > 0 || variant.imageIds.isNotEmpty() ||
            (variant.title.isNotBlank() && variant.title != "Default")
    } || snapshot?.let { draft ->
        draft.specificationText.values.any { it.isNotBlank() } ||
            draft.optionValues.values.any { it.isNotEmpty() } ||
            draft.optionDrafts.values.any { it.isNotBlank() } ||
            draft.variantDrafts.values.any { row ->
                row.id != null || row.title.isNotBlank() || row.sku.isNotBlank() ||
                    row.price.isNotBlank() || row.stock != "0" || row.imageIds.isNotEmpty()
            }
    } == true
}

internal fun shouldPersistSellerVariant(existingId: String?, active: Boolean): Boolean =
    existingId != null || active

internal fun removeUnavailableVariantImages(
    variants: List<ShopSellerVariantInput>,
    availableImageIds: Set<String>,
): List<ShopSellerVariantInput> = variants.map { variant ->
    variant.copy(imageIds = variant.imageIds.filter { it in availableImageIds })
}

internal fun syncSingleDefaultVariant(
    options: List<ShopSellerProductOptionInput>,
    variants: List<ShopSellerVariantInput>,
    basePriceMinor: Int,
    baseStock: Int,
): List<ShopSellerVariantInput> =
    if (options.isEmpty() && variants.size == 1 && variants[0].options.isEmpty()) {
        listOf(variants[0].copy(priceMinor = basePriceMinor, quantityAvailable = baseStock))
    } else variants

internal fun generateSuggestedSku(brand: String?, title: String, combination: Map<String, String>): String {
    val prefix = (brand?.takeIf { it.isNotBlank() } ?: title.take(8)).trim()
        .uppercase().replace(Regex("[^A-Z0-9]+"), "")
    val suffix = combination.values.joinToString("-") { it.uppercase().replace(Regex("[^A-Z0-9]+"), "") }
    return if (prefix.isNotBlank() && suffix.isNotBlank()) "$prefix-$suffix"
    else if (suffix.isNotBlank()) suffix
    else prefix.ifBlank { "SKU" }
}

internal fun findDuplicateSkus(drafts: Map<String, SellerVariantDraft>): Set<String> {
    val activeSkus = drafts.values
        .filter { it.active }
        .mapNotNull { it.sku.trim().takeIf(String::isNotBlank)?.uppercase() }
    val counts = activeSkus.groupingBy { it }.eachCount()
    return counts.filter { it.value > 1 }.keys
}

internal fun findDuplicateSkusFromOutputs(json: String): Set<String> {
    if (json.isBlank()) return emptySet()
    val outputs = decodeSellerVariantOutputs(json)
    val activeSkus = outputs.variants
        .filter { it.status == "active" }
        .mapNotNull { it.sku?.trim()?.takeIf(String::isNotBlank)?.uppercase() }
    val counts = activeSkus.groupingBy { it }.eachCount()
    return counts.filter { it.value > 1 }.keys
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SellerVariantCatalogEditor(
    product: ShopSellerProductDto,
    categoryId: String,
    attributes: List<ShopCategoryAttributeDto>,
    productImages: List<ShopProductImageDto>,
    baseStock: Int,
    savedSnapshot: SellerVariantEditorSnapshot?,
    onSnapshotChanged: (SellerVariantEditorSnapshot) -> Unit,
    onChanged: (List<ShopSellerSpecificationInput>, List<ShopSellerProductOptionInput>, List<ShopSellerVariantInput>) -> Unit,
    modifier: Modifier = Modifier,
    categoryCode: String? = null,
    basePriceMinor: Int = product.priceMinor,
) {
    val specificationText = remember(product.id, categoryId, attributes) {
        mutableStateMapOf<String, String>().apply {
            savedSnapshot?.specificationText?.let { putAll(it) } ?: product.specificationValues.forEach { value ->
                put(value.attributeCode, value.valueText ?: value.valueNumber?.toString() ?: value.valueBoolean?.toString().orEmpty())
            }
        }
    }
    val optionValues = remember(product.id, categoryId, attributes) {
        mutableStateMapOf<String, List<String>>().apply {
            savedSnapshot?.optionValues?.let { putAll(it) } ?: product.productOptions.forEach { option ->
                put(option.attributeCode, option.values.map { it.displayValue }.filter { it.isNotBlank() })
            }
        }
    }
    val optionDrafts = remember(product.id, categoryId, attributes) {
        mutableStateMapOf<String, String>().apply { savedSnapshot?.optionDrafts?.let { putAll(it) } }
    }
    val enabledOptions = remember(product.id, categoryId, attributes) {
        mutableStateMapOf<String, Boolean>().apply {
            savedSnapshot?.enabledOptions?.let { putAll(it) } ?: product.productOptions.forEach { put(it.attributeCode, true) }
        }
    }
    val optionColors = remember(product.id, product.productOptions) {
        product.productOptions.associate { option ->
            option.attributeCode to option.values.associate { value ->
                variantValueCode(value.displayValue) to value.colorHex
            }
        }
    }
    val variantDrafts = remember(product.id, categoryId, attributes) {
        mutableStateMapOf<String, SellerVariantDraft>().apply {
            savedSnapshot?.variantDrafts?.let { putAll(it) } ?: product.variants.forEach { variant ->
                val signature = variant.options.orEmpty().entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${variantValueCode(it.value.toString())}" }
                put(signature, SellerVariantDraft(
                    id = variant.id,
                    title = variant.title,
                    sku = variant.sku.orEmpty(),
                    price = (variant.priceMinor / 100).toString(),
                    stock = variant.inventory?.quantityAvailable?.toString() ?: "0",
                    active = variant.status == "active",
                    compareAtPriceMinor = variant.compareAtPriceMinor,
                    imageIds = variant.imageIds,
                ))
            }
        }
    }

    fun snapshot() = SellerVariantEditorSnapshot(
        specificationText = specificationText.toMap(),
        optionValues = optionValues.toMap(),
        optionDrafts = optionDrafts.toMap(),
        enabledOptions = enabledOptions.toMap(),
        variantDrafts = variantDrafts.toMap(),
    )

    fun outputs(includeUnselectedCandidates: Boolean = false): Triple<List<ShopSellerSpecificationInput>, List<ShopSellerProductOptionInput>, List<ShopSellerVariantInput>> {
        val sections = sellerAttributeSections(attributes)
        val specs = (sections.specifications + sections.conditionAndDisclosures).mapNotNull { attribute ->
            specificationText[attribute.code]?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
                when (attribute.dataType) {
                    "number" -> value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
                        ShopSellerSpecificationInput(attribute.code, valueNumber = it, unit = attribute.unit)
                    }
                    "boolean" -> ShopSellerSpecificationInput(attribute.code, valueBoolean = value == "true", unit = attribute.unit)
                    else -> ShopSellerSpecificationInput(attribute.code, valueText = value, unit = attribute.unit)
                }
            }
        }
        val options = sections.options.filter { enabledOptions[it.code] == true }.mapNotNull { attribute ->
            val values = optionValues[attribute.code].orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
            values.takeIf { it.isNotEmpty() }?.let {
                ShopSellerProductOptionInput(
                    attributeCode = attribute.code,
                    name = attribute.displayName,
                    position = attribute.sortOrder,
                    values = it.mapIndexed { index, value ->
                        ShopSellerOptionValueInput(
                            code = variantValueCode(value),
                            value = value,
                            displayValue = value,
                            colorHex = sellerOptionColorHex(attribute.code, value, optionColors[attribute.code].orEmpty()),
                            position = index,
                        )
                    },
                )
            }
        }
        val combinations = generateVariantCombinations(options)
        val displayValues = options.associate { option -> option.attributeCode to option.values.associate { it.code to it.displayValue } }
        val variants = combinations.mapNotNull { combination ->
            val signature = combination.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
            val previous = variantDrafts[signature] ?: SellerVariantDraft(
                price = (product.priceMinor / 100).takeIf { it > 0 }?.toString().orEmpty(),
                active = combination.isEmpty(),
            )
            variantDrafts.putIfAbsent(signature, previous)
            if (!includeUnselectedCandidates && !shouldPersistSellerVariant(previous.id, previous.active)) return@mapNotNull null
            ShopSellerVariantInput(
                id = previous.id,
                title = previous.title.trim().ifBlank {
                    if (combination.isEmpty()) "Default" else combination.entries.sortedBy { it.key }.joinToString(" / ") { displayValues[it.key]?.get(it.value) ?: it.value }
                },
                sku = previous.sku.trim().takeIf { it.isNotBlank() },
                options = combination,
                priceMinor = (previous.price.toIntOrNull() ?: 0) * 100,
                compareAtPriceMinor = previous.compareAtPriceMinor,
                status = if (previous.active) "active" else "paused",
                quantityAvailable = previous.stock.toIntOrNull() ?: 0,
                imageIds = previous.imageIds.orEmpty().filter { id -> productImages.any { it.id == id } },
            )
        }
        return Triple(specs, options, variants)
    }

    fun emit() {
        onSnapshotChanged(snapshot())
        outputs().let { onChanged(it.first, it.second, it.third) }
    }
    LaunchedEffect(product.id, categoryId, attributes) { emit() }

    val sections = sellerAttributeSections(attributes)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (sections.specifications.isNotEmpty()) {
            Text(
                if (attributes.any { it.code in setOf("SCREEN_SIZE", "PROCESSOR", "NETWORK", "OPERATING_SYSTEM", "CONDITION") }) "Device Specifications" else "Category details",
                color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            )
            SellerSpecificationFields(sections.specifications, specificationText) { code, value ->
                specificationText[code] = value
                emit()
            }
        }

        if (sections.options.isNotEmpty()) {
            Text("Options", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sections.options, key = { it.code }) { attribute ->
                    FilterChip(
                        selected = enabledOptions[attribute.code] == true,
                        onClick = { enabledOptions[attribute.code] = enabledOptions[attribute.code] != true; emit() },
                        label = { Text(attribute.displayName) },
                    )
                }
            }
            sections.options.filter { enabledOptions[it.code] == true }.forEach { attribute ->
                val selected = optionValues[attribute.code].orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(attribute.displayName, color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold)
                    if (selected.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            selected.forEach { value ->
                                InputChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(value) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                optionValues[attribute.code] = selected.filterNot { it.equals(value, ignoreCase = true) }
                                                emit()
                                            },
                                        ) { Icon(Icons.Default.Close, contentDescription = "Remove $value") }
                                    },
                                )
                            }
                        }
                    }
                    val effectiveSuggestions = CategoryVariantPresets.resolveSuggestions(
                        categoryId = categoryId,
                        categoryCode = categoryCode,
                        attributeCode = attribute.code,
                        defaultSuggestions = attribute.suggestions,
                    )
                    if (effectiveSuggestions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(effectiveSuggestions.filterNot { suggestion -> selected.any { it.equals(suggestion, true) } }, key = { it }) { suggestion ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        optionValues[attribute.code] = selected + suggestion
                                        emit()
                                    },
                                    label = { Text(suggestion) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = optionDrafts[attribute.code].orEmpty(),
                        onValueChange = { optionDrafts[attribute.code] = it; onSnapshotChanged(snapshot()) },
                        label = { Text("Add ${attribute.displayName.lowercase()}") },
                        supportingText = { Text("Add one stocked value at a time") },
                        trailingIcon = {
                            IconButton(onClick = {
                                val value = optionDrafts[attribute.code].orEmpty().trim()
                                if (value.isNotBlank() && selected.none { it.equals(value, true) }) {
                                    optionValues[attribute.code] = selected + value
                                    optionDrafts[attribute.code] = ""
                                    emit()
                                }
                            }) { Icon(Icons.Default.Add, contentDescription = "Add ${attribute.displayName}") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }

        val duplicateSkus = remember(variantDrafts.values.map { it.sku to it.active }) {
            findDuplicateSkus(variantDrafts)
        }
        val current = outputs(includeUnselectedCandidates = true)
        if (current.third.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Variant inventory (${current.third.count { variantDrafts[it.options.entries.sortedBy { e -> e.key }.joinToString("|") { e -> "${e.key}=${e.value}" }]?.active == true }} active / ${current.third.size} total)",
                    color = AdisGebeyaColors.PrimaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }
            if (current.third.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        OutlinedButton(
                            onClick = {
                                current.third.forEach { variant ->
                                    val sig = variant.options.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
                                    val draft = variantDrafts[sig] ?: SellerVariantDraft()
                                    if (draft.sku.isBlank()) {
                                        val suggested = generateSuggestedSku(product.brand, product.title, variant.options)
                                        variantDrafts[sig] = draft.copy(sku = suggested)
                                    }
                                }
                                emit()
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp), tint = AdisGebeyaColors.Primary)
                            Spacer(Modifier.width(5.dp))
                            Text("Auto-fill SKUs", fontSize = 12.sp)
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                val basePriceStr = (basePriceMinor / 100).takeIf { it > 0 }?.toString().orEmpty()
                                if (basePriceStr.isNotBlank()) {
                                    current.third.forEach { variant ->
                                        val sig = variant.options.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
                                        val draft = variantDrafts[sig] ?: SellerVariantDraft()
                                        if (draft.price.isBlank() || draft.price == "0") {
                                            variantDrafts[sig] = draft.copy(price = basePriceStr)
                                        }
                                    }
                                    emit()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Apply base price", fontSize = 12.sp)
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                val baseStockStr = baseStock.takeIf { it > 0 }?.toString().orEmpty()
                                if (baseStockStr.isNotBlank()) {
                                    current.third.forEach { variant ->
                                        val sig = variant.options.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
                                        val draft = variantDrafts[sig] ?: SellerVariantDraft()
                                        if (draft.stock.isBlank() || draft.stock == "0") {
                                            variantDrafts[sig] = draft.copy(stock = baseStockStr)
                                        }
                                    }
                                    emit()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Apply base stock", fontSize = 12.sp)
                        }
                    }
                    item {
                        val allActive = current.third.all { variant ->
                            val sig = variant.options.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
                            variantDrafts[sig]?.active == true
                        }
                        OutlinedButton(
                            onClick = {
                                current.third.forEach { variant ->
                                    val sig = variant.options.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
                                    val draft = variantDrafts[sig] ?: SellerVariantDraft()
                                    variantDrafts[sig] = draft.copy(active = !allActive)
                                }
                                emit()
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(if (allActive) "Deactivate all" else "Activate all", fontSize = 12.sp)
                        }
                    }
                }
            }
            if (duplicateSkus.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AdisGebeyaColors.ErrorSoft),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AdisGebeyaColors.Error.copy(alpha = 0.5f)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AdisGebeyaColors.Error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Duplicate SKU detected (${duplicateSkus.joinToString(", ")}). Each active variant must have a unique SKU.",
                            color = AdisGebeyaColors.Error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            current.third.forEach { variant ->
                val signature = variant.options.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
                val draft = variantDrafts[signature] ?: SellerVariantDraft()
                Card(
                    colors = CardDefaults.cardColors(containerColor = AdisGebeyaColors.SecondarySurface),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(variant.title, color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold)
                            Row {
                                Checkbox(draft.active, { checked -> variantDrafts[signature] = draft.copy(active = checked); emit() })
                                Text("Active", modifier = Modifier.padding(top = 12.dp), fontSize = 12.sp)
                            }
                        }
                        OutlinedTextField(draft.title, { value -> variantDrafts[signature] = draft.copy(title = value); emit() }, label = { Text("Variant name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (current.second.isEmpty() && variant.options.isEmpty()) {
                            Text("Price and stock use the product fields above.", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(draft.price, { value -> variantDrafts[signature] = draft.copy(price = value.filter(Char::isDigit)); emit() }, label = { Text("Price ETB") }, modifier = Modifier.weight(1f), singleLine = true)
                                OutlinedTextField(draft.stock, { value -> variantDrafts[signature] = draft.copy(stock = value.filter(Char::isDigit)); emit() }, label = { Text("Stock") }, modifier = Modifier.weight(1f), singleLine = true)
                            }
                        }
                        val isDuplicateSku = draft.active && draft.sku.trim().uppercase() in duplicateSkus
                        OutlinedTextField(
                            value = draft.sku,
                            onValueChange = { value ->
                                val clean = value.uppercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                                variantDrafts[signature] = draft.copy(sku = clean)
                                emit()
                            },
                            label = { Text("Seller SKU") },
                            isError = isDuplicateSku,
                            supportingText = if (isDuplicateSku) {
                                { Text("Duplicate SKU — must be unique", color = AdisGebeyaColors.Error, fontSize = 11.sp) }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        if (productImages.isNotEmpty()) {
                            Text("Variant images", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.Medium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(productImages, key = { it.id }) { image ->
                                    val selected = image.id in draft.imageIds.orEmpty()
                                    Card(
                                        onClick = {
                                            variantDrafts[signature] = draft.copy(imageIds = if (selected) draft.imageIds.orEmpty() - image.id else draft.imageIds.orEmpty() + image.id)
                                            emit()
                                        },
                                        modifier = Modifier.size(64.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AdisGebeyaColors.Primary else AdisGebeyaColors.Border),
                                    ) {
                                        AsyncImage(
                                            model = image.imageUrl,
                                            contentDescription = if (selected) "Remove selected variant image" else "Select variant image",
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val totalInventory = if (current.second.isEmpty() && current.third.size == 1 && current.third[0].options.isEmpty()) {
                if (current.third[0].status == "active") baseStock else 0
            } else current.third.filter { it.status == "active" }.sumOf { it.quantityAvailable }
            Text("Total inventory: $totalInventory", color = AdisGebeyaColors.Primary, fontWeight = FontWeight.SemiBold)
        }
        if (sections.conditionAndDisclosures.isNotEmpty()) {
            Text("Condition & Disclosures", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            SellerSpecificationFields(sections.conditionAndDisclosures, specificationText) { code, value ->
                specificationText[code] = value
                emit()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SellerSpecificationFields(
    attributes: List<ShopCategoryAttributeDto>,
    values: Map<String, String>,
    onChange: (String, String) -> Unit,
) {
    attributes.forEach { attribute ->
        val value = values[attribute.code].orEmpty()
        val choices = sellerSpecificationChoices(attribute)
        if (attribute.dataType == "boolean" || attribute.code == "CONDITION" || (attribute.dataType == "choice" && choices.isNotEmpty())) {
            Text(attribute.displayName + if (attribute.required) " *" else "", color = AdisGebeyaColors.PrimaryText)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (attribute.dataType == "boolean") listOf("Yes", "No") else choices).forEach { choice ->
                    val wireValue = if (attribute.dataType == "boolean") (choice == "Yes").toString() else choice
                    FilterChip(
                        selected = value == wireValue,
                        onClick = { onChange(attribute.code, if (value == wireValue && !attribute.required) "" else wireValue) },
                        label = { Text(choice) },
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = { onChange(attribute.code, it) },
                label = { Text(attribute.displayName + if (attribute.required) " *" else "") },
                suffix = { attribute.unit?.takeIf { it.isNotBlank() }?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = if (attribute.dataType == "number") KeyboardType.Decimal else KeyboardType.Text),
                isError = sellerSpecificationErrors(listOf(attribute), mapOf(attribute.code to value), false).isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = shopOutlinedTextFieldColors(),
            )
        }
    }
}

internal fun sellerOptionColorHex(attributeCode: String, value: String, existing: Map<String, String?> = emptyMap()): String? {
    val code = variantValueCode(value)
    existing[code]?.takeIf { it.isNotBlank() }?.let { return it }
    if (!attributeCode.equals("COLOR", ignoreCase = true)) return null
    return when (code) {
        "BLACK" -> "#000000"
        "WHITE" -> "#FFFFFF"
        "RED" -> "#E53935"
        "GREY", "GRAY" -> "#808080"
        "BLUE" -> "#1976D2"
        "GREEN" -> "#2E7D32"
        "GOLD" -> "#D4AF37"
        "SILVER" -> "#C0C0C0"
        else -> null
    }
}
