package com.adischat.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.adischat.app.data.model.AdminStats
import com.adischat.app.data.model.AdminVerificationRequest
import com.adischat.app.data.model.AppUser
import com.adischat.app.network.GameApprovalResponse
import com.adischat.app.ui.theme.Amber
import com.adischat.app.ui.theme.AmberBg
import com.adischat.app.ui.theme.ErrorBg
import com.adischat.app.ui.theme.ErrorRed
import com.adischat.app.ui.theme.GreenBg
import com.adischat.app.ui.theme.InfoBg
import com.adischat.app.ui.theme.InfoBlue
import com.adischat.app.ui.theme.Primary
import com.adischat.app.ui.theme.PrimaryDk
import com.adischat.app.ui.theme.Violet
import com.adischat.app.ui.theme.VioletBg

// ── Entry point ────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen() {
    val vm: AdminViewModel = viewModel()

    val stats          by vm.stats.collectAsStateWithLifecycle()
    val loadingStats   by vm.loadingStats.collectAsStateWithLifecycle()
    val requests       by vm.requests.collectAsStateWithLifecycle()
    val loadingReqs    by vm.loadingRequests.collectAsStateWithLifecycle()
    val requestStatus  by vm.requestStatus.collectAsStateWithLifecycle()
    val gameApprovals  by vm.gameApprovals.collectAsStateWithLifecycle()
    val loadingGameApprovals by vm.loadingGameApprovals.collectAsStateWithLifecycle()
    val gameApprovalStatus by vm.gameApprovalStatus.collectAsStateWithLifecycle()
    val channelRequests by vm.channelRequests.collectAsStateWithLifecycle()
    val loadingChannelRequests by vm.loadingChannelRequests.collectAsStateWithLifecycle()
    val users          by vm.users.collectAsStateWithLifecycle()
    val loadingUsers   by vm.loadingUsers.collectAsStateWithLifecycle()
    val loadingMore    by vm.loadingMore.collectAsStateWithLifecycle()
    val searchQuery    by vm.searchQuery.collectAsStateWithLifecycle()
    val toast          by vm.toast.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    var primaryTab   by remember { mutableStateOf(0) }

    LaunchedEffect(toast) {
        toast?.let { snackbarHost.showSnackbar(it); vm.clearToast() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost   = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Top header ─────────────────────────────────────────────────────
            AdminHeader()

            // ── Primary tab row ────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = primaryTab,
                containerColor   = MaterialTheme.colorScheme.surface,
                contentColor     = InfoBlue,
            ) {
                listOf("Overview", "Verifications", "Game Pots", "Users").forEachIndexed { i, title ->
                    Tab(
                        selected = primaryTab == i,
                        onClick  = { primaryTab = i },
                        text     = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    title,
                                    fontWeight = if (primaryTab == i) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (primaryTab == i) InfoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // Badge on Verifications tab
                                if (i == 1) {
                                    val pending = stats?.pendingVerifications ?: 0
                                    if (pending > 0) {
                                        Spacer(Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = ErrorRed,
                                        ) {
                                            Text(
                                                "$pending",
                                                color    = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // ── Tab content ────────────────────────────────────────────────────
            Box(Modifier.weight(1f)) {
                when (primaryTab) {
                    0 -> OverviewTab(
                        stats        = stats,
                        loading      = loadingStats,
                        onRefresh    = vm::loadStats,
                        onGoVerify   = { primaryTab = 1 },
                        onGoUsers    = { primaryTab = 3 },
                    )
                    1 -> VerificationsTab(
                        requests     = requests,
                        loading      = loadingReqs,
                        status       = requestStatus,
                        onStatusChange = vm::loadRequests,
                        onApprove    = vm::approveVerification,
                        onReject     = vm::rejectVerification,
                        channelRequests = channelRequests,
                        onApproveChannel = vm::approveChannelVerification,
                        onRejectChannel  = { id -> vm.rejectChannelVerification(id, "") },
                    )
                    2 -> GameApprovalsTab(
                        requests = gameApprovals,
                        loading = loadingGameApprovals,
                        status = gameApprovalStatus,
                        onStatusChange = vm::loadGameApprovals,
                        onApprove = vm::approveGame,
                        onReject = vm::rejectGame,
                    )
                    3 -> UsersTab(
                        users        = users,
                        loading      = loadingUsers,
                        loadingMore  = loadingMore,
                        searchQuery  = searchQuery,
                        onSearch     = vm::updateSearch,
                        onLoadMore   = vm::loadNextPage,
                        onSetRole    = vm::setUserRole,
                        onAddMoney   = vm::addWalletMoney,
                        onGrantBadge = vm::grantBlueBadge,
                    )
                }
            }
        }
    }
}

// ── Admin header ───────────────────────────────────────────────────────────────

@Composable
private fun GameApprovalsTab(
    requests: List<GameApprovalResponse>,
    loading: Boolean,
    status: String,
    onStatusChange: (String) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
) {
    var rejectChannelId by remember { mutableStateOf<String?>(null) }
    var rejectNote by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("pending", "approved", "rejected").forEach { item ->
                val selected = status == item
                Button(
                    onClick = { onStatusChange(item) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(item.replaceFirstChar { it.uppercase() }) }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (requests.isEmpty()) {
            Text("No Game Pot approval requests", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            requests.forEach { req ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(req.channelName.ifBlank { req.channelId }, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Owner: ${req.ownerName.ifBlank { req.ownerUid }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(req.description, style = MaterialTheme.typography.bodyMedium)
                        req.adminNote?.takeIf { it.isNotBlank() }?.let {
                            Text("Admin note: $it", color = ErrorRed)
                        }
                        if (req.status == "pending") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onApprove(req.channelId) }, modifier = Modifier.weight(1f)) { Text("Approve") }
                                OutlinedButton(onClick = { rejectChannelId = req.channelId; rejectNote = "" }, modifier = Modifier.weight(1f)) { Text("Reject") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (rejectChannelId != null) {
        AlertDialog(
            onDismissRequest = { rejectChannelId = null },
            title = { Text("Reject Game Pot request") },
            text = {
                OutlinedTextField(
                    value = rejectNote,
                    onValueChange = { rejectNote = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        rejectChannelId?.let { onReject(it, rejectNote) }
                        rejectChannelId = null
                    },
                    enabled = rejectNote.isNotBlank(),
                ) { Text("Reject") }
            },
            dismissButton = { TextButton(onClick = { rejectChannelId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AdminHeader() {
    Surface(
        modifier      = Modifier.fillMaxWidth(),
        color         = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint     = InfoBlue,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Admin Panel", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("AdisChat management console", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(999.dp), color = InfoBg) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Shield, null, tint = InfoBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Admin", color = InfoBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Overview Tab ───────────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(
    stats: AdminStats?,
    loading: Boolean,
    onRefresh: () -> Unit,
    onGoVerify: () -> Unit,
    onGoUsers: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Section header
        SectionHeader("Platform Overview", "Live statistics across all users")
        Spacer(Modifier.height(12.dp))

        if (loading && stats == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = InfoBlue)
            }
        } else {
            // Stats grid — 2 columns
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    value    = "${stats?.totalUsers ?: 0}",
                    label    = "Total Users",
                    icon     = Icons.Default.Group,
                    tint     = InfoBlue,
                    bg       = InfoBg,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value    = "${stats?.blueBadge ?: 0}",
                    label    = "Blue Badges",
                    icon     = Icons.Default.Verified,
                    tint     = Primary,
                    bg       = GreenBg,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    value    = "${stats?.pendingVerifications ?: 0}",
                    label    = "Pending Reviews",
                    icon     = Icons.Default.HourglassBottom,
                    tint     = Amber,
                    bg       = AmberBg,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value    = "${stats?.emailVerified ?: 0}",
                    label    = "Email Verified",
                    icon     = Icons.Default.Email,
                    tint     = Violet,
                    bg       = VioletBg,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))

            // Verification level breakdown
            SectionHeader("Verification Levels", "Distribution of users by level")
            Spacer(Modifier.height(12.dp))
            stats?.let { s ->
                val total = s.totalUsers.coerceAtLeast(1).toFloat()
                LevelBar("Unverified (0)",      s.unverified,      total, MaterialTheme.colorScheme.onSurfaceVariant)
                LevelBar("Email Only (1)",       s.emailVerified,   total, Amber)
                LevelBar("Profile Complete (2)", s.profileComplete, total, Primary)
                LevelBar("Blue Badge (3)",        s.blueBadge,       total, InfoBlue)
            }

            Spacer(Modifier.height(20.dp))

            // Verification stats
            SectionHeader("Verifications", "Request pipeline overview")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStatCard("Pending",  "${stats?.pendingVerifications ?: 0}",  Amber,    AmberBg, Modifier.weight(1f))
                MiniStatCard("Approved", "${stats?.approvedVerifications ?: 0}", Primary,  GreenBg, Modifier.weight(1f))
                MiniStatCard("Rejected", "${stats?.rejectedVerifications ?: 0}", ErrorRed, ErrorBg, Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))

            // Quick actions
            SectionHeader("Quick Actions", "Jump to common tasks")
            Spacer(Modifier.height(12.dp))
            QuickActionCard(
                icon    = Icons.Default.Badge,
                title   = "Review Verifications",
                subtitle = "${stats?.pendingVerifications ?: 0} pending requests",
                tint    = Amber,
                bg      = AmberBg,
                onClick = onGoVerify,
            )
            Spacer(Modifier.height(8.dp))
            QuickActionCard(
                icon    = Icons.Default.ManageAccounts,
                title   = "Manage Users",
                subtitle = "${stats?.totalUsers ?: 0} registered accounts",
                tint    = InfoBlue,
                bg      = InfoBg,
                onClick = onGoUsers,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LevelBar(label: String, count: Int, total: Float, color: Color) {
    val fraction = (count / total).coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(155.dp))
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun StatCard(
    value: String, label: String, icon: ImageVector,
    tint: Color, bg: Color, modifier: Modifier = Modifier,
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniStatCard(label: String, value: String, tint: Color, bg: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(16.dp),
        color    = bg,
    ) {
        Column(
            Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = tint)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector, title: String, subtitle: String,
    tint: Color, bg: Color, onClick: () -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Default.Shield,
                null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Verifications Tab ──────────────────────────────────────────────────────────

@Composable
private fun VerificationsTab(
    requests: List<AdminVerificationRequest>,
    loading: Boolean,
    status: String,
    onStatusChange: (String) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
    channelRequests: List<com.adischat.app.network.ChannelVerificationResponse> = emptyList(),
    onApproveChannel: (String) -> Unit = {},
    onRejectChannel: (String) -> Unit = {},
) {
    var rejectUid    by remember { mutableStateOf<String?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    val tabs    = listOf("Pending", "Approved", "Rejected")
    val tabKeys = listOf("pending", "approved", "rejected")
    val selIdx  = tabKeys.indexOf(status).coerceAtLeast(0)

    Column(Modifier.fillMaxSize()) {
        // Sub-tab row
        TabRow(
            selectedTabIndex = selIdx,
            containerColor   = MaterialTheme.colorScheme.surface,
            contentColor     = InfoBlue,
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = selIdx == i,
                    onClick  = { onStatusChange(tabKeys[i]) },
                    text     = {
                        Text(
                            label,
                            fontWeight = if (selIdx == i) FontWeight.Bold else FontWeight.Normal,
                            color      = if (selIdx == i) InfoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }

        // Request list
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── Channel blue-badge requests (pending tab only) ──────────────────
            if (status == "pending" && channelRequests.isNotEmpty()) {
                Text("Channel blue-badge requests", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                channelRequests.forEach { ch ->
                    ChannelVerifCard(
                        request   = ch,
                        onApprove = { onApproveChannel(ch.channelId) },
                        onReject  = { onRejectChannel(ch.channelId) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(Modifier.height(16.dp))
                Text("User verification", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
            }

            when {
                loading -> Box(
                    Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = InfoBlue) }

                requests.isEmpty() -> EmptyState(
                    icon    = Icons.Default.Badge,
                    title   = "No ${tabs[selIdx].lowercase()} requests",
                    subtitle = "Nothing to review right now",
                )

                else -> requests.forEach { req ->
                    VerifRequestCard(
                        request     = req,
                        showActions = status == "pending",
                        onApprove   = { onApprove(req.uid) },
                        onReject    = { rejectUid = req.uid; rejectReason = "" },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Reject dialog
    if (rejectUid != null) {
        AlertDialog(
            onDismissRequest = { rejectUid = null },
            title = { Text("Reject Verification") },
            text  = {
                Column {
                    Text("Enter rejection reason:", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value         = rejectReason,
                        onValueChange = { rejectReason = it },
                        placeholder   = { Text("e.g. Document unclear…") },
                        shape         = RoundedCornerShape(12.dp),
                        minLines      = 2,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick  = { rejectUid?.let { onReject(it, rejectReason) }; rejectUid = null },
                    enabled  = rejectReason.isNotBlank(),
                ) { Text("Reject", color = ErrorRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { rejectUid = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ChannelVerifCard(
    request: com.adischat.app.network.ChannelVerificationResponse,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier  = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Verified, null, tint = InfoBlue, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    request.channelName.ifBlank { "Channel" },
                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(request.status)
            }
            if (request.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(request.note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            InfoPair("Requested", formatTimestamp(request.createdAt), Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick  = onReject,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                ) { Text("Reject") }
                Button(
                    onClick  = onApprove,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = InfoBlue),
                ) {
                    Icon(Icons.Default.Verified, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Approve")
                }
            }
        }
    }
}

@Composable
private fun VerifRequestCard(
    request: AdminVerificationRequest,
    showActions: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier  = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            // User row
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(name = request.userName, size = 46)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(request.userName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(request.userEmail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(request.status)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(12.dp))
            // Details
            InfoPair("Applicant real name", request.fullName.ifBlank { "Not provided" }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoPair("Document", request.documentType.replace("_", " ").replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
                InfoPair("Phone", request.userPhone, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            InfoPair("Submitted", formatTimestamp(request.submittedAt), Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UploadedVerificationImage(
                    title = "ID document",
                    imageUrl = request.documentUrl,
                    modifier = Modifier.weight(1f),
                )
                UploadedVerificationImage(
                    title = "Applicant photo",
                    imageUrl = request.selfieUrl.ifBlank { request.userPhoto },
                    modifier = Modifier.weight(1f),
                )
            }
            request.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ErrorBg,
                ) {
                    Text(
                        "Rejection reason: $reason",
                        fontSize = 12.sp,
                        color    = ErrorRed,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            if (showActions) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick  = onReject,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    ) { Text("Reject", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick  = onApprove,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) { Text("Approve", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ── Users Tab ──────────────────────────────────────────────────────────────────

@Composable
private fun UploadedVerificationImage(title: String, imageUrl: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(142.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl.isBlank()) {
                Text("No upload", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun UsersTab(
    users: List<AppUser>,
    loading: Boolean,
    loadingMore: Boolean,
    searchQuery: String,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSetRole: (String, String) -> Unit,
    onAddMoney: (String, Double, String) -> Unit,
    onGrantBadge: (String) -> Unit,
) {
    var selectedUser by remember { mutableStateOf<AppUser?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = onSearch,
            modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            placeholder   = { Text("Search by name, username, email, phone…") },
            leadingIcon   = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon  = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearch("") }) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
            },
            shape         = RoundedCornerShape(16.dp),
            singleLine    = true,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = InfoBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            )
        )

        // User list
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            when {
                loading -> Box(
                    Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = InfoBlue) }

                users.isEmpty() && searchQuery.isNotEmpty() -> EmptyState(
                    icon     = Icons.Default.Person,
                    title    = "No results for \"$searchQuery\"",
                    subtitle = "Try a different name, email or phone",
                )

                users.isEmpty() -> EmptyState(
                    icon     = Icons.Default.Group,
                    title    = "No users yet",
                    subtitle = "Users appear here once they sign up",
                )

                else -> {
                    users.forEach { user ->
                        UserListCard(
                            user    = user,
                            onClick = { selectedUser = user },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    // Load more
                    if (users.size % 25 == 0) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            if (loadingMore) {
                                CircularProgressIndicator(color = InfoBlue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                OutlinedButton(
                                    onClick = onLoadMore,
                                    shape   = RoundedCornerShape(12.dp),
                                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = InfoBlue),
                                ) { Text("Load more") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // User detail sheet
    selectedUser?.let { user ->
        ModalBottomSheet(
            onDismissRequest = { selectedUser = null },
            containerColor   = MaterialTheme.colorScheme.surface,
        ) {
            UserDetailSheet(
                user      = user,
                onSetRole = { newRole ->
                    onSetRole(user.uid, newRole)
                    selectedUser = selectedUser?.copy(role = newRole)
                },
                onAddMoney = { amount, reason ->
                    onAddMoney(user.uid, amount, reason)
                },
                onGrantBadge = {
                    onGrantBadge(user.uid)
                    selectedUser = selectedUser?.copy(verificationLevel = 3, verificationRequestStatus = "approved")
                },
                onDismiss = { selectedUser = null },
            )
        }
    }
}

@Composable
private fun UserListCard(user: AppUser, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(name = user.name, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.name.ifBlank { "—" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 14.sp,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    LevelBadge(user.verificationLevel)
                }
                Text(
                    "@${user.username}  ·  ${user.email.ifBlank { user.phone }}",
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            RoleBadge(user.role)
        }
    }
}

@Composable
private fun LevelBadge(level: Int) {
    val (text, tint, bg) = when (level) {
        3    -> Triple("🔵 L3", InfoBlue, InfoBg)
        2    -> Triple("✓ L2", Primary, GreenBg)
        1    -> Triple("✉ L1", Amber, AmberBg)
        else -> return  // show nothing for level 0
    }
    Surface(shape = RoundedCornerShape(999.dp), color = bg) {
        Text(text, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

@Composable
private fun RoleBadge(role: String) {
    val (tint, bg) = when (role) {
        "admin" -> InfoBlue to InfoBg
        "merchant" -> Primary to GreenBg
        else -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(shape = RoundedCornerShape(999.dp), color = bg) {
        Text(
            role.replaceFirstChar { it.uppercase() },
            color    = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ── User Detail Sheet ──────────────────────────────────────────────────────────

@Composable
private fun UserDetailSheet(
    user: AppUser,
    onSetRole: (String) -> Unit,
    onAddMoney: (Double, String) -> Unit,
    onGrantBadge: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmRole by remember { mutableStateOf<String?>(null) }
    var walletAmount by remember { mutableStateOf("") }
    var walletReason by remember { mutableStateOf("Admin wallet top-up") }
    val selectedRole = confirmRole
    val roleOptions = listOf(
        Triple("user", "Make User", MaterialTheme.colorScheme.onSurfaceVariant),
        Triple("merchant", "Make Merchant", Primary),
        Triple("admin", "Make Admin", InfoBlue),
    ).filter { it.first != user.role }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("User Details", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
        }
        Spacer(Modifier.height(16.dp))

        // Avatar + identity
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(name = user.name, size = 60)
            Spacer(Modifier.width(14.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.name.ifBlank { "(no name)" }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    LevelBadge(user.verificationLevel)
                }
                Text("@${user.username}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))

        // Info card
        Card(
            shape     = RoundedCornerShape(18.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
            modifier  = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                DetailRow2("Email",   user.email.ifBlank { "—" })
                AdminDivider()
                DetailRow2("Phone",   user.phone.ifBlank { "—" })
                AdminDivider()
                DetailRow2("UID",     user.uid.take(12) + "…")
                AdminDivider()
                DetailRow2("Role",    user.role.replaceFirstChar { it.uppercase() })
                AdminDivider()
                val levelText = when (user.verificationLevel) {
                    3 -> "Level 3 — Blue Badge"
                    2 -> "Level 2 — Profile Complete"
                    1 -> "Level 1 — Email Verified"
                    else -> "Level 0 — Unverified"
                }
                DetailRow2("Verification", levelText)
                user.verificationRequestStatus?.let {
                    AdminDivider()
                    DetailRow2("Request Status", it.replaceFirstChar { c -> c.uppercase() })
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Text("Wallet permission", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Add money to wallet", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("Admin-only secure top-up", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = walletAmount,
                    onValueChange = { value -> walletAmount = value.filter { it.isDigit() || it == '.' }.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount ETB") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null, tint = Primary) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = walletReason,
                    onValueChange = { walletReason = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reason") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        onAddMoney(walletAmount.toDoubleOrNull() ?: 0.0, walletReason)
                        walletAmount = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Money", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Text("Blue badge verification", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                if (user.verificationLevel >= 3) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, null, tint = InfoBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("This user already has the blue badge", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                } else {
                    Text("Approve blue badge", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(
                        "Grant the verified blue badge directly. A request-based flow will be added later.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick  = onGrantBadge,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = InfoBlue),
                    ) {
                        Icon(Icons.Default.Verified, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Grant Blue Badge", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Text("Change role", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        roleOptions.forEach { (role, label, tint) ->
            Button(
                onClick  = { confirmRole = role },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = tint),
            ) {
                Icon(Icons.Default.ManageAccounts, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(18.dp))
    }

    // Confirm dialog
    if (selectedRole != null) {
        val roleTitle = selectedRole.replaceFirstChar { it.uppercase() }
        val actionTint = when (selectedRole) {
            "admin" -> InfoBlue
            "merchant" -> Primary
            else -> ErrorRed
        }
        AlertDialog(
            onDismissRequest = { confirmRole = null },
            title   = { Text("Change role to $roleTitle") },
            text    = {
                val msg = when (selectedRole) {
                    "admin" -> "This will give ${user.name} full admin access to the Admin Panel."
                    "merchant" -> "This will allow ${user.name} to collect merchant scan payments."
                    else -> "This will remove merchant/admin access from ${user.name}."
                }
                Text(msg)
            },
            confirmButton = {
                TextButton(onClick = { onSetRole(selectedRole); confirmRole = null }) {
                    Text("Confirm", color = actionTint, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRole = null }) { Text("Cancel") }
            },
        )
    }
}

// ── Shared helpers ─────────────────────────────────────────────────────────────

@Composable
private fun UserAvatar(name: String, size: Int) {
    val initial = name.firstOrNull()?.uppercase() ?: "?"
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(PrimaryDk, Primary))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            color      = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize   = (size * 0.4f).sp,
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (tint, bg) = when (status) {
        "approved" -> Primary  to GreenBg
        "rejected" -> ErrorRed to ErrorBg
        else       -> Amber    to AmberBg
    }
    Surface(shape = RoundedCornerShape(999.dp), color = bg) {
        Text(
            status.replaceFirstChar { it.uppercase() },
            color    = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun InfoPair(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DetailRow2(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AdminDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title,    fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(subtitle, fontSize   = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(title,    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize   = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    if (epochMs == 0L) return "—"
    val diff = System.currentTimeMillis() - epochMs
    return when {
        diff < 60_000L        -> "Just now"
        diff < 3_600_000L     -> "${diff / 60_000}m ago"
        diff < 86_400_000L    -> "${diff / 3_600_000}h ago"
        diff < 604_800_000L   -> "${diff / 86_400_000}d ago"
        else                  -> "${diff / 604_800_000}w ago"
    }
}
