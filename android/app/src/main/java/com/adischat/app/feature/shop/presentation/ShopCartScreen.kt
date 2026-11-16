package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopCartDto
import com.adischat.app.feature.shop.data.ShopCartItemDto
import com.adischat.app.feature.shop.data.ShopProtectedState
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.ui.theme.AdisGebeyaColors

@Composable
internal fun CartScreen(
    protectedState: ShopProtectedState,
    cartCount: Int,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onUpdate: (String, Int) -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onClear: () -> Unit,
    onCheckout: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    val cart = protectedState.cart
    val contentState = buyerCartContentState(cart, protectedState.loading, protectedState.error)
    val mutationPending = CART_MUTATION_BUSY_ID in protectedState.busyIds
    val unavailable = cartHasConfirmedUnavailableQuantity(cart, protectedState.cartAvailableQuantities)
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = AdisGebeyaColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (cart != null && cart.items.isNotEmpty()) {
                CartCheckoutBar(cart = cart, pending = mutationPending || unavailable, onCheckout = onCheckout)
            }
        },
    ) { innerPadding ->
        LazyColumn(
            Modifier.fillMaxSize().background(AdisGebeyaColors.Background).padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Cart (${cart?.items?.size ?: cartCount})", color = AdisGebeyaColors.PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (!cart?.items.isNullOrEmpty()) {
                        androidx.compose.material3.TextButton(onClick = onClear, enabled = !mutationPending) { Text("Clear", color = AdisGebeyaColors.Error) }
                    }
                }
            }
            if (contentState == BuyerCartContentState.Loading) {
                items(3) { SkeletonCard() }
            } else if (contentState == BuyerCartContentState.Error) {
                item { ErrorCardMessage(protectedState.error.orEmpty()) }
            } else if (contentState == BuyerCartContentState.Empty) {
                item {
                    PremiumEmptyState(
                        title = "Your cart is empty",
                        message = "Products you add from Product Details will appear here.",
                        icon = Icons.Default.ShoppingCart,
                    )
                }
            } else {
                val groups = cartStoreGroups(cart?.items.orEmpty())
                groups.forEach { (storeId, storeItems) ->
                    val storeName = storeItems.firstOrNull()?.product?.store?.name?.takeIf(String::isNotBlank) ?: "Marketplace store"
                    item(key = "store-$storeId") { CartStoreHeader(storeName) }
                    items(storeItems, key = { it.id }) { item ->
                        CartItemRow(item, protectedState.cartAvailableQuantities[item.id], mutationPending, item.id in protectedState.busyIds, onUpdate, onProduct)
                    }
                }
            }
            protectedState.error?.takeIf(String::isNotBlank)?.takeIf { cart != null }?.let { message ->
                item { ErrorCardMessage(message) }
            }
        }
    }
}

@Composable
private fun CartStoreHeader(storeName: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Storefront, contentDescription = null, tint = AdisGebeyaColors.Primary, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(storeName, color = AdisGebeyaColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CartItemRow(item: ShopCartItemDto, available: Int?, busy: Boolean, showProgress: Boolean, onUpdate: (String, Int) -> Unit, onProduct: (ShopProductDto) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(82.dp).clip(RoundedCornerShape(12.dp)).background(AdisGebeyaColors.SecondarySurface).clickable { onProduct(cartProductForDetails(item)) }) {
                val imageUrl = item.product?.images?.firstOrNull { it.isPrimary == true }?.imageUrl ?: item.product?.images?.firstOrNull()?.imageUrl
                ShopAssetImage(imageUrl, item.product?.title, Modifier.fillMaxSize(), iconSize = 28.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.product?.title ?: "AdisGebeya product", modifier = Modifier.clickable { onProduct(cartProductForDetails(item)) }, color = AdisGebeyaColors.PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.variant?.options?.values?.takeIf { it.isNotEmpty() }?.let { values ->
                    Text(values.joinToString(" • "), color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val unitSymbol = item.unitSnapshot?.symbol?.takeIf { it.isNotBlank() }
                Text(
                    formatEtb(item.unitPriceMinor) + (unitSymbol?.let { " / $it" } ?: ""),
                    color = AdisGebeyaColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (available != null) Text(cartAvailabilityMessage(item, available) ?: "$available available", color = if (cartAvailabilityMessage(item, available) != null) AdisGebeyaColors.Error else AdisGebeyaColors.SecondaryText, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(10.dp), color = AdisGebeyaColors.SecondarySurface) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onUpdate(item.id, item.quantity - 1) }, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Remove, contentDescription = "Decrease quantity", modifier = Modifier.size(17.dp)) }
                            Text("${item.quantity}${unitSymbol?.let { " $it" } ?: ""}", color = AdisGebeyaColors.PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            IconButton(onClick = { onUpdate(item.id, item.quantity + 1) }, enabled = !busy && (available == null || item.quantity < available), modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Add, contentDescription = "Increase quantity", modifier = Modifier.size(17.dp)) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onUpdate(item.id, 0) }, enabled = !busy) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove product", tint = AdisGebeyaColors.Error, modifier = Modifier.size(20.dp))
                    }
                    if (showProgress) CircularProgressIndicator(color = AdisGebeyaColors.Primary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        }
        HorizontalDivider(color = AdisGebeyaColors.Divider)
    }
}

@Composable
private fun CartCheckoutBar(cart: ShopCartDto, pending: Boolean, onCheckout: () -> Unit) {
    Surface(color = AdisGebeyaColors.Surface, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Subtotal", color = AdisGebeyaColors.SecondaryText, fontSize = 11.sp)
                Text(formatEtb(cart.totals.subtotalMinor), color = AdisGebeyaColors.PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onCheckout,
                enabled = !pending,
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AdisGebeyaColors.Primary),
                contentPadding = PaddingValues(horizontal = 22.dp),
            ) {
                Text("Checkout (${cart.items.size})", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
