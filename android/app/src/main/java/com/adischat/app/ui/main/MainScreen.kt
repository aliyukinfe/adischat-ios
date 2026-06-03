package com.adischat.app.ui.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adischat.app.data.preferences.AppPreferences
import com.adischat.app.ui.UserViewModel
import com.adischat.app.ui.admin.AdminScreen
import com.adischat.app.ui.chat.ChatScreen
import com.adischat.app.ui.discover.DiscoverScreen
import com.adischat.app.ui.home.HomeScreen
import com.adischat.app.ui.profile.ProfileScreen
import com.adischat.app.ui.scan.ScanScreen
import kotlinx.coroutines.launch

private data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val standardNavItems = listOf(
    NavItem("Home",     Icons.Filled.Home,          Icons.Outlined.Home),
    NavItem("Discover", Icons.Filled.Explore,       Icons.Outlined.Explore),
    NavItem("Scan",     Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner),
    NavItem("Chat",     Icons.Filled.Chat,          Icons.Outlined.ChatBubbleOutline),
    NavItem("Profile",  Icons.Filled.Person,        Icons.Outlined.Person),
)

private val adminNavItem = NavItem(
    label         = "Admin",
    selectedIcon  = Icons.Filled.ManageAccounts,
    unselectedIcon = Icons.Outlined.ManageAccounts,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    prefs:     AppPreferences,
    onOpenUrl: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    // Shared user VM — same instance as HomeScreen uses
    val userVm: UserViewModel = viewModel()
    val currentUser by userVm.user.collectAsStateWithLifecycle()
    val isAdmin     = currentUser?.role == "admin"

    val navItems = remember(isAdmin) {
        if (isAdmin) standardNavItems + adminNavItem else standardNavItems
    }

    var selected by remember { mutableIntStateOf(0) }

    // If admin tab disappears (e.g. role changed), snap back to Home
    LaunchedEffect(isAdmin) {
        if (!isAdmin && selected >= standardNavItems.size) selected = 0
    }

    val keyboardOpen   = WindowInsets.isImeVisible
    val isChatSelected = selected == 3
    val lightMode      = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val navSurface     = if (lightMode) Color.White.copy(alpha = 0.96f) else Color(0xFF0B0F0D)
    val navIndicator   = if (lightMode) Color(0xFFEAF7F1) else Color(0xFF223027).copy(alpha = 0.82f)
    val navActive      = if (lightMode) Color(0xFF22C55E) else Color(0xFF7CFF8A)
    val navInactive    = if (lightMode) Color(0xFF4B5563) else Color(0xFF9EA5A0)
    val navSelectedText = if (lightMode) Color(0xFF111827) else Color.White

    // ── Double-back-press to exit ─────────────────────────────────────────────
    val activity          = LocalContext.current as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    var lastBackTime      by remember { mutableLongStateOf(0L) }

    BackHandler {
        if (selected != 0) {
            selected = 0
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackTime < 2_000L) {
                activity?.finish()
            } else {
                lastBackTime = now
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message  = "Press back again to exit",
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!keyboardOpen && !isChatSelected) {
                NavigationBar(
                    containerColor = navSurface,
                    tonalElevation = if (lightMode) 8.dp else 0.dp,
                ) {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick  = { selected = index },
                            icon     = {
                                Icon(
                                    imageVector        = if (selected == index) item.selectedIcon
                                                         else item.unselectedIcon,
                                    contentDescription = item.label,
                                )
                            },
                            label  = { Text(item.label, fontSize = 13.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = navActive,
                                selectedTextColor   = navSelectedText,
                                indicatorColor      = navIndicator,
                                unselectedIconColor = navInactive,
                                unselectedTextColor = navInactive,
                            ),
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(if (isChatSelected) Modifier.fillMaxSize() else Modifier.padding(padding)) {
            when (selected) {
                0 -> HomeScreen(
                    onOpenUrl = onOpenUrl,
                    onGoScan = { selected = 2 },
                    onGoChat = { selected = 3 },
                    onGoWallet = { selected = 4 },
                )
                1 -> DiscoverScreen(onOpenUrl = onOpenUrl)
                2 -> ScanScreen(
                    prefs = prefs,
                    onOpenUrl = onOpenUrl,
                    onGoWallet = { selected = 4 },
                )
                3 -> ChatScreen()
                4 -> ProfileScreen(prefs = prefs, onSignOut = onSignOut, onGoScan = { selected = 2 })
                5 -> AdminScreen()
            }
        }
    }
}
