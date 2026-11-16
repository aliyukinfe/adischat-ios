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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal fun variantValueCode(value: String): String = value.trim().uppercase()
    .replace(Regex("[^A-Z0-9]+"), "_").trim('_').take(64).ifBlank { "VALUE" }

internal fun generateVariantCombinations(options: List<ShopSellerProductOptionInput>): List<Map<String, String>> {
    if (options.isEmpty()) return listOf(emptyMap())
    return options.fold(listOf(emptyMap())) { combinations, option ->
        combinations.flatMap { existing ->
            option.values.map { value -> existing + (option.attributeCode to value.code) }
        }
    }
}

internal data class SellerVariantDraft(
    val id: String? = null,
    val title: String = "",
    val sku: String = "",
    val price: String = "",
    val stock: String = "0",
    val active: Boolean = false,
    val compareAtPriceMinor: Int? = null,
    val imageIds: List<String> = emptyList(),
)

internal data class SellerVariantEditorSnapshot(
    val specificationText: Map<String, String> = emptyMap(),
    val optionValues: Map<String, List<String>> = emptyMap(),
    val optionDrafts: Map<String, String> = emptyMap(),
    val enabledOptions: Map<String, Boolean> = emptyMap(),
    val variantDrafts: Map<String, SellerVariantDraft> = emptyMap(),
)

internal fun encodeSellerVariantSnapshot(snapshot: SellerVariantEditorSnapshot): String = Gson().toJson(snapshot)

internal fun decodeSellerVariantSnapshot(json: String?): SellerVariantEditorSnapshot? =
    json?.let { runCatching { Gson().fromJson(it, SellerVariantEditorSnapshot::class.java) }.getOrNull() }

internal fun decodeSellerVariantSnapshots(json: String): Map<String, String> =
    runCatching {
        Gson().fromJson<Map<String, String>>(json, object : TypeToken<Map<String, String>>() {}.type).orEmpty()
    }.getOrDefault(emptyMap())

internal fun putSellerVariantSnapshot(json: String, categoryId: String, snapshot: SellerVariantEditorSnapshot): String =
    Gson().toJson(decodeSellerVariantSnapshots(json) + (categoryId to encodeSellerVariantSnapshot(snapshot)))

internal data class SellerVariantOutputs(
    val specifications: List<ShopSellerSpecificationInput> = emptyList(),
    val options: List<ShopSellerProductOptionInput> = emptyList(),
    val variants: List<ShopSellerVariantInput> = emptyList(),
)

internal fun encodeSellerVariantOutputs(outputs: SellerVariantOutputs): String = Gson().toJson(outputs)

internal fun decodeSellerVariantOutputs(json: String): SellerVariantOutputs =
    runCatching { Gson().fromJson(json, SellerVariantOutputs::class.java) }.getOrNull() ?: SellerVariantOutputs()

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
                    if (attribute.suggestions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(attribute.suggestions.filterNot { suggestion -> selected.any { it.equals(suggestion, true) } }, key = { it }) { suggestion ->
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

        val current = outputs(includeUnselectedCandidates = true)
        if (current.third.isNotEmpty()) {
            Text("Variant inventory (${current.third.size})", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
                        OutlinedTextField(draft.sku, { value -> variantDrafts[signature] = draft.copy(sku = value); emit() }, label = { Text("Seller SKU") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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
