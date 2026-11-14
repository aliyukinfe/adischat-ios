package com.adischat.app.feature.restaurants.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Isolated native table QR check-in and dining session confirmation screen
 * (Phase R3E offline UI foundation). Accepts scanner tokens from a future native
 * camera hook without modifying AdisChat's existing Scan tab or AndroidManifest.
 * Never logs or prints raw QR token strings in UI states or errors.
 */
@Composable
fun RestaurantTableScanScreen(
    state: RestaurantTableScanUiState,
    onScannerTokenReceived: (String) -> Unit,
    onSessionCodeChanged: (String) -> Unit,
    onSubmitSessionCode: () -> Unit,
    onRetryClick: () -> Unit,
    onScanAgainClick: () -> Unit,
    onContinueToMenuClick: (ActiveDiningSessionUi) -> Unit,
    onCancelClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPreviewContent: (@Composable () -> Unit)? = null,
) {
    RestaurantsLightTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = RestaurantsLightTheme.colors.mainBackground,
            topBar = {
                RestaurantsTopBar(
                    title = "Table Check-In",
                    subtitle = "Dine-in session verification",
                    onBackClick = onBackClick,
                    trailingContent = {
                        RestaurantTagPill(
                            label = "Dine-In",
                            containerColor = RestaurantsLightTheme.colors.brandGreenSoft,
                            contentColor = RestaurantsLightTheme.colors.brandGreenDeep,
                        )
                    },
                )
            },
        ) { innerPadding ->
            RestaurantTableScanBody(
                state = state,
                onScannerTokenReceived = onScannerTokenReceived,
                onSessionCodeChanged = onSessionCodeChanged,
                onSubmitSessionCode = onSubmitSessionCode,
                onRetryClick = onRetryClick,
                onScanAgainClick = onScanAgainClick,
                onContinueToMenuClick = onContinueToMenuClick,
                onCancelClick = onCancelClick,
                cameraPreviewContent = cameraPreviewContent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun RestaurantTableScanBody(
    state: RestaurantTableScanUiState,
    onScannerTokenReceived: (String) -> Unit,
    onSessionCodeChanged: (String) -> Unit,
    onSubmitSessionCode: () -> Unit,
    onRetryClick: () -> Unit,
    onScanAgainClick: () -> Unit,
    onContinueToMenuClick: (ActiveDiningSessionUi) -> Unit,
    onCancelClick: () -> Unit,
    cameraPreviewContent: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
            Column(
                modifier = Modifier
                    .widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RestaurantsUiDefaults.ScreenHorizontalPadding, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val phase = state.phase) {
                    is TableScanPhase.Disabled -> {
                        RestaurantsDisabledNoticeCard()
                    }

                    is TableScanPhase.AwaitingScan -> {
                        AwaitingTableQrCard(
                            onSimulateScannerCallback = onScannerTokenReceived,
                            onCancelClick = onCancelClick,
                            cameraPreviewContent = cameraPreviewContent,
                        )
                    }

                    is TableScanPhase.Resolving -> {
                        ResolvingSessionCard(onCancelClick = onCancelClick)
                    }

                    is TableScanPhase.NeedsWaiterActivation -> {
                        WaiterActivationRequiredCard(
                            message = phase.message,
                            onRetryCheckClick = onRetryClick,
                            onScanDifferentTableClick = onScanAgainClick,
                            onCancelClick = onCancelClick,
                        )
                    }

                    is TableScanPhase.NeedsSessionCode -> {
                        SessionCodeEntryCard(
                            promptMessage = phase.promptMessage,
                            validationError = phase.validationError,
                            sessionCodeInput = state.sessionCodeInput,
                            onSessionCodeChanged = onSessionCodeChanged,
                            onSubmitClick = onSubmitSessionCode,
                            onScanAgainClick = onScanAgainClick,
                        )
                    }

                    is TableScanPhase.SessionExpiredOrClosed -> {
                        RestaurantsStateMessageCard(
                            icon = Icons.Rounded.Schedule,
                            title = if (phase.isQrInvalidOrRevoked) {
                                "Table QR code unavailable"
                            } else {
                                "Table session closed"
                            },
                            message = phase.message,
                            actionLabel = "Scan Active Table QR",
                            onActionClick = onScanAgainClick,
                            isError = false,
                        )
                    }

                    is TableScanPhase.Error -> {
                        RestaurantsStateMessageCard(
                            icon = Icons.Rounded.ErrorOutline,
                            title = "Could not verify table",
                            message = phase.message,
                            actionLabel = if (state.hasTokenCaptured) "Retry" else "Scan Again",
                            onActionClick = if (state.hasTokenCaptured) onRetryClick else onScanAgainClick,
                            isError = true,
                        )
                    }

                    is TableScanPhase.Cancelled -> {
                        RestaurantsStateMessageCard(
                            icon = Icons.Rounded.Cancel,
                            title = "Table check-in cancelled",
                            message = "You can scan your table QR code anytime once seated to begin a dine-in order.",
                            actionLabel = "Start Table Scan",
                            onActionClick = onScanAgainClick,
                        )
                    }

                    is TableScanPhase.Confirmed -> {
                        ConfirmedTableSessionCard(
                            session = phase.session,
                            onContinueToMenuClick = { onContinueToMenuClick(phase.session) },
                            onSwitchTableClick = onScanAgainClick,
                        )
                    }
                }
            }
        }
    }

@Composable
private fun AwaitingTableQrCard(
    onSimulateScannerCallback: (String) -> Unit,
    onCancelClick: () -> Unit,
    cameraPreviewContent: (@Composable () -> Unit)? = null,
) {
    var manualCodePreviewInput by rememberSaveable { mutableStateOf("") }

    DineInNoticeBanner(
        headline = "Scan Your Table QR at the Restaurant",
        supportingText = "AdisChat verifies your seated table and dining session before opening table ordering.",
        icon = Icons.Rounded.QrCodeScanner,
    )

    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (cameraPreviewContent != null) {
                cameraPreviewContent()
            } else {
                Surface(
                    shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Point camera at the QR tent on your table",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Native camera scanner hook attaches here when Restaurants goes live.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = manualCodePreviewInput,
                onValueChange = { manualCodePreviewInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                label = { Text("Captured QR token (scanner integration input)") },
                placeholder = { Text("Delivered automatically by scanner") },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSimulateScannerCallback(manualCodePreviewInput) },
                    enabled = manualCodePreviewInput.isNotBlank(),
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                ) {
                    Text("Verify Table")
                }
            }
        }
    }
}

@Composable
private fun ResolvingSessionCard(
    onCancelClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(34.dp))
            Text(
                text = "Checking table session...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Confirming restaurant, branch, and active table status.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = onCancelClick,
                shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun WaiterActivationRequiredCard(
    message: String,
    onRetryCheckClick: () -> Unit,
    onScanDifferentTableClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = extColors.cardSurface,
        border = BorderStroke(1.dp, extColors.qaNoticeBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = extColors.qaNoticeBackground,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Restaurant,
                        contentDescription = null,
                        tint = extColors.qaNoticeText,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "This restaurant requires floor staff to open your dining session before orders can be placed. Once your waiter activates the table, tap Check Again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRetryCheckClick,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Waiter Activated — Check Again")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onScanDifferentTableClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                ) {
                    Text("Scan Another")
                }
                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun SessionCodeEntryCard(
    promptMessage: String,
    validationError: String?,
    sessionCodeInput: String,
    onSessionCodeChanged: (String) -> Unit,
    onSubmitClick: () -> Unit,
    onScanAgainClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Table Session Code Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = promptMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = sessionCodeInput,
                onValueChange = onSessionCodeChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = !validationError.isNullOrBlank(),
                shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                label = { Text("Waiter Session Code") },
                placeholder = { Text("e.g. A7K2") },
                supportingText = if (!validationError.isNullOrBlank()) {
                    { Text(validationError, color = MaterialTheme.colorScheme.error) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmitClick() }),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onScanAgainClick,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                ) {
                    Text("Rescan QR")
                }
                Button(
                    onClick = onSubmitClick,
                    enabled = sessionCodeInput.isNotBlank(),
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                ) {
                    Text("Confirm Code")
                }
            }
        }
    }
}

@Composable
private fun ConfirmedTableSessionCard(
    session: ActiveDiningSessionUi,
    onContinueToMenuClick: () -> Unit,
    onSwitchTableClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = RestaurantsUiDefaults.DineInGreenSoft,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Table Session Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Pay directly at the restaurant after your meal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SessionDetailRow("Restaurant", session.restaurantName)
                SessionDetailRow("Branch", session.branchName)
                SessionDetailRow("Table", session.tableLabel)
                session.expiresAt?.let { SessionDetailRow("Session Valid", it) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onSwitchTableClick,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                ) {
                    Text("Change Table")
                }
                Button(
                    onClick = onContinueToMenuClick,
                    modifier = Modifier.weight(1.3f).height(46.dp),
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                ) {
                    Text("Order From Menu")
                }
            }
        }
    }
}

@Composable
private fun SessionDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RestaurantTableScanWaiterPreview() {
    RestaurantTableScanScreen(
        state = RestaurantTableScanUiState(
            phase = TableScanPhase.NeedsWaiterActivation(),
            pendingQrToken = "preview-opaque-token",
            isDineInEnabled = true,
        ),
        onScannerTokenReceived = {},
        onSessionCodeChanged = {},
        onSubmitSessionCode = {},
        onRetryClick = {},
        onScanAgainClick = {},
        onContinueToMenuClick = {},
        onCancelClick = {},
        onBackClick = {},
    )
}
