package com.adischat.app.ui.gamepot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adischat.app.ui.localization.tr

// - Preset tokens -

private val ticketCountPresets   = listOf(50, 100, 250, 500)
private val durationPresets      = listOf(
    "30 min" to 1_800L,
    "1 hr"   to 3_600L,
    "3 hrs"  to 10_800L,
    "6 hrs"  to 21_600L,
)
private val commissionPresets    = listOf(5.0, 10.0, 15.0, 20.0)

// - Screen -

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGameScreen(
    channelId:  String,
    onBack:     () -> Unit,
    onCreated:  () -> Unit,
    vm:      GamePotViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    // - Form fields -
    var title      by remember { mutableStateOf("") }
    var ticketCountIndex    by remember { mutableIntStateOf(1) }      // default 100
    var customTicketCount   by remember { mutableStateOf("") }
    var priceText      by remember { mutableStateOf("") }
    var commissionIndex     by remember { mutableIntStateOf(0) }      // default 5%
    var durationIndex       by remember { mutableIntStateOf(0) }      // default 30 min
    var customDurationMins  by remember { mutableStateOf("") }
    var maxPerUserText      by remember { mutableStateOf("") }
    var useCustomTickets    by remember { mutableStateOf(false) }
    var useCustomDuration   by remember { mutableStateOf(false) }
    var titleError      by remember { mutableStateOf(false) }
    var priceError      by remember { mutableStateOf(false) }
    var approvalDescription by remember { mutableStateOf("") }

    // - Channel game-approval gate -
    LaunchedEffect(channelId) { vm.loadApproval(channelId) }
    val approval = state.approval
    val approved = approval?.status == "approved"
    // - Derived values -
    val totalTickets: Int = if (useCustomTickets)
        customTicketCount.toIntOrNull() ?: 0
    else ticketCountPresets[ticketCountIndex]

    val price: Double = priceText.toDoubleOrNull() ?: 0.0

    val durationSeconds: Long = if (useCustomDuration)
        (customDurationMins.toLongOrNull() ?: 30L) * 60L
    else durationPresets[durationIndex].second

    val commission: Double = commissionPresets[commissionIndex]

    val jackpotEstimate: Double =
        totalTickets * price * (1.0 - commission / 100.0)

    // - Collect one-shot events -
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
        when (event) {
        is GamePotEvent.NavigateToRoom -> onCreated()
        else -> Unit
        }
        }
    }

    Scaffold(
        topBar = {
        TopAppBar(
        title = {
        Text(
        "Create Game Pot",
        fontWeight = FontWeight.Bold,
        fontSize   = 18.sp,
        )
        },
        navigationIcon = {
        IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        },
        colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        )
        )
        }
    ) { padding ->
        Column(
        modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

        // - Header banner -
        GradientBanner()

        if (!approved) {
        // Channel must be approved by an AdisChat admin before any game
        // can be created / started. Show the request-approval card.
        GameApprovalRequestCard(
        status      = approval?.status ?: "none",
        adminNote      = approval?.adminNote ?: "",
        description      = approvalDescription,
        isSubmitting      = state.isRequestingApproval,
        onDescriptionChange = { approvalDescription = it },
        onSubmit      = { vm.requestApproval(channelId, approvalDescription.trim()) },
        )
        } else {

        // - Title -
        SectionLabel("Game Title")
        OutlinedTextField(
        value      = title,
        onValueChange = { title = it; titleError = false },
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = { Text(tr("e.g. Friday Night Jackpot")) },
        isError       = titleError,
        supportingText = if (titleError) {{ Text(tr("Title is required")) }} else null,
        singleLine    = true,
        )

        // - Ticket count -
        SectionLabel("Total Tickets")
        PresetChips(
        labels    = ticketCountPresets.map { it.toString() } + listOf("Custom"),
        selected  = if (useCustomTickets) ticketCountPresets.size else ticketCountIndex,
        onSelect  = { idx ->
        useCustomTickets = idx == ticketCountPresets.size
        if (!useCustomTickets) ticketCountIndex = idx
        },
        )
        AnimatedVisibility(visible = useCustomTickets) {
        OutlinedTextField(
        value      = customTicketCount,
        onValueChange = { customTicketCount = it.filter(Char::isDigit) },
        modifier      = Modifier.fillMaxWidth(),
        label      = { Text(tr("Ticket count")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine    = true,
        )
        }

        // - Price -
        SectionLabel("Price per Ticket (ETB)")
        OutlinedTextField(
        value      = priceText,
        onValueChange = { priceText = it; priceError = false },
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = { Text(tr("e.g. 500")) },
        isError       = priceError,
        supportingText = if (priceError) {{ Text(tr("Enter a valid price")) }} else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix      = { Text(tr("ETB")) },
        singleLine    = true,
        )

        // - Admin commission -
        SectionLabel("Admin Commission")
        PresetChips(
        labels   = commissionPresets.map { "${it.toInt()}%" },
        selected = commissionIndex,
        onSelect = { commissionIndex = it },
        )

        // - Duration -
        SectionLabel("Game Duration")
        PresetChips(
        labels    = durationPresets.map { it.first } + listOf("Custom"),
        selected  = if (useCustomDuration) durationPresets.size else durationIndex,
        onSelect  = { idx ->
        useCustomDuration = idx == durationPresets.size
        if (!useCustomDuration) durationIndex = idx
        },
        )
        AnimatedVisibility(visible = useCustomDuration) {
        OutlinedTextField(
        value      = customDurationMins,
        onValueChange = { customDurationMins = it.filter(Char::isDigit) },
        modifier      = Modifier.fillMaxWidth(),
        label      = { Text(tr("Duration (minutes)")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine    = true,
        )
        }

        // - Max tickets per user -
        SectionLabel("Max Tickets Per User (optional)")
        OutlinedTextField(
        value      = maxPerUserText,
        onValueChange = { maxPerUserText = it.filter(Char::isDigit) },
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = { Text(tr("Leave blank for unlimited")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine    = true,
        )

        // - Jackpot estimate card -
        JackpotEstimateCard(
        totalTickets   = totalTickets,
        price      = price,
        commission     = commission,
        jackpot      = jackpotEstimate,
        )

        // - Error message -
        state.errorMessage?.let { msg ->
        Card(
        colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth(),
        ) {
        Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Icon(
        Icons.Default.Info,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
        text  = msg,
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.bodyMedium,
        )
        }
        }
        }

        // - Create button -
        Button(
        onClick = {
        titleError = title.isBlank()
        priceError = price <= 0.0 || priceText.isBlank()
        if (!titleError && !priceError && totalTickets > 0) {
        vm.createGame(
        channelId      = channelId,
        title      = title.trim(),
        totalTickets       = totalTickets,
        pricePerTicket     = price,
        adminCommissionPct = commission,
        maxTicketsPerUser  = maxPerUserText.toIntOrNull(),
        durationSeconds    = durationSeconds,
        )
        }
        },
        modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
        enabled  = !state.isLoading,
        shape    = RoundedCornerShape(14.dp),
        ) {
        if (state.isLoading) {
        CircularProgressIndicator(
        modifier  = Modifier.size(22.dp),
        color     = MaterialTheme.colorScheme.onPrimary,
        strokeWidth = 2.5.dp,
        )
        } else {
        Text(
        "Create Game Pot",
        fontSize   = 16.sp,
        fontWeight = FontWeight.Bold,
        )
        }
        }

        Spacer(Modifier.height(16.dp))
        } // end approved form
        }
    }
}

// - Sub-composables -

@Composable
private fun GameApprovalRequestCard(
    status: String,
    adminNote: String,
    description: String,
    isSubmitting: Boolean,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val title = when (status) {
        "pending" -> "Game approval pending"
        "rejected" -> "Game approval rejected"
        else -> "Request Game Pot approval"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(
        "AdisChat admin must approve this channel before a Game Pot can be created.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        )
        if (adminNote.isNotBlank()) {
        Text("Admin note: $adminNote", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (status != "pending") {
        OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(tr("Describe the game, rules, and prize")) },
        minLines = 3,
        )
        Button(
        onClick = onSubmit,
        enabled = !isSubmitting && description.trim().length >= 10,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        ) {
        if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text(tr("Submit for Approval"), fontWeight = FontWeight.Bold)
        }
        }
        }
    }
}

@Composable
private fun GradientBanner() {
    Box(
        modifier = Modifier
        .fillMaxWidth()
        .height(90.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(
        Brush.horizontalGradient(
        listOf(Color(0xFF7C3AED), Color(0xFFDB2777))
        )
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr("-"), fontSize = 32.sp)
        Text(
        "Channel Game Pot",
        color      = Color.White,
        fontWeight = FontWeight.ExtraBold,
        fontSize   = 16.sp,
        )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PresetChips(
    labels:   List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        labels.forEachIndexed { index, label ->
        val isSelected = index == selected
        Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant
        )
        .border(
        width = if (isSelected) 0.dp else 1.dp,
        color = MaterialTheme.colorScheme.outline,
        shape = RoundedCornerShape(8.dp),
        )
        .clickable { onSelect(index) }
        .padding(horizontal = 14.dp, vertical = 8.dp)
        .weight(1f, fill = false),
        ) {
        Text(
        text  = label,
        color = if (isSelected)
        MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        }
        }
    }
}

@Composable
private fun JackpotEstimateCard(
    totalTickets: Int,
    price: Double,
    commission: Double,
    jackpot: Double,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
        Text(
        "Jackpot Estimate",
        fontWeight = FontWeight.Bold,
        style      = MaterialTheme.typography.titleSmall,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        EstimateRow("Tickets",     "$totalTickets")
        EstimateRow("Price each",  "$price ETB")
        EstimateRow("Gross pool",  "${totalTickets * price} ETB")
        EstimateRow("Commission",  "${commission.toInt()}%  (${totalTickets * price * commission / 100} ETB)")
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        ) {
        Text(
        "- Winner gets",
        fontWeight = FontWeight.Bold,
        style      = MaterialTheme.typography.titleSmall,
        )
        Text(
        "${"%.2f".format(jackpot)} ETB",
        fontWeight = FontWeight.ExtraBold,
        color      = MaterialTheme.colorScheme.primary,
        style      = MaterialTheme.typography.titleSmall,
        )
        }
        }
    }
}

@Composable
private fun EstimateRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
