package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adischat.app.feature.shop.data.ShopBenefitBrandDto
import com.adischat.app.feature.shop.data.ShopBenefitBrandEventDto
import com.adischat.app.feature.shop.data.ShopBenefitBrandRequest
import com.adischat.app.feature.shop.data.ShopBenefitFlags
import com.adischat.app.feature.shop.data.ShopBenefitMergeReviewDto
import com.adischat.app.feature.shop.data.ShopBenefitMergeRequest
import com.adischat.app.feature.shop.data.ShopBenefitsRepository
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun AdminBenefitBrandPanel() {
    if (!ShopBenefitFlags.managementEnabled) return
    val repository = remember { ShopBenefitsRepository() }
    val scope = rememberCoroutineScope()
    var brands by remember { mutableStateOf<List<ShopBenefitBrandDto>>(emptyList()) }
    var events by remember { mutableStateOf<List<ShopBenefitBrandEventDto>>(emptyList()) }
    var reviews by remember { mutableStateOf<List<ShopBenefitMergeReviewDto>>(emptyList()) }
    var selected by remember { mutableStateOf<ShopBenefitBrandDto?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf("create") }
    var name by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        repository.adminBrandSnapshot().onSuccess {
            brands = it.benefitBrands
            events = it.benefitBrandEvents
            reviews = it.benefitMergeReviews
        }
            .onFailure { message = "Brands could not be loaded." }
    }
    LaunchedEffect(Unit) { refresh() }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE6E7F0))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Canonical brands", color = BenefitInk)
            OutlinedButton(onClick = { expanded = true }, enabled = !busy) {
                Text(selected?.canonicalName ?: "Select brand")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                brands.forEach { brand ->
                    DropdownMenuItem(text = { Text("${brand.canonicalName} · ${brand.status}") }, onClick = {
                        selected = brand
                        expanded = false
                    })
                }
            }
            selected?.aliases?.takeIf { it.isNotEmpty() }?.let { aliases ->
                Text("Aliases: ${aliases.joinToString()}", color = Color(0xFF747A94))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("create", "rename", "add_alias", "retire_alias", "retire").forEach { option ->
                    OutlinedButton(onClick = { action = option; message = null }, enabled = !busy) {
                        Text(option.replace('_', ' '))
                    }
                }
            }
            if (action != "retire") {
                OutlinedTextField(name, { name = it.take(120) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (action in listOf("add_alias", "retire_alias")) "Brand alias" else "Canonical brand name") })
            }
            if (action in listOf("retire", "retire_alias")) {
                OutlinedTextField(reason, { reason = it.take(500) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Retirement reason") })
            }
            Button(onClick = {
                busy = true
                scope.launch {
                    val result = repository.adminManageBrand(ShopBenefitBrandRequest(
                        brandAction = action,
                        brandId = if (action == "create") null else selected?.id,
                        name = name.takeIf { action != "retire" },
                        expectedVersion = if (action == "create") null else selected?.version,
                        reason = reason.takeIf { action in listOf("retire", "retire_alias") },
                        requestKey = UUID.randomUUID().toString(),
                    ))
                    message = if (result.isSuccess) "Brand record updated." else "Brand action failed. Refresh and check the name."
                    if (result.isSuccess) {
                        refresh()
                        selected = brands.firstOrNull { it.id == result.getOrNull()?.id } ?: result.getOrNull()
                        name = ""
                        reason = ""
                    }
                    busy = false
                }
            }, enabled = !busy && (
                action == "create" && name.trim().length >= 2 ||
                action in listOf("rename", "add_alias") && selected?.status == "active" && name.trim().length >= 2 ||
                action == "retire_alias" && selected?.status == "active" &&
                    selected?.aliases?.contains(name.trim().lowercase()) == true && reason.trim().length >= 4 ||
                action == "retire" && selected?.status == "active" && reason.trim().length >= 4
            )) { Text("Save brand") }
            events.filter { it.brandId == selected?.id }.take(5).forEach { event ->
                Text("${event.action.replace('_', ' ')} · ${event.normalizedName.orEmpty()}", color = Color(0xFF747A94))
            }
            AdminBenefitMergePanel(brands, reviews, selected, repository) { scope.launch { refresh() } }
            message?.let { Text(it, color = BenefitInk) }
        }
    }
}

@Composable
private fun AdminBenefitMergePanel(
    brands: List<ShopBenefitBrandDto>,
    reviews: List<ShopBenefitMergeReviewDto>,
    source: ShopBenefitBrandDto?,
    repository: ShopBenefitsRepository,
    onUpdated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var target by remember(source?.id) { mutableStateOf<ShopBenefitBrandDto?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Text("Potential duplicate review", color = BenefitInk)
    OutlinedButton(onClick = { expanded = true }, enabled = !busy && source != null) {
        Text(target?.canonicalName ?: "Select second brand")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        brands.filter { it.id != source?.id && it.status == "active" }.forEach { brand ->
            DropdownMenuItem(text = { Text(brand.canonicalName) }, onClick = {
                target = brand
                expanded = false
            })
        }
    }
    OutlinedTextField(reason, { reason = it.take(500) }, modifier = Modifier.fillMaxWidth(),
        label = { Text("Review reason") })
    OutlinedButton(onClick = {
        val first = source?.id ?: return@OutlinedButton
        val second = target?.id ?: return@OutlinedButton
        busy = true
        scope.launch {
            val result = repository.adminManageMerge(ShopBenefitMergeRequest(
                brandAction = "merge_request", sourceBrandId = first, targetBrandId = second,
                reason = reason, requestKey = UUID.randomUUID().toString(),
            ))
            message = if (result.isSuccess) "Duplicate review requested." else "Duplicate review could not be requested."
            if (result.isSuccess) onUpdated()
            busy = false
        }
    }, enabled = !busy && source?.status == "active" && target?.status == "active" && reason.trim().length >= 4) {
        Text("Request review")
    }
    reviews.take(10).forEach { review ->
        val first = brands.firstOrNull { it.id == review.sourceBrandId }?.canonicalName ?: "Brand"
        val second = brands.firstOrNull { it.id == review.targetBrandId }?.canonicalName ?: "Brand"
        Text("$first / $second · ${review.status.replace('_', ' ')}", color = BenefitInk)
        if (review.status == "pending") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("confirm_duplicate", "reject").forEach { decision ->
                    OutlinedButton(onClick = {
                        busy = true
                        scope.launch {
                            val result = repository.adminManageMerge(ShopBenefitMergeRequest(
                                brandAction = "merge_decide", reviewId = review.id,
                                expectedVersion = review.version, decision = decision,
                                reason = reason, requestKey = UUID.randomUUID().toString(),
                            ))
                            message = if (result.isSuccess) "Duplicate review recorded." else "Decision failed. Refresh the review."
                            if (result.isSuccess) onUpdated()
                            busy = false
                        }
                    }, enabled = !busy && reason.trim().length >= 4) {
                        Text(if (decision == "confirm_duplicate") "Confirm duplicate" else "Reject")
                    }
                }
            }
        }
    }
    message?.let { Text(it, color = BenefitInk) }
}
