package com.adischat.app.feature.shop.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adischat.app.feature.shop.data.ShopBenefitBrandDto
import com.adischat.app.feature.shop.data.ShopBenefitCaseDto
import com.adischat.app.feature.shop.data.ShopBenefitCreateRequest
import com.adischat.app.feature.shop.data.ShopBenefitDecisionRequest
import com.adischat.app.feature.shop.data.ShopBenefitFlags
import com.adischat.app.feature.shop.data.ShopBenefitEvidenceDto
import com.adischat.app.feature.shop.data.ShopBenefitEventDto
import com.adischat.app.feature.shop.data.ShopBenefitsRepository
import com.adischat.app.feature.shop.data.ShopVerifiedBenefitDto
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import java.io.ByteArrayOutputStream

internal val BenefitInk = Color(0xFF1E1B4B)
private val BenefitGold = Color(0xFFD99A12)
private val BenefitBorder = Color(0xFFE6E7F0)

private fun benefitLabel(kind: String): String = when (kind) {
    "original_product" -> "Original product verification"
    "official_store" -> "Official store authorization"
    "seller_warranty" -> "Seller warranty"
    "manufacturer_warranty" -> "Manufacturer warranty"
    else -> "Product benefit"
}

@Composable
internal fun VerifiedBenefitBadges(benefits: List<ShopVerifiedBenefitDto>, maxBadges: Int, modifier: Modifier = Modifier) {
    if (!ShopBenefitFlags.buyerBadgesEnabled) return
    val visible = benefits.filter { it.type in setOf("original_product", "official_store", "seller_warranty", "manufacturer_warranty") }
        .take(maxBadges)
    if (visible.isEmpty()) return
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        visible.forEach { benefit ->
            val icon = when (benefit.type) {
                "official_store" -> Icons.Outlined.Storefront
                "original_product" -> Icons.Outlined.Verified
                "manufacturer_warranty" -> Icons.Outlined.WorkspacePremium
                else -> Icons.Outlined.Shield
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier
                .padding(vertical = 2.dp)) {
                Icon(icon, contentDescription = null, tint = BenefitInk)
                Text(benefit.label, color = BenefitInk, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun VerifiedBenefitDetails(benefits: List<ShopVerifiedBenefitDto>, modifier: Modifier = Modifier) {
    if (!ShopBenefitFlags.buyerBadgesEnabled || benefits.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Verified benefits", color = BenefitInk, fontWeight = FontWeight.SemiBold)
        benefits.filter { it.type in setOf("original_product", "official_store", "seller_warranty", "manufacturer_warranty") }
            .forEach { benefit ->
                Text(benefit.label, color = BenefitInk, fontWeight = FontWeight.Medium)
                benefit.summary?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(0xFF747A94)) }
                benefit.effectiveUntil?.let { Text("Valid until $it", color = Color(0xFF747A94)) }
            }
    }
}

@Composable
internal fun SellerBenefitClaimsPanel(storeId: String, productId: String) {
    if (!ShopBenefitFlags.managementEnabled || storeId.isBlank() || productId.isBlank()) return
    val repo = remember { ShopBenefitsRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var claims by remember(storeId, productId) { mutableStateOf<List<ShopBenefitCaseDto>>(emptyList()) }
    var brands by remember(storeId) { mutableStateOf<List<ShopBenefitBrandDto>>(emptyList()) }
    var kind by remember(productId) { mutableStateOf("original_product") }
    var brandId by remember(storeId) { mutableStateOf<String?>(null) }
    var terms by remember(productId) { mutableStateOf("") }
    var coverage by remember(productId) { mutableStateOf("") }
    var duration by remember(productId) { mutableStateOf("") }
    var documentExpiry by remember(productId) { mutableStateOf("") }
    var busy by remember(storeId, productId) { mutableStateOf(false) }
    var message by remember(storeId, productId) { mutableStateOf<String?>(null) }
    var chooseKind by remember { mutableStateOf(false) }
    var chooseBrand by remember { mutableStateOf(false) }
    var evidenceClaim by remember(storeId, productId) { mutableStateOf<ShopBenefitCaseDto?>(null) }

    suspend fun refresh() {
        repo.sellerClaims(storeId).onSuccess { all ->
            claims = all.filter { it.productId == productId || it.kind == "official_store" }
        }.onFailure { message = "Claims could not be loaded." }
    }
    LaunchedEffect(storeId, productId) {
        refresh()
        repo.brands(storeId).onSuccess { brands = it }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val claim = evidenceClaim ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = runCatching {
                val mime = context.contentResolver.getType(uri) ?: error("Unsupported file")
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > 10 * 1024 * 1024) error("File is too large")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("File unavailable")
                val evidenceType = when (claim.kind) {
                    "official_store" -> "authorization_letter"
                    "manufacturer_warranty" -> "manufacturer_terms"
                    "seller_warranty" -> "seller_terms"
                    else -> "invoice"
                }
                repo.uploadEvidence(storeId, claim.id, bytes, mime, evidenceType,
                    documentExpiry.takeIf { it.isNotBlank() }).getOrThrow()
            }
            message = if (result.isSuccess) "Evidence uploaded." else "Evidence upload failed. Check the file and claim status."
            busy = false
        }
    }

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BenefitBorder),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Product trust claims", color = BenefitInk, fontWeight = FontWeight.SemiBold)
            claims.forEach { claim ->
                Text("${benefitLabel(claim.kind)} · ${(claim.effectiveStatus ?: claim.status).replace('_', ' ')}", color = BenefitInk)
                claim.publicReason?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(0xFF747A94)) }
                claim.expiresAt?.let { Text("Expires $it", color = Color(0xFF747A94)) }
                if (claim.status == "draft") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { evidenceClaim = claim; picker.launch("*/*") },
                            enabled = !busy && (claim.kind !in setOf("official_store", "manufacturer_warranty") || documentExpiry.isNotBlank())) {
                            Text("Add evidence")
                        }
                        Button(onClick = {
                            busy = true
                            scope.launch {
                                val result = repo.submitClaim(storeId, claim)
                                message = if (result.isSuccess) "Submitted for verification." else "Submission failed. Check your evidence."
                                refresh()
                                busy = false
                            }
                        }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = BenefitInk)) {
                            Text("Submit claim")
                        }
                    }
                }
            }
            OutlinedButton(onClick = { chooseKind = true }, enabled = !busy) { Text(benefitLabel(kind)) }
            DropdownMenu(expanded = chooseKind, onDismissRequest = { chooseKind = false }) {
                listOf("original_product", "official_store", "seller_warranty", "manufacturer_warranty").forEach { option ->
                    DropdownMenuItem(text = { Text(benefitLabel(option)) }, onClick = { kind = option; chooseKind = false })
                }
            }
            if (kind in setOf("official_store", "original_product", "manufacturer_warranty")) {
                OutlinedButton(onClick = { chooseBrand = true }, enabled = !busy && brands.isNotEmpty()) {
                    Text(brands.firstOrNull { it.id == brandId }?.canonicalName ?: "Select registered brand")
                }
                DropdownMenu(expanded = chooseBrand, onDismissRequest = { chooseBrand = false }) {
                    brands.forEach { brand ->
                        DropdownMenuItem(text = { Text(brand.canonicalName) }, onClick = {
                            brandId = brand.id
                            chooseBrand = false
                        })
                    }
                }
            }
            if (kind != "official_store") {
                OutlinedTextField(terms, { terms = it }, label = { Text("Claim or warranty terms") }, modifier = Modifier.fillMaxWidth())
                if (kind.endsWith("warranty")) {
                    OutlinedTextField(coverage, { coverage = it }, label = { Text("Coverage details") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(3) },
                        label = { Text("Duration in months") }, modifier = Modifier.fillMaxWidth())
                }
            }
            OutlinedTextField(documentExpiry, { documentExpiry = it },
                label = { Text("Evidence expiry (ISO date, where applicable)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                busy = true
                scope.launch {
                    val result = repo.createClaim(ShopBenefitCreateRequest(
                        storeId, if (kind == "official_store") null else productId,
                        if (kind in setOf("official_store", "original_product", "manufacturer_warranty")) brandId else null,
                        kind, terms, coverage, duration.toIntOrNull(),
                        if (kind == "official_store") "all_brand_products" else null,
                        requestKey = UUID.randomUUID().toString(),
                    ))
                    message = if (result.isSuccess) "Claim draft created. Add evidence before submitting." else "Claim could not be created."
                    refresh()
                    busy = false
                }
            }, enabled = !busy &&
                (kind == "official_store" && brandId != null ||
                 kind == "seller_warranty" && terms.length >= 4 && (duration.toIntOrNull() ?: 0) in 1..120 ||
                 kind == "original_product" && brandId != null && terms.length >= 4 ||
                 kind == "manufacturer_warranty" && brandId != null && terms.length >= 4 && (duration.toIntOrNull() ?: 0) in 1..120),
                colors = ButtonDefaults.buttonColors(containerColor = BenefitInk)) { Text("Create claim draft") }
            message?.let { Text(it, color = BenefitGold) }
        }
    }
}

@Composable
internal fun AdminBenefitReviewPanel(productId: String? = null, storeId: String? = null) {
    if (!ShopBenefitFlags.managementEnabled) return
    val repo = remember { ShopBenefitsRepository() }
    val scope = rememberCoroutineScope()
    var claims by remember(productId, storeId) { mutableStateOf<List<ShopBenefitCaseDto>>(emptyList()) }
    var evidence by remember(productId, storeId) { mutableStateOf<List<ShopBenefitEvidenceDto>>(emptyList()) }
    var evidenceCaseId by remember(productId, storeId) { mutableStateOf<String?>(null) }
    var evidenceToView by remember(productId, storeId) { mutableStateOf<ShopBenefitEvidenceDto?>(null) }
    var reviewedEvidenceIds by remember(productId, storeId) { mutableStateOf<Set<String>>(emptySet()) }
    var history by remember(productId, storeId) { mutableStateOf<List<ShopBenefitEventDto>>(emptyList()) }
    var historyCaseId by remember(productId, storeId) { mutableStateOf<String?>(null) }
    var reason by remember(productId, storeId) { mutableStateOf("") }
    var expiry by remember(productId, storeId) { mutableStateOf("") }
    var busy by remember(productId, storeId) { mutableStateOf(false) }
    var message by remember(productId, storeId) { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val result = when {
            productId != null -> repo.adminProductClaims(productId)
            storeId != null -> repo.adminStoreClaims(storeId)
            else -> repo.adminQueue()
        }
        result.onSuccess { claims = it }.onFailure { message = "Claims could not be loaded." }
    }
    LaunchedEffect(productId, storeId) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Benefit verification", color = BenefitInk, fontWeight = FontWeight.SemiBold)
        if (claims.isEmpty()) Text("No benefit claims in this view.", color = Color(0xFF747A94))
        claims.forEach { claim ->
            Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BenefitBorder)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(benefitLabel(claim.kind), color = BenefitInk, fontWeight = FontWeight.Medium)
                    Text((claim.effectiveStatus ?: claim.status).replace('_', ' '), color = Color(0xFF747A94))
                    TextButton(onClick = {
                        scope.launch {
                            repo.adminEvidence(claim.id).onSuccess {
                                evidenceCaseId = claim.id
                                evidence = it
                                reviewedEvidenceIds = emptySet()
                            }
                                .onFailure { message = "Evidence could not be opened." }
                        }
                    }) { Text("Review evidence") }
                    TextButton(onClick = {
                        scope.launch {
                            repo.adminHistory(claim.id).onSuccess { historyCaseId = claim.id; history = it }
                                .onFailure { message = "Claim history is unavailable." }
                        }
                    }) { Text("History") }
                    history.takeIf { historyCaseId == claim.id }.orEmpty().forEach { event ->
                        Text("${event.action.replace('_', ' ')} · ${event.createdAt.orEmpty()}", color = Color(0xFF747A94))
                    }
                    evidence.takeIf { evidenceCaseId == claim.id }.orEmpty().forEach { item ->
                        TextButton(onClick = { evidenceToView = item }) {
                            Text(item.evidenceType.replace('_', ' '))
                        }
                    }
                    if (claim.status in listOf("submitted", "under_review", "approved")) {
                        OutlinedTextField(reason, { reason = it }, label = { Text("Decision reason") }, modifier = Modifier.fillMaxWidth())
                        if (claim.status != "approved") {
                            OutlinedTextField(expiry, { expiry = it }, label = { Text("Approval expiry (ISO date)") }, modifier = Modifier.fillMaxWidth())
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val actions = if (claim.status == "approved") listOf("revoke") else listOf("approve", "reject")
                            actions.forEach { action ->
                                OutlinedButton(onClick = {
                                    busy = true
                                    scope.launch {
                                        val result = repo.adminDecide(ShopBenefitDecisionRequest(
                                            claim.id, claim.version, action, reason,
                                            if (action == "approve") Instant.now().toString() else null,
                                            if (action == "approve") expiry else null,
                                            UUID.randomUUID().toString(),
                                        ))
                                        message = if (result.isSuccess) "Decision recorded." else "Decision was not recorded. Refresh and review the claim."
                                        refresh()
                                        busy = false
                                    }
                                }, enabled = !busy && (
                                    action == "approve" && expiry.isNotBlank() && evidenceCaseId == claim.id &&
                                        evidence.isNotEmpty() && evidence.all { it.id in reviewedEvidenceIds } ||
                                    action != "approve" && reason.isNotBlank()
                                ),
                                    modifier = Modifier.widthIn(min = 80.dp)) { Text(action.replaceFirstChar { it.uppercase() }) }
                            }
                        }
                    }
                }
            }
        }
        message?.let { Text(it, color = BenefitGold) }
    }
    evidenceToView?.let { item ->
        ShopBenefitEvidenceViewer(item, onDismiss = { evidenceToView = null },
            onReviewed = { reviewedEvidenceIds = reviewedEvidenceIds + it })
    }
}
