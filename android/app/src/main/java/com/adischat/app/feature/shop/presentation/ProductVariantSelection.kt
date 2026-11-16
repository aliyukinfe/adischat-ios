package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopProductOptionDto
import com.adischat.app.feature.shop.data.ShopProductOptionValueDto
import com.adischat.app.feature.shop.data.ShopPublicVariantDto
import com.adischat.app.ui.theme.AdisGebeyaColors

internal fun resolveExactVariant(
    variants: List<ShopPublicVariantDto>,
    requiredOptionCodes: List<String>,
    selectedValues: Map<String, String>,
): ShopPublicVariantDto? {
    if (requiredOptionCodes.any { selectedValues[it].isNullOrBlank() }) return null
    return variants.singleOrNull { variant ->
        variant.status == "active" && requiredOptionCodes.all { code ->
            variant.options[code].equals(selectedValues[code], ignoreCase = true)
        }
    }
}

internal fun isOptionValueAvailable(
    variants: List<ShopPublicVariantDto>,
    optionCode: String,
    displayValue: String,
    selectedValues: Map<String, String>,
): Boolean = variants.any { variant ->
    variant.status == "active" &&
        variant.availableQuantity > 0 &&
        variant.options[optionCode].equals(displayValue, ignoreCase = true) &&
        selectedValues.all { (selectedCode, selectedValue) ->
            selectedCode == optionCode || variant.options[selectedCode].equals(selectedValue, ignoreCase = true)
        }
}

internal fun isPurchaseOptionValueAvailable(
    variants: List<ShopPublicVariantDto>,
    optionCode: String,
    displayValue: String,
): Boolean = variants.any { variant ->
    variant.status == "active" && variant.availableQuantity > 0 &&
        variant.options[optionCode].equals(displayValue, ignoreCase = true)
}

@Composable
internal fun ProductVariantSelector(
    options: List<ShopProductOptionDto>,
    variants: List<ShopPublicVariantDto>,
    selectedValues: Map<String, String>,
    onSelect: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    allowConfigurationSwitch: Boolean = false,
) {
    if (options.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AdisGebeyaColors.Surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AdisGebeyaColors.Border),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            options.sortedBy { it.position }.forEach { option ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(option.name, color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        selectedValues[option.code]?.let { Text(it, color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp) }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(option.values, key = { it.id.ifBlank { "${option.code}:${it.code}" } }) { value ->
                            VariantValueChip(
                                value = value,
                                selected = selectedValues[option.code].equals(value.displayValue, ignoreCase = true),
                                enabled = if (allowConfigurationSwitch) {
                                    isPurchaseOptionValueAvailable(variants, option.code, value.displayValue)
                                } else isOptionValueAvailable(variants, option.code, value.displayValue, selectedValues),
                                onClick = { onSelect(option.code, value.displayValue) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VariantValueChip(
    value: ShopProductOptionValueDto,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = when {
        selected -> AdisGebeyaColors.Primary
        enabled -> AdisGebeyaColors.Border
        else -> AdisGebeyaColors.Border.copy(alpha = .55f)
    }
    val interactionModifier = if (enabled) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.semantics { disabled() }
    }
    Row(
        modifier = Modifier
            .widthIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AdisGebeyaColors.PrimaryLight else AdisGebeyaColors.Surface)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .then(interactionModifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        value.colorHex?.let { hex ->
            val swatch = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(AdisGebeyaColors.Border)
            Box(Modifier.size(16.dp).clip(CircleShape).background(swatch).border(1.dp, AdisGebeyaColors.Border, CircleShape))
        }
        Text(
            value.displayValue,
            color = if (enabled) AdisGebeyaColors.PrimaryText else AdisGebeyaColors.Disabled,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun ProductQuantitySelector(
    quantity: Int,
    maximum: Int,
    onQuantity: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (maximum <= 0) return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Quantity", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("$maximum available", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp)
        }
        Surface(shape = RoundedCornerShape(14.dp), color = AdisGebeyaColors.SecondarySurface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onQuantity((quantity - 1).coerceAtLeast(1)) }, enabled = quantity > 1) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease quantity", tint = AdisGebeyaColors.Primary)
                }
                Text(quantity.toString(), color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { onQuantity((quantity + 1).coerceAtMost(maximum)) }, enabled = quantity < maximum) {
                    Icon(Icons.Default.Add, contentDescription = "Increase quantity", tint = AdisGebeyaColors.Primary)
                }
            }
        }
    }
}
