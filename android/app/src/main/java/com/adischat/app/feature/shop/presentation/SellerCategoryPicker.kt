package com.adischat.app.feature.shop.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.feature.shop.data.ShopSellingUnitDto
import kotlinx.coroutines.delay

internal fun categoryBreadcrumb(category: ShopCategoryDto, categories: List<ShopCategoryDto>): String {
    val byId = categories.associateBy { it.id }
    val path = mutableListOf(category.name)
    var parentId = category.parentId
    val visited = mutableSetOf(category.id)
    while (!parentId.isNullOrBlank() && visited.add(parentId)) {
        val parent = byId[parentId] ?: break
        path.add(0, parent.name)
        parentId = parent.parentId
    }
    return path.joinToString(" > ")
}

internal fun categoryMatchesQuery(category: ShopCategoryDto, query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return true
    return sequenceOf(category.name, category.slug, category.code)
        .plus(category.searchTerms.asSequence())
        .any { it.lowercase().contains(normalized) }
}

@Composable
internal fun SellerCategoryPicker(
    categories: List<ShopCategoryDto>,
    selectedCategoryId: String,
    onDismiss: () -> Unit,
    onSelect: (ShopCategoryDto) -> Unit,
) {
    val hierarchy = remember(categories) { CategoryHierarchyDefinition.buildAuthoritativeHierarchy(categories) }
    var parentId by rememberSaveable { mutableStateOf<String?>(null) }
    var queryInput by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(queryInput) {
        delay(250)
        query = queryInput.trim()
    }
    val byId = remember(hierarchy) { hierarchy.associateBy { it.id } }
    val currentParent = parentId?.let(byId::get)

    BackHandler(enabled = parentId != null && query.isBlank()) {
        parentId = currentParent?.parentId
    }

    val rows = remember(hierarchy, parentId, query) {
        if (query.isNotBlank()) {
            hierarchy.filter { it.isLeaf && it.sellerSelectable && categoryMatchesQuery(it, query) }
                .sortedBy { categoryBreadcrumb(it, hierarchy) }
        } else {
            hierarchy.filter {
                if (parentId.isNullOrBlank()) it.parentId.isNullOrBlank()
                else it.parentId == parentId
            }.sortedWith(compareBy({ it.displayOrder }, { it.name }))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentParent != null && query.isBlank()) {
                    IconButton(onClick = { parentId = currentParent.parentId }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous category")
                    }
                }
                Column {
                    Text(if (currentParent == null) "Choose category" else currentParent.name, fontWeight = FontWeight.SemiBold)
                    if (currentParent != null) Text(categoryBreadcrumb(currentParent, hierarchy), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = queryInput,
                    onValueChange = { queryInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Search categories (e.g. Jeans, Shoes)...") },
                )
                if (rows.isEmpty()) {
                    Text(
                        if (categories.isEmpty()) "Categories are loading or unavailable." else "No matching category.",
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
                        items(rows, key = { it.id }) { category ->
                            val hasChildren = hierarchy.any { it.parentId == category.id && it.buyerVisible }
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (category.isLeaf && category.sellerSelectable) onSelect(category)
                                    else if (hasChildren) parentId = category.id
                                },
                                color = if (category.id == selectedCategoryId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(categoryIcon(category), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(category.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (query.isNotBlank()) {
                                            Text(categoryBreadcrumb(category, hierarchy), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                        }
                                    }
                                    if (!category.isLeaf || hasChildren) Icon(Icons.Default.ChevronRight, contentDescription = "Open ${category.name}")
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
internal fun SellingUnitPicker(
    units: List<ShopSellingUnitDto>,
    categoryId: String,
    selectedCode: String,
    onDismiss: () -> Unit,
    onSelect: (ShopSellingUnitDto) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val suggested = remember(categoryId) { suggestedSellingUnitCodes(categoryId) }
    val rows = remember(units, categoryId, selectedCode, query) {
        orderSellingUnits(units, categoryId, selectedCode).filter { unit ->
            query.isBlank() || unit.name.contains(query, true) || unit.symbol.contains(query, true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selling unit", fontWeight = FontWeight.SemiBold) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search units") })
                if (suggested.isNotEmpty() && query.isBlank()) {
                    Text("Suggested units first. All active units remain available.", style = MaterialTheme.typography.labelSmall)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(rows, key = { it.code }) { unit ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSelect(unit) },
                            color = if (unit.code == selectedCode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        ) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 6.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(unit.name, fontWeight = FontWeight.Medium)
                                    if (query.isBlank()) {
                                        val hint = when {
                                            unit.code == selectedCode && unit.code !in suggested -> "Current unit"
                                            unit.code in suggested -> "Suggested for this category"
                                            else -> null
                                        }
                                        if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Text(unit.symbol, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
    )
}

