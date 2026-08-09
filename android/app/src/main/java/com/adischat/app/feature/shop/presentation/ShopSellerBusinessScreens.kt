package com.adischat.app.feature.shop.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Reviews
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adischat.app.feature.shop.data.ShopSellerBusinessDto
import com.adischat.app.feature.shop.data.ShopSellerInventoryChange
import com.adischat.app.feature.shop.data.ShopSellerReviewDto
import com.adischat.app.ui.theme.AdisGebeyaColors
import java.util.Locale
import java.time.LocalDate

internal data class SellerBusinessActions(
    val refresh: (String?) -> Unit,
    val loadRange: (String?, String?, String?) -> Unit,
    val updateStore: (Map<String, Any?>) -> Unit,
    val updateInventory: (String, String, Int, Int) -> Unit,
    val updateInventoryBulk: (String, List<ShopSellerInventoryChange>) -> Unit,
    val saveCoupon: (Map<String, Any?>) -> Unit,
    val saveCampaign: (Map<String, Any?>) -> Unit,
    val replyReview: (String, String, String, String) -> Unit,
    val saveCustomerNote: (String, String, String) -> Unit,
    val openCustomerChat: (String) -> Unit,
    val saveSettings: (Map<String, Any?>) -> Unit,
    val uploadStoreAsset: (String, String, ByteArray, String) -> Unit,
)

private enum class SellerBusinessModule(val label: String, val subtitle: String, val icon: ImageVector) {
    Store("Store Profile", "Identity, contact, hours and policies", Icons.Default.Storefront),
    Inventory("Inventory", "Stock, reserved quantity and alerts", Icons.Default.Inventory2),
    Marketing("Marketing", "Coupons and campaigns", Icons.Default.Campaign),
    Analytics("Analytics", "Real orders and sales metrics", Icons.Default.Analytics),
    Customers("Customers", "Privacy-safe buyer history", Icons.Default.People),
    Reviews("Reviews", "Product and store feedback", Icons.Default.Reviews),
    Returns("Returns", "Refund and exchange requests", Icons.Default.Assignment),
    Finance("Finance", "Payments and settlement availability", Icons.Default.AccountBalanceWallet),
    Settings("Settings", "Notifications, shipping and preferences", Icons.Default.Settings),
}

@Composable
internal fun SellerBusinessSuite(
    business: ShopSellerBusinessDto?,
    busyIds: Set<String>,
    actions: SellerBusinessActions,
) {
    var selected by remember { mutableStateOf<SellerBusinessModule?>(null) }
    if (selected == null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SellerBusinessHeading("Seller tools", "All actions use your authenticated, server-owned store.")
            SellerBusinessModule.entries.chunked(2).forEach { modules ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    modules.forEach { module ->
                        SellerModuleCard(module, Modifier.weight(1f)) { selected = module }
                    }
                    if (modules.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selected = null }) { Text("Back") }
            Text(selected!!.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AdisGebeyaColors.PrimaryText)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { actions.refresh(business?.selectedStore?.id) }) { Text("Refresh") }
        }
        if (business == null) {
            SellerInfoCard("Seller tools are loading. Tap Refresh if this takes longer than expected.")
            return@Column
        }
        when (selected) {
            SellerBusinessModule.Store -> SellerStoreProfile(business, actions)
            SellerBusinessModule.Inventory -> SellerInventory(business, busyIds, actions)
            SellerBusinessModule.Marketing -> SellerMarketing(business, actions)
            SellerBusinessModule.Analytics -> SellerAnalytics(business, actions)
            SellerBusinessModule.Customers -> SellerCustomers(business, actions)
            SellerBusinessModule.Reviews -> SellerReviews(business, busyIds, actions)
            SellerBusinessModule.Returns -> SellerReturns(business)
            SellerBusinessModule.Finance -> SellerFinance(business)
            SellerBusinessModule.Settings -> SellerSettings(business, actions)
            null -> Unit
        }
    }
}

@Composable
private fun SellerModuleCard(module: SellerBusinessModule, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AdisGebeyaColors.Surface),
        border = BorderStroke(1.dp, AdisGebeyaColors.Divider),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(module.icon, contentDescription = null, tint = AdisGebeyaColors.Primary)
            Text(module.label, fontWeight = FontWeight.SemiBold, color = AdisGebeyaColors.PrimaryText)
            Text(module.subtitle, style = MaterialTheme.typography.bodySmall, color = AdisGebeyaColors.SecondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SellerStoreProfile(business: ShopSellerBusinessDto, actions: SellerBusinessActions) {
    val store = business.selectedStore ?: return SellerInfoCard("No approved store is connected to this seller account.")
    var name by remember(store.id, store.name) { mutableStateOf(store.name) }
    var description by remember(store.id, store.description) { mutableStateOf(store.description.orEmpty()) }
    var businessType by remember(store.id, store.category) { mutableStateOf(store.category.orEmpty()) }
    var address by remember(store.id, store.addressLine) { mutableStateOf(store.addressLine.orEmpty()) }
    var city by remember(store.id, store.city) { mutableStateOf(store.city.orEmpty()) }
    var region by remember(store.id, store.regionState) { mutableStateOf(store.regionState.orEmpty()) }
    var country by remember(store.id, store.country) { mutableStateOf(store.country) }
    var phone by remember(store.id, store.contactPhone) { mutableStateOf(store.contactPhone.orEmpty()) }
    var email by remember(store.id, store.contactEmail) { mutableStateOf(store.contactEmail.orEmpty()) }
    var hours by remember(store.id, store.businessHours) { mutableStateOf(store.businessHours["summary"]?.toString().orEmpty()) }
    var shipping by remember(store.id, store.shippingPolicy) { mutableStateOf(store.shippingPolicy.orEmpty()) }
    var returns by remember(store.id, store.returnPolicy) { mutableStateOf(store.returnPolicy.orEmpty()) }
    var exchanges by remember(store.id, store.exchangePolicy) { mutableStateOf(store.exchangePolicy.orEmpty()) }
    var warranty by remember(store.id, store.warrantyPolicy) { mutableStateOf(store.warrantyPolicy.orEmpty()) }
    var support by remember(store.id, store.supportContact) { mutableStateOf(store.supportContact.orEmpty()) }
    var website by remember(store.id, store.socialLinks) { mutableStateOf(store.socialLinks["website"]?.toString().orEmpty()) }
    var instagram by remember(store.id, store.socialLinks) { mutableStateOf(store.socialLinks["instagram"]?.toString().orEmpty()) }
    val changed = name != store.name || description != store.description.orEmpty() || businessType != store.category.orEmpty() ||
        address != store.addressLine.orEmpty() || city != store.city.orEmpty() || region != store.regionState.orEmpty() ||
        country != store.country || phone != store.contactPhone.orEmpty() || email != store.contactEmail.orEmpty() ||
        hours != store.businessHours["summary"]?.toString().orEmpty() || shipping != store.shippingPolicy.orEmpty() ||
        returns != store.returnPolicy.orEmpty() || exchanges != store.exchangePolicy.orEmpty() || warranty != store.warrantyPolicy.orEmpty() ||
        support != store.supportContact.orEmpty() || website != store.socialLinks["website"]?.toString().orEmpty() ||
        instagram != store.socialLinks["instagram"]?.toString().orEmpty()
    val context = LocalContext.current
    var uploadKind by remember { mutableStateOf("logo") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) actions.uploadStoreAsset(store.id, uploadKind, bytes, mime)
        }
    }
    SellerBusinessHeading(store.name, "${humanLabel(store.approvalStatus)} store profile")
    SellerField("Store name", name) { name = it }
    SellerField("Description", description, 3) { description = it }
    SellerField("Business type", businessType) { businessType = it }
    SellerField("Address", address) { address = it }
    SellerField("City", city) { city = it }
    SellerField("Region / state", region) { region = it }
    SellerField("Country", country) { country = it }
    SellerField("Contact phone", phone) { phone = it }
    SellerField("Contact email", email) { email = it }
    SellerField("Business hours", hours) { hours = it }
    SellerField("Shipping policy", shipping, 3) { shipping = it }
    SellerField("Return policy", returns, 3) { returns = it }
    SellerField("Exchange policy", exchanges, 3) { exchanges = it }
    SellerField("Warranty", warranty, 3) { warranty = it }
    SellerField("Support contact and policy", support, 2) { support = it }
    SellerField("Website", website) { website = it }
    SellerField("Instagram", instagram) { instagram = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { uploadKind = "logo"; picker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Change logo") }
        OutlinedButton(onClick = { uploadKind = "cover"; picker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Change banner") }
    }
    Button(onClick = {
        actions.updateStore(mapOf(
            "storeId" to store.id, "expectedUpdatedAt" to store.updatedAt, "name" to name, "description" to description,
            "businessType" to businessType, "addressLine" to address,
            "city" to city, "regionState" to region, "country" to country,
            "contactPhone" to phone, "contactEmail" to email, "businessHours" to mapOf("summary" to hours),
            "socialLinks" to mapOf("website" to website, "instagram" to instagram),
            "shippingPolicy" to shipping, "returnPolicy" to returns, "exchangePolicy" to exchanges,
            "warrantyPolicy" to warranty, "supportContact" to support,
        ))
    }, modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank() && changed) { Text(if (changed) "Save store profile" else "No unsaved changes") }
}

@Composable
private fun SellerInventory(business: ShopSellerBusinessDto, busyIds: Set<String>, actions: SellerBusinessActions) {
    val storeId = business.selectedStore?.id.orEmpty()
    val rows = business.products.flatMap { product -> product.inventory.map { product to it } }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var descending by remember { mutableStateOf(false) }
    val quantities = remember(storeId) { mutableStateMapOf<String, String>() }
    val thresholds = remember(storeId) { mutableStateMapOf<String, String>() }
    val selected = remember(storeId) { mutableStateMapOf<String, Boolean>() }
    rows.forEach { (_, inventory) ->
        quantities.putIfAbsent(inventory.id, inventory.quantityAvailable.toString())
        thresholds.putIfAbsent(inventory.id, inventory.lowStockThreshold.toString())
    }
    val visibleRows = rows.filter { (product, inventory) ->
        product.title.contains(search, ignoreCase = true) || product.sellerSku.orEmpty().contains(search, ignoreCase = true)
    }.filter { (_, inventory) ->
        when (filter) { "low" -> inventory.quantityAvailable <= inventory.lowStockThreshold; "out" -> inventory.quantityAvailable == 0; else -> true }
    }.sortedWith(compareBy<Pair<com.adischat.app.feature.shop.data.ShopSellerProductDto, com.adischat.app.feature.shop.data.ShopSellerProductInventoryDto>> { it.first.title.lowercase(Locale.ROOT) }.let { if (descending) it.reversed() else it })
    SellerBusinessHeading("Inventory", "${rows.size} stock records. Reserved quantity cannot be edited.")
    SellerField("Search product or SKU", search) { search = it }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("all" to "All", "low" to "Low stock", "out" to "Out").forEach { (value, label) ->
            OutlinedButton(onClick = { filter = value }, modifier = Modifier.weight(1f)) { Text(if (filter == value) "$label selected" else label) }
        }
    }
    TextButton(onClick = { descending = !descending }) { Text(if (descending) "Sort Z-A" else "Sort A-Z") }
    if (rows.isEmpty()) SellerInfoCard("No inventory records are available yet.")
    if (rows.isNotEmpty() && visibleRows.isEmpty()) SellerInfoCard("No inventory matches these filters.")
    visibleRows.forEach { (product, inventory) ->
        val variant = product.variants.firstOrNull { it.id == inventory.variantId }
        SellerSurface {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(selected[inventory.id] == true, { selected[inventory.id] = it })
                Column(Modifier.weight(1f)) {
                    Text(product.title, fontWeight = FontWeight.SemiBold, color = AdisGebeyaColors.PrimaryText)
                    Text(listOfNotNull(product.sellerSku?.let { "SKU $it" }, variant?.title, variant?.sku?.let { "Variant SKU $it" }).joinToString(" - ").ifBlank { "Base inventory" }, color = AdisGebeyaColors.SecondaryText)
                }
            }
            Text("Reserved: ${inventory.quantityReserved} - ${if (inventory.quantityAvailable == 0) "Out of stock" else if (inventory.quantityAvailable <= inventory.lowStockThreshold) "Low stock" else "In stock"}", color = AdisGebeyaColors.SecondaryText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SellerNumberField("Available", quantities[inventory.id].orEmpty(), Modifier.weight(1f)) { quantities[inventory.id] = it.filter(Char::isDigit) }
                SellerNumberField("Low-stock alert", thresholds[inventory.id].orEmpty(), Modifier.weight(1f)) { thresholds[inventory.id] = it.filter(Char::isDigit) }
            }
            Button(
                onClick = { actions.updateInventory(storeId, inventory.id, quantities[inventory.id]?.toIntOrNull() ?: 0, thresholds[inventory.id]?.toIntOrNull() ?: 0) },
                enabled = !busyIds.contains("inventory-${inventory.id}"),
            ) { Text("Update stock") }
        }
    }
    val bulkChanges = visibleRows.filter { selected[it.second.id] == true }.map { (_, inventory) -> ShopSellerInventoryChange(inventory.id, quantities[inventory.id]?.toIntOrNull() ?: inventory.quantityAvailable, thresholds[inventory.id]?.toIntOrNull() ?: inventory.lowStockThreshold) }
    if (bulkChanges.isNotEmpty()) Button(onClick = { actions.updateInventoryBulk(storeId, bulkChanges) }, enabled = !busyIds.contains("inventory-bulk"), modifier = Modifier.fillMaxWidth()) { Text("Update ${bulkChanges.size} selected") }
    if (business.inventoryHistory.isNotEmpty()) SellerInfoCard("Latest change: ${business.inventoryHistory.first().quantityDelta} units (${humanLabel(business.inventoryHistory.first().reason)}).")
}

@Composable
private fun SellerMarketing(business: ShopSellerBusinessDto, actions: SellerBusinessActions) {
    val storeId = business.selectedStore?.id.orEmpty()
    var code by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var campaign by remember { mutableStateOf("") }
    var campaignDiscount by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    SellerBusinessHeading("Coupons", "Create real store-scoped discounts. Drafts are not shown to buyers.")
    SellerField("Coupon code", code) { code = it.uppercase(Locale.ROOT).filter { ch -> ch.isLetterOrDigit() || ch in "_-" } }
    SellerNumberField("Discount percent", value, Modifier.fillMaxWidth()) { value = it.filter(Char::isDigit) }
    SellerField("Start date (YYYY-MM-DD)", startDate) { startDate = it.take(10) }
    SellerField("End date (YYYY-MM-DD)", endDate) { endDate = it.take(10) }
    Button(onClick = { actions.saveCoupon(mapOf("storeId" to storeId, "code" to code, "discountType" to "percent", "discountValue" to (value.toIntOrNull() ?: 0), "startsAt" to startDate.ifBlank { null }, "endsAt" to endDate.ifBlank { null }, "status" to "draft")) }, enabled = code.length >= 3 && (value.toIntOrNull() ?: 0) in 1..90) { Text("Save coupon draft") }
    business.coupons.forEach { SellerInfoCard("${it.code}: ${it.discountValue}% - ${humanLabel(it.status)}") }
    SellerBusinessHeading("Campaigns", "Campaigns remain drafts until deliberately activated.")
    SellerField("Campaign title", campaign) { campaign = it }
    SellerNumberField("Discount percent", campaignDiscount, Modifier.fillMaxWidth()) { campaignDiscount = it.filter(Char::isDigit) }
    Button(onClick = { actions.saveCampaign(mapOf("storeId" to storeId, "title" to campaign, "discountPercent" to campaignDiscount.toIntOrNull(), "startsAt" to startDate.ifBlank { null }, "endsAt" to endDate.ifBlank { null }, "status" to "draft", "isActive" to false)) }, enabled = campaign.isNotBlank() && (campaignDiscount.toIntOrNull() ?: 0) in 1..90) { Text("Save campaign draft") }
    business.campaigns.forEach { SellerInfoCard("${it.title}: ${humanLabel(it.status)}") }
}

@Composable
private fun SellerAnalytics(business: ShopSellerBusinessDto, actions: SellerBusinessActions) {
    val metrics = business.analytics
    val storeId = business.selectedStore?.id
    SellerBusinessHeading("Sales analytics", "Calculated from this store's real order records.")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(7L to "7 days", 30L to "30 days", 90L to "90 days").forEach { (days, label) ->
            OutlinedButton(onClick = { actions.loadRange(storeId, LocalDate.now().minusDays(days - 1).toString(), LocalDate.now().toString()) }, modifier = Modifier.weight(1f)) { Text(label) }
        }
    }
    SellerMetric("Revenue", sellerMoney(metrics.revenueMinor))
    SellerMetric("Orders", metrics.orderCount.toString())
    SellerMetric("Delivered", metrics.deliveredOrderCount.toString())
    SellerMetric("Products sold", metrics.productsSold.toString())
    SellerMetric("Average order", sellerMoney(metrics.averageOrderValueMinor))
    SellerMetric("Refund rate", String.format(Locale.US, "%.1f%%", metrics.refundRate * 100))
    SellerMetric("Visitors", metrics.visitors?.toString() ?: "Not measured")
    SellerMetric("Conversion", metrics.conversionRate?.let { String.format(Locale.US, "%.1f%%", it * 100) } ?: "Not measured")
    if (metrics.topProducts.isNotEmpty()) {
        SellerBusinessHeading("Top products", "Real quantities from orders in the selected range.")
        metrics.topProducts.forEach { SellerInfoCard("${it.title}: ${it.quantity} sold - ${sellerMoney(it.revenueMinor)}") }
    }
}

@Composable
private fun SellerCustomers(business: ShopSellerBusinessDto, actions: SellerBusinessActions) {
    val storeId = business.selectedStore?.id.orEmpty()
    var search by remember { mutableStateOf("") }
    SellerBusinessHeading("Customers", "Only order-derived, privacy-safe customer information is shown.")
    SellerField("Search customers", search) { search = it }
    if (business.customers.isEmpty()) SellerInfoCard("No customers have ordered from this store yet.")
    business.customers.filter { it.displayName.contains(search, true) }.forEach { customer ->
        var note by remember(customer.latestOrderGroupId, customer.note) { mutableStateOf(customer.note) }
        SellerSurface {
            Text(customer.displayName, fontWeight = FontWeight.SemiBold, color = AdisGebeyaColors.PrimaryText)
            Text("${customer.orderCount} orders - ${sellerMoney(customer.lifetimeSpendMinor)}", color = AdisGebeyaColors.SecondaryText)
            Text(if (customer.orderCount > 1) "Repeat customer" else "First-time customer", color = AdisGebeyaColors.SecondaryText)
            customer.recentOrders.take(5).forEach { order -> Text("${order.orderNumber}: ${humanLabel(order.status)} - ${sellerMoney(order.totalMinor)}", style = MaterialTheme.typography.bodySmall, color = AdisGebeyaColors.SecondaryText) }
            SellerField("Private seller note", note, 2) { note = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.openCustomerChat(customer.latestOrderGroupId) }, modifier = Modifier.weight(1f)) { Text("Message") }
                Button(onClick = { actions.saveCustomerNote(storeId, customer.latestOrderGroupId, note) }, modifier = Modifier.weight(1f)) { Text("Save note") }
            }
        }
    }
}

@Composable
private fun SellerReviews(business: ShopSellerBusinessDto, busyIds: Set<String>, actions: SellerBusinessActions) {
    val storeId = business.selectedStore?.id.orEmpty()
    var search by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0) }
    var repliedOnly by remember { mutableStateOf(false) }
    var newestFirst by remember { mutableStateOf(true) }
    SellerBusinessHeading("Reviews", "Reply publicly without exposing buyer identifiers.")
    SellerMetric("Replies", "${business.reviewSummary.replied}/${business.reviewSummary.total}")
    SellerField("Search reviews", search) { search = it }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0 to "All", 5 to "5 star", 4 to "4 star").forEach { (value, label) -> OutlinedButton(onClick = { rating = value }, modifier = Modifier.weight(1f)) { Text(if (rating == value) "$label selected" else label) } }
    }
    SellerCheck("Only reviews with a reply", repliedOnly) { repliedOnly = it }
    TextButton(onClick = { newestFirst = !newestFirst }) { Text(if (newestFirst) "Newest first" else "Oldest first") }
    val reviews = (business.productReviews.map { "product" to it } + business.storeReviews.map { "store" to it })
        .filter { (_, review) -> rating == 0 || review.rating == rating }
        .filter { (_, review) -> !repliedOnly || !review.sellerResponse.isNullOrBlank() }
        .filter { (_, review) -> review.title.orEmpty().contains(search, true) || review.body.orEmpty().contains(search, true) }
        .sortedBy { it.second.createdAt.orEmpty() }.let { if (newestFirst) it.reversed() else it }
    if (reviews.isEmpty()) SellerInfoCard("No product or store reviews yet.")
    reviews.forEach { (type, review) -> SellerReviewCard(storeId, type, review, busyIds, actions) }
}

@Composable
private fun SellerReviewCard(storeId: String, type: String, review: ShopSellerReviewDto, busyIds: Set<String>, actions: SellerBusinessActions) {
    var reply by remember(review.id, review.sellerResponse) { mutableStateOf(review.sellerResponse.orEmpty()) }
    SellerSurface {
        Text("${review.rating}/5 ${humanLabel(type)} review", fontWeight = FontWeight.SemiBold, color = AdisGebeyaColors.PrimaryText)
        Text(review.body.orEmpty().ifBlank { "No written comment." }, color = AdisGebeyaColors.SecondaryText)
        SellerField("Public reply", reply, 2) { reply = it }
        Button(onClick = { actions.replyReview(storeId, review.id, type, reply) }, enabled = reply.isNotBlank() && !busyIds.contains("review-${review.id}")) { Text("Save reply") }
    }
}

@Composable
private fun SellerReturns(business: ShopSellerBusinessDto) {
    SellerBusinessHeading("Returns and refunds", "Seller responses remain available from the Requests tab.")
    if (business.refunds.isEmpty() && business.exchanges.isEmpty()) SellerInfoCard("No refund or exchange requests yet.")
    business.refunds.forEach { SellerInfoCard("Refund: ${humanLabel(it.status)} - ${it.description.orEmpty()}") }
    business.exchanges.forEach { SellerInfoCard("Exchange: ${humanLabel(it.status)} - ${it.description.orEmpty()}") }
}

@Composable
private fun SellerFinance(business: ShopSellerBusinessDto) {
    val finance = business.finance
    SellerBusinessHeading("Finance", "Payment records are read-only and scoped to this store's orders.")
    SellerMetric("Captured sales", sellerMoney(finance.capturedSalesMinor))
    SellerMetric("Pending payment", sellerMoney(finance.pendingMinor))
    SellerMetric("Available to withdraw", finance.withdrawableMinor?.let(::sellerMoney) ?: "Not available")
    SellerInfoCard(if (finance.payoutsAvailable) "Payout tools are available." else "Payouts, settlements and withdrawal methods are not configured yet. No balance is presented as withdrawable.")
    finance.transactions.take(10).forEach { SellerInfoCard("${humanLabel(it.methodCode)} - ${sellerMoney(it.amountMinor)} - ${humanLabel(it.status)}") }
}

@Composable
private fun SellerSettings(business: ShopSellerBusinessDto, actions: SellerBusinessActions) {
    val settings = business.settings
    val storeId = business.selectedStore?.id.orEmpty()
    var orders by remember(settings) { mutableStateOf(settings.notifications["orders"] as? Boolean ?: true) }
    var messages by remember(settings) { mutableStateOf(settings.notifications["messages"] as? Boolean ?: true) }
    var reviews by remember(settings) { mutableStateOf(settings.notifications["reviews"] as? Boolean ?: true) }
    var requests by remember(settings) { mutableStateOf(settings.notifications["returns"] as? Boolean ?: true) }
    var marketing by remember(settings) { mutableStateOf(settings.notifications["marketing"] as? Boolean ?: false) }
    var finance by remember(settings) { mutableStateOf(settings.notifications["finance"] as? Boolean ?: true) }
    var shippingNote by remember(settings) { mutableStateOf(settings.shipping["preference"]?.toString().orEmpty()) }
    var returnNote by remember(settings) { mutableStateOf(settings.returns["preference"]?.toString().orEmpty()) }
    var exchangeNote by remember(settings) { mutableStateOf(settings.exchanges["preference"]?.toString().orEmpty()) }
    var language by remember(settings.language) { mutableStateOf(settings.language) }
    var theme by remember(settings.theme) { mutableStateOf(settings.theme) }
    SellerBusinessHeading("Seller settings", "Preferences are stored for this store.")
    SellerCheck("Order notifications", orders) { orders = it }
    SellerCheck("Message notifications", messages) { messages = it }
    SellerCheck("Review notifications", reviews) { reviews = it }
    SellerCheck("Return and refund notifications", requests) { requests = it }
    SellerCheck("Marketing notifications", marketing) { marketing = it }
    SellerCheck("Finance notifications", finance) { finance = it }
    SellerField("Shipping preference", shippingNote, 2) { shippingNote = it }
    SellerField("Return preference", returnNote, 2) { returnNote = it }
    SellerField("Exchange preference", exchangeNote, 2) { exchangeNote = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { language = "en" }, modifier = Modifier.weight(1f)) { Text(if (language == "en") "English selected" else "English") }
        OutlinedButton(onClick = { language = "am" }, modifier = Modifier.weight(1f)) { Text(if (language == "am") "Amharic selected" else "Amharic") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("system", "light", "dark").forEach { value -> OutlinedButton(onClick = { theme = value }, modifier = Modifier.weight(1f)) { Text(if (theme == value) "${humanLabel(value)} selected" else humanLabel(value)) } }
    }
    SellerInfoCard("Seller PIN, two-factor authentication, staff roles and payout settings are not configured. They are not simulated in this app.")
    Button(onClick = {
        actions.saveSettings(mapOf(
            "storeId" to storeId,
            "notifications" to mapOf("orders" to orders, "messages" to messages, "reviews" to reviews, "returns" to requests, "marketing" to marketing, "finance" to finance),
            "shipping" to mapOf("preference" to shippingNote), "returns" to mapOf("preference" to returnNote), "exchanges" to mapOf("preference" to exchangeNote),
            "security" to emptyMap<String, Any>(), "language" to language, "theme" to theme,
        ))
    }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
}

@Composable private fun SellerBusinessHeading(title: String, subtitle: String) { Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AdisGebeyaColors.PrimaryText); Text(subtitle, color = AdisGebeyaColors.SecondaryText) } }
@Composable private fun SellerInfoCard(message: String) { SellerSurface { Text(message, color = AdisGebeyaColors.SecondaryText) } }
@Composable private fun SellerMetric(label: String, value: String) { SellerSurface { Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f), color = AdisGebeyaColors.SecondaryText); Text(value, fontWeight = FontWeight.Bold, color = AdisGebeyaColors.PrimaryText) } } }
@Composable private fun SellerSurface(content: @Composable () -> Unit) { Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AdisGebeyaColors.Surface), border = BorderStroke(1.dp, AdisGebeyaColors.Divider), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { content() } } }
@Composable private fun SellerField(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, label = { Text(label) }, minLines = lines, maxLines = maxOf(lines, 4), modifier = Modifier.fillMaxWidth(), colors = sellerFieldColors()) }
@Composable private fun SellerNumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier, colors = sellerFieldColors()) }
@Composable private fun SellerCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChange); Text(label, color = AdisGebeyaColors.PrimaryText) } }
@Composable private fun sellerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AdisGebeyaColors.PrimaryText,
    unfocusedTextColor = AdisGebeyaColors.PrimaryText,
    cursorColor = AdisGebeyaColors.Primary,
    focusedLabelColor = AdisGebeyaColors.Primary,
    unfocusedLabelColor = AdisGebeyaColors.SecondaryText,
    focusedBorderColor = AdisGebeyaColors.Primary,
    unfocusedBorderColor = AdisGebeyaColors.Divider,
    focusedContainerColor = AdisGebeyaColors.Surface,
    unfocusedContainerColor = AdisGebeyaColors.Surface,
)
private fun humanLabel(value: String): String = value.replace('_', ' ').trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
private fun sellerMoney(minor: Long): String = "ETB ${String.format(Locale.US, "%,.2f", minor / 100.0)}"
