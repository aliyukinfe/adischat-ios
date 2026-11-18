package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopPublicVariantDto
import com.adischat.app.ui.theme.AdisGebeyaColors

internal enum class ProductPurchaseAction { ADD_TO_CART, BUY_NOW }

internal fun buyerSelectableConfigurationRequired(product: ShopProductDto): Boolean =
    product.variants.count { it.status == "active" } > 1

internal fun directPurchaseVariant(product: ShopProductDto): ShopPublicVariantDto? =
    product.variants.filter { it.status == "active" }.singleOrNull()?.takeIf { it.availableQuantity > 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductPurchaseVariantSheet(
    product: ShopProductDto,
    action: ProductPurchaseAction,
    selectedValues: MutableMap<String, String>,
    selectedVariantId: String?,
    onSelectedVariantId: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    val requiredOptionCodes = remember(product.options) { product.options.sortedBy { it.position }.map { it.code } }
    var quantity by remember(product.id, action) { mutableIntStateOf(1) }
    val selectedVariant = if (requiredOptionCodes.isEmpty()) {
        product.variants.singleOrNull { it.id == selectedVariantId && it.status == "active" }
    } else resolveExactVariant(product.variants, requiredOptionCodes, selectedValues)
    val maximum = selectedVariant?.availableQuantity?.coerceAtMost(99) ?: 0
    val imageUrl = remember(product.images, selectedVariant?.id) {
        val variantImageIds = selectedVariant?.imageIds.orEmpty().toSet()
        product.images.orEmpty()
            .filter { !it.imageUrl.isNullOrBlank() }
            .sortedWith(compareByDescending<com.adischat.app.feature.shop.data.ShopProductImageDto> { it.isPrimary == true }.thenBy { it.displayOrder ?: 0 })
            .firstOrNull { variantImageIds.isNotEmpty() && it.id in variantImageIds }
            ?.imageUrl
            ?: product.images.orEmpty().firstOrNull { it.isPrimary == true }?.imageUrl
            ?: product.images.orEmpty().firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
    }
    LaunchedEffect(maximum) { if (maximum > 0) quantity = quantity.coerceIn(1, maximum) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(sheetState) { sheetState.expand() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AdisGebeyaColors.Surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.86f).dp)
                .navigationBarsPadding().imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = product.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(82.dp).clip(RoundedCornerShape(14.dp)),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(product.title, color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2)
                    Text(
                        (if (selectedVariant == null && product.hasVariants) "From " else "") +
                            formatVariantPrice(selectedVariant?.priceMinor ?: product.priceMinor),
                        color = AdisGebeyaColors.Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                    )
                    Text(
                        when {
                            selectedVariant == null -> "Select options to see availability"
                            selectedVariant.availableQuantity <= 0 -> "Out of stock"
                            else -> "${selectedVariant.availableQuantity} available"
                        },
                        color = AdisGebeyaColors.SecondaryText,
                        fontSize = 13.sp,
                    )
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = AdisGebeyaColors.Border)
                ProductVariantSelector(
                    options = product.options,
                    variants = product.variants,
                    selectedValues = selectedValues,
                    onSelect = { code, value ->
                        selectedValues[code] = value
                        clearInvalidDependentSelections(product.variants, code, selectedValues)
                        quantity = 1
                    },
                    allowConfigurationSwitch = true,
                )
                if (requiredOptionCodes.isEmpty()) {
                    product.variants.filter { it.status == "active" }.forEach { variant ->
                        OutlinedButton(
                            onClick = { onSelectedVariantId(variant.id); quantity = 1 },
                            enabled = variant.availableQuantity > 0,
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, if (selectedVariantId == variant.id) AdisGebeyaColors.Primary else AdisGebeyaColors.Border),
                        ) { Text(variant.title.ifBlank { "Option ${product.variants.indexOf(variant) + 1}" }) }
                    }
                }
                ProductQuantitySelector(quantity = quantity, maximum = maximum, onQuantity = { quantity = it })
            }
            val canConfirm = selectedVariant != null && selectedVariant.availableQuantity >= quantity && quantity > 0
            val totalPriceMinor = (selectedVariant?.priceMinor ?: product.priceMinor) * quantity
            var lastConfirmClickTime by remember(product.id, action) { androidx.compose.runtime.mutableStateOf(0L) }
            val onConfirmDebounced = {
                val now = System.currentTimeMillis()
                if (now - lastConfirmClickTime >= 600L) {
                    lastConfirmClickTime = now
                    selectedVariant?.let { onConfirm(it.id, quantity) }
                }
            }
            if (action == ProductPurchaseAction.ADD_TO_CART) {
                OutlinedButton(
                    onClick = { onConfirmDebounced() },
                    enabled = canConfirm,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.5.dp, if (canConfirm) AdisGebeyaColors.Primary else AdisGebeyaColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AdisGebeyaColors.Primary,
                        disabledContentColor = AdisGebeyaColors.Disabled,
                    ),
                ) {
                    Text(
                        if (canConfirm) "Add to Cart • ${formatVariantPrice(totalPriceMinor)}" else "Add to Cart",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            } else {
                Button(
                    onClick = { onConfirmDebounced() },
                    enabled = canConfirm,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AdisGebeyaColors.Primary,
                        disabledContainerColor = AdisGebeyaColors.Border,
                        contentColor = AdisGebeyaColors.OnColor,
                        disabledContentColor = AdisGebeyaColors.Disabled,
                    ),
                ) {
                    Text(
                        if (canConfirm) "Buy Now • ${formatVariantPrice(totalPriceMinor)}" else "Buy Now",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

internal fun clearInvalidDependentSelections(
    variants: List<ShopPublicVariantDto>,
    changedCode: String,
    selectedValues: MutableMap<String, String>,
) {
    val changedValue = selectedValues[changedCode] ?: return
    val retained = linkedMapOf(changedCode to changedValue)
    selectedValues.keys.filter { it != changedCode }.toList().forEach { code ->
        val value = selectedValues[code] ?: return@forEach
        val valid = variants.any { variant ->
            variant.status == "active" && variant.availableQuantity > 0 &&
                (retained + (code to value)).all { (selectedCode, selectedValue) ->
                    variant.options[selectedCode].equals(selectedValue, ignoreCase = true)
                }
        }
        if (valid) retained[code] = value
    }
    selectedValues.clear()
    selectedValues.putAll(retained)
}

private fun formatVariantPrice(priceMinor: Int): String =
    "ETB ${"%,d".format(priceMinor.coerceAtLeast(0) / 100)}"
