package com.adischat.app.feature.shop.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopProductSpecificationDto
import com.adischat.app.ui.theme.AdisGebeyaColors

internal data class PublicProductDetail(
    val code: String,
    val label: String,
    val value: String,
)

private val privateProductCodes = setOf(
    "COST", "COST_PRICE", "SELLER_COST", "INTERNAL_SKU", "ADMIN_NOTES",
    "MODERATION_NOTES", "RESERVED_QUANTITY", "STORAGE_PATH", "SERVICE_ID",
    "IMEI", "IMEI_1", "IMEI_2", "IMEI1", "IMEI2", "MEID", "EID", "ICCID", "IMSI", "ESN", "SERIAL", "SERIAL_NO", "SERIAL_NUMBER",
    "DEVICE_ID", "DEVICE_IDENTIFIER", "ANDROID_ID", "MAC_ADDRESS",
)

private val sellerDisclosureLabels = mapOf(
    "CONDITION" to "Condition (seller-reported)",
    "KNOWN_DEFECTS" to "Known defects (seller-reported)",
    "REPAIR_HISTORY" to "Repair history (seller-reported)",
    "BATTERY_HEALTH_PERCENT" to "Measured battery health (seller-reported)",
    "INCLUDED_ACCESSORIES" to "Included accessories (seller-reported)",
    "DEVICE_LOCK_STATUS" to "Device lock status (seller-reported)",
)

private fun String.normalizedSpecificationCode(): String = uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')

private fun ShopProductSpecificationDto.isPrivateIdentifier(): Boolean {
    val identifiers = listOf(code, name).map(String::normalizedSpecificationCode)
    return identifiers.any { it in privateProductCodes }
}

internal fun ShopProductSpecificationDto.publicValue(): String? {
    if (isPrivateIdentifier()) return null
    if (code.uppercase() == "CONDITION" && valueText?.trim()?.let { it in sellerConditionChoices } != true) return null
    if (code.uppercase() == "BATTERY_HEALTH_PERCENT" && valueNumber?.let { it.isFinite() && it in 0.0..100.0 } != true) return null
    val raw = when {
        !valueText.isNullOrBlank() -> valueText.trim()
        valueNumber != null -> if (valueNumber % 1.0 == 0.0) valueNumber.toLong().toString() else valueNumber.toString()
        valueBoolean != null -> if (valueBoolean) "Yes" else "No"
        valueJson is String -> valueJson.trim().takeIf(String::isNotBlank)
        valueJson is Collection<*> -> valueJson.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }.joinToString(", ").takeIf(String::isNotBlank)
        else -> null
    } ?: return null
    return if (unit?.trim() == "%") "$raw%" else listOf(raw, unit?.trim()).filterNot { it.isNullOrBlank() }.joinToString(" ")
}

internal fun publicProductDetails(product: ShopProductDto): List<PublicProductDetail> {
    val rows = buildList {
        product.brand?.trim()?.takeIf(String::isNotBlank)?.let { add(PublicProductDetail("BRAND", "Brand", it)) }
        product.specifications.forEach { specification ->
            specification.publicValue()?.let { value ->
                add(PublicProductDetail(specification.code, sellerDisclosureLabels[specification.code.uppercase()]
                    ?: specification.name.ifBlank { specification.code.toDisplayLabel() }, value))
            }
        }
    }
    return rows.distinctBy { it.code.uppercase() to it.value.lowercase() }
}

internal fun productCompactHighlight(product: ShopProductDto): String? {
    val preferred = listOf("RAM", "STORAGE", "SIZE", "MATERIAL", "COLOR", "MODEL", "STONE_TYPE")
    val values = publicProductDetails(product)
        .sortedBy { preferred.indexOf(it.code.uppercase()).let { index -> if (index < 0) Int.MAX_VALUE else index } }
        .map { it.value }
        .distinct()
        .take(2)
    if (values.isNotEmpty()) return values.joinToString(" • ")
    val unit = product.sellingUnit?.name ?: product.sellingUnitCode.toDisplayLabel()
    val unitHint = unit.takeUnless { product.sellingUnitCode.equals("PIECE", true) }
    return unitHint?.let { "Sold by $it" }
        ?: product.shortDescription.cleanSellerText()?.lineSequence()?.firstOrNull()?.take(90)
}

internal fun String?.cleanSellerText(): String? = this
    ?.replace("\r\n", "\n")
    ?.replace(Regex("[ \\t]+\n"), "\n")
    ?.replace(Regex("\n{3,}"), "\n\n")
    ?.trim()
    ?.takeIf(String::isNotBlank)

private fun String.toDisplayLabel(): String = lowercase()
    .replace('_', ' ')
    .replace('-', ' ')
    .split(' ')
    .filter(String::isNotBlank)
    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

@Composable
internal fun ProductHighlights(product: ShopProductDto, modifier: Modifier = Modifier) {
    val highlights = remember(product) {
        buildList {
            if (!product.sellingUnitCode.equals("PIECE", true)) {
                add(PublicProductDetail("SELLING_UNIT", "Selling unit", product.sellingUnit?.name ?: product.sellingUnitCode.toDisplayLabel()))
            }
            addAll(publicProductDetails(product).filterNot { it.code.uppercase() in sellerDisclosureLabels })
            product.options.forEach { option ->
                val values = option.values.mapNotNull { it.displayValue.ifBlank { it.value }.takeIf(String::isNotBlank) }.distinct()
                if (values.isNotEmpty()) add(PublicProductDetail(option.code, option.name, values.take(3).joinToString(", ")))
            }
        }.distinctBy { it.code.uppercase() }.take(6)
    }
    if (highlights.isEmpty()) return
    ProductSectionCard(modifier) {
        ProductSectionTitle("Highlights")
        highlights.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { highlight ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(attributeIcon(highlight.code), contentDescription = null, tint = AdisGebeyaColors.Primary, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(highlight.label, color = AdisGebeyaColors.SecondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(highlight.value, color = AdisGebeyaColors.PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun ProductOptionsSummary(
    product: ShopProductDto,
    modifier: Modifier = Modifier,
    selectedValues: Map<String, String> = emptyMap(),
    onSelectOption: ((String, String) -> Unit)? = null,
) {
    if (product.options.isEmpty()) return
    ProductSectionCard(modifier) {
        ProductSectionTitle("Available options")
        if (onSelectOption != null) {
            ProductVariantSelector(
                options = product.options,
                variants = product.variants,
                selectedValues = selectedValues,
                onSelect = onSelectOption,
                allowConfigurationSwitch = true,
            )
        } else {
            val options = product.options.mapNotNull { option ->
                option.values.mapNotNull { it.displayValue.ifBlank { it.value }.takeIf(String::isNotBlank) }
                    .distinct().takeIf(List<String>::isNotEmpty)?.let { option.name to it.joinToString(", ") }
            }
            options.forEach { (label, value) -> ProductInformationRow(attributeIcon(label), label, value) }
            Text("Choose options after tapping Add to Cart or Buy Now.", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
internal fun ProductDetailsInformation(product: ShopProductDto, categories: List<ShopCategoryDto>, modifier: Modifier = Modifier) {
    val allDetails = remember(product) { publicProductDetails(product) }
    val details = allDetails.filterNot { it.code.uppercase() in sellerDisclosureLabels }
    val disclosures = allDetails.filter { it.code.uppercase() in sellerDisclosureLabels }
    val categoryPath = remember(product.categoryId, categories) { categoryPath(product.categoryId, categories) }
    val unitName = product.sellingUnit?.name ?: product.sellingUnitCode.toDisplayLabel()
    val stock = product.aggregateAvailable ?: product.aggregateAvailableQuantity
    val purchaseRows = buildList {
        if (!product.sellingUnitCode.equals("PIECE", true) || product.minimumOrderQuantity > 1) add(PublicProductDetail("SELLING_UNIT", "Selling unit", unitName))
        if (product.minimumOrderQuantity > 1) add(PublicProductDetail("MINIMUM_ORDER", "Minimum order", "${product.minimumOrderQuantity} $unitName"))
        stock?.takeIf { it >= 0 }?.let { add(PublicProductDetail("STOCK", "Available stock", "$it${if (product.sellingUnitCode.equals("PIECE", true)) "" else " $unitName"}")) }
    }
    if (details.isEmpty() && disclosures.isEmpty() && purchaseRows.isEmpty() && categoryPath.isEmpty()) return
    var expanded by remember(product.id) { mutableStateOf(false) }
    ProductSectionCard(modifier.animateContentSize()) {
        if (details.isNotEmpty()) {
            ProductSectionTitle("Product details")
            val visible = if (expanded) details else details.take(6)
            visible.forEachIndexed { index, detail ->
                ProductInformationRow(attributeIcon(detail.code), detail.label, detail.value)
                if (index < visible.lastIndex) HorizontalDivider(color = AdisGebeyaColors.Border)
            }
            if (details.size > 6) {
                Text(
                    if (expanded) "Show fewer details" else "View all ${details.size} details",
                    color = AdisGebeyaColors.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 6.dp),
                )
            }
        }
        if (disclosures.isNotEmpty()) {
            if (details.isNotEmpty()) HorizontalDivider(color = AdisGebeyaColors.Border, modifier = Modifier.padding(vertical = 4.dp))
            ProductSectionTitle("Condition & seller disclosures")
            disclosures.forEach { ProductInformationRow(attributeIcon(it.code), it.label, it.value) }
        }
        if (purchaseRows.isNotEmpty()) {
            if (details.isNotEmpty() || disclosures.isNotEmpty()) HorizontalDivider(color = AdisGebeyaColors.Border, modifier = Modifier.padding(vertical = 4.dp))
            ProductSectionTitle("Purchase information")
            purchaseRows.forEach { ProductInformationRow(attributeIcon(it.code), it.label, it.value) }
        }
        if (categoryPath.isNotEmpty()) {
            if (details.isNotEmpty() || disclosures.isNotEmpty() || purchaseRows.isNotEmpty()) HorizontalDivider(color = AdisGebeyaColors.Border, modifier = Modifier.padding(vertical = 4.dp))
            ProductInformationRow(Icons.Default.Category, "Category", categoryPath.joinToString("  ›  "))
        }
    }
}

@Composable
internal fun ProductDescriptionSection(product: ShopProductDto, modifier: Modifier = Modifier) {
    val description = product.description.cleanSellerText() ?: return
    ProductSectionCard(modifier) {
        ProductSectionTitle("Description")
        ExpandableProductText(description, 6, "Read more", "Show less")
    }
}

@Composable
internal fun ExpandableProductText(
    text: String?,
    collapsedLines: Int,
    expandLabel: String,
    collapseLabel: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(color = AdisGebeyaColors.PrimaryText, lineHeight = 21.sp),
) {
    val clean = text.cleanSellerText() ?: return
    var expanded by remember(clean) { mutableStateOf(false) }
    var overflowing by remember(clean) { mutableStateOf(false) }
    Column(modifier.animateContentSize()) {
        Text(
            clean,
            style = style,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) overflowing = result.hasVisualOverflow },
        )
        if (overflowing || expanded) {
            Text(
                if (expanded) collapseLabel else expandLabel,
                color = AdisGebeyaColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { expanded = !expanded }.padding(top = 5.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun ProductInformationRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AdisGebeyaColors.Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp, modifier = Modifier.weight(.42f))
        Text(value, color = AdisGebeyaColors.PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(.58f))
    }
}

private fun categoryPath(categoryId: String?, categories: List<ShopCategoryDto>): List<String> {
    if (categoryId.isNullOrBlank()) return emptyList()
    val byId = categories.associateBy { it.id }
    val path = mutableListOf<String>()
    val visited = mutableSetOf<String>()
    var current = byId[categoryId]
    while (current != null && visited.add(current.id)) {
        path.add(0, current.name)
        current = current.parentId?.let(byId::get)
    }
    return path
}

internal fun attributeIcon(code: String): ImageVector = when (code.uppercase().replace(' ', '_')) {
    "BRAND" -> Icons.Default.Sell
    "MODEL" -> Icons.Default.Devices
    "COLOR", "COLOR_OPTIONS" -> Icons.Default.Palette
    "SIZE", "DIMENSIONS", "WIDTH", "SCREEN_SIZE" -> Icons.Default.Straighten
    "STORAGE" -> Icons.Default.SdStorage
    "RAM", "MEMORY", "PROCESSOR" -> Icons.Default.Memory
    "BATTERY", "BATTERY_CAPACITY" -> Icons.Default.BatteryFull
    "MATERIAL", "STONE_TYPE", "SHAPE", "GRADE" -> Icons.Default.ViewInAr
    "WEIGHT", "SELLING_UNIT" -> Icons.Default.Scale
    "MINIMUM_ORDER", "STOCK", "PACKAGING" -> Icons.Default.Inventory2
    "WARRANTY" -> Icons.Default.VerifiedUser
    "NETWORK" -> Icons.Default.NetworkCell
    "CATEGORY" -> Icons.Default.Category
    else -> Icons.Default.Info
}
