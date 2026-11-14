package com.adischat.app.feature.restaurants.qa

import com.adischat.app.feature.restaurants.admin.AdminApplicationDocUi
import com.adischat.app.feature.restaurants.admin.AdminApplicationUi
import com.adischat.app.feature.restaurants.admin.AdminAuditEntryUi
import com.adischat.app.feature.restaurants.admin.AdminRestaurantOverviewUi
import com.adischat.app.feature.restaurants.admin.RestaurantAdminTab
import com.adischat.app.feature.restaurants.admin.RestaurantAdminUiState
import com.adischat.app.feature.restaurants.center.CenterApplicationDocUi
import com.adischat.app.feature.restaurants.center.CenterApplicationUi
import com.adischat.app.feature.restaurants.center.CenterBranchUi
import com.adischat.app.feature.restaurants.center.CenterMenuItemUi
import com.adischat.app.feature.restaurants.center.CenterOrderTicketUi
import com.adischat.app.feature.restaurants.center.CenterRestaurantUi
import com.adischat.app.feature.restaurants.center.CenterStaffInvitationUi
import com.adischat.app.feature.restaurants.center.CenterStaffMemberUi
import com.adischat.app.feature.restaurants.center.CenterTableUi
import com.adischat.app.feature.restaurants.center.CenterUploadPhase
import com.adischat.app.feature.restaurants.center.CenterUploadUiState
import com.adischat.app.feature.restaurants.center.OrderBoardFilter
import com.adischat.app.feature.restaurants.center.RestaurantApplicationStatus
import com.adischat.app.feature.restaurants.center.RestaurantCenterTab
import com.adischat.app.feature.restaurants.center.RestaurantCenterUiState
import com.adischat.app.feature.restaurants.center.RestaurantOperationalStatus
import com.adischat.app.feature.restaurants.center.RestaurantStaffRole
import com.adischat.app.feature.restaurants.center.RestaurantsPendingPickerTarget
import com.adischat.app.feature.restaurants.data.MenuCategoryDto
import com.adischat.app.feature.restaurants.data.RestaurantsBinaryUploadSource
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RestaurantsFailure
import com.adischat.app.feature.restaurants.data.SubmitOrderResultDto
import com.adischat.app.feature.restaurants.presentation.ActiveDiningSessionUi
import com.adischat.app.feature.restaurants.presentation.CartCheckoutPhase
import com.adischat.app.feature.restaurants.presentation.CartMutationOutcome
import com.adischat.app.feature.restaurants.presentation.CheckoutSheetStep
import com.adischat.app.feature.restaurants.presentation.DineInCartItemUi
import com.adischat.app.feature.restaurants.presentation.DineInCartReducers
import com.adischat.app.feature.restaurants.presentation.DineInOrderMode
import com.adischat.app.feature.restaurants.presentation.MenuItemSheetState
import com.adischat.app.feature.restaurants.presentation.MenuItemUiModel
import com.adischat.app.feature.restaurants.presentation.OrderTimelineStepUi
import com.adischat.app.feature.restaurants.presentation.OrderTrackingReducers
import com.adischat.app.feature.restaurants.presentation.OrderTrackingSyncState
import com.adischat.app.feature.restaurants.presentation.PaymentStatusUi
import com.adischat.app.feature.restaurants.presentation.QuickAddOutcome
import com.adischat.app.feature.restaurants.presentation.QuickDecrementOutcome
import com.adischat.app.feature.restaurants.presentation.RestaurantCartUiState
import com.adischat.app.feature.restaurants.presentation.RestaurantMenuUiState
import com.adischat.app.feature.restaurants.presentation.RestaurantOrderIsolationReducers
import com.adischat.app.feature.restaurants.presentation.RestaurantOrderStatus
import com.adischat.app.feature.restaurants.presentation.RestaurantOrderSummaryUi
import com.adischat.app.feature.restaurants.presentation.RestaurantOrderTrackingUiState
import com.adischat.app.feature.restaurants.presentation.RestaurantTableScanUiState
import com.adischat.app.feature.restaurants.presentation.RestaurantWorkspaceTab
import com.adischat.app.feature.restaurants.presentation.RestaurantsDiscoverUiState
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadState
import com.adischat.app.feature.restaurants.presentation.RestaurantsOrderingPreviewFixtures
import com.adischat.app.feature.restaurants.presentation.RestaurantsPreviewFixtures
import com.adischat.app.feature.restaurants.presentation.RestaurantsUiReducers
import com.adischat.app.feature.restaurants.presentation.SelectedCartOptionUi
import com.adischat.app.feature.restaurants.presentation.TableQrScanReducers
import com.adischat.app.feature.restaurants.presentation.TableScanPhase
import com.adischat.app.feature.restaurants.presentation.TimelineStepState
import com.adischat.app.feature.restaurants.presentation.TrackingOrderLineUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RestaurantsQaRoute(val title: String) {
    HUB("Restaurants QA Hub"),
    DISCOVER("Restaurant Discovery"),
    WORKSPACE("Restaurant Workspace"),
    TABLE_SCAN("QR Table Check-In"),
    CENTER("Restaurant Center"),
    ADMIN("Platform Admin"),
}

data class RestaurantsQaUiState(
    val isQaAllowed: Boolean,
    val isStagingConnected: Boolean,
    val stagingBannerText: String,
    val currentRoute: RestaurantsQaRoute = RestaurantsQaRoute.HUB,
    val backStack: List<RestaurantsQaRoute> = listOf(RestaurantsQaRoute.HUB),
    val bannerNotice: String? = null,
    val currentCustomerUid: String? = RestaurantsQaSampleData.QA_CUSTOMER_UID,
    val selectedRestaurantId: String = RestaurantsQaSampleData.QA_RESTAURANT_ID,
    val selectedWorkspaceTab: RestaurantWorkspaceTab = RestaurantWorkspaceTab.MENU,
    val isWorkspaceCartOpen: Boolean = false,
    val checkoutSheetStep: CheckoutSheetStep = CheckoutSheetStep.CART_REVIEW,
    val isOrdersSheetOpen: Boolean = false,
    val selectedWorkspaceOrderId: String? = null,
    val discoverState: RestaurantsDiscoverUiState = RestaurantsDiscoverUiState(),
    val menuState: RestaurantMenuUiState = RestaurantMenuUiState(),
    val activeMenuItemSheet: MenuItemSheetState? = null,
    val tableScanState: RestaurantTableScanUiState = RestaurantTableScanUiState(),
    val cartState: RestaurantCartUiState = RestaurantCartUiState(),
    val sessionsByRestaurantId: Map<String, ActiveDiningSessionUi> = emptyMap(),
    val cartsByRestaurantId: Map<String, RestaurantCartUiState> = emptyMap(),
    val workspaceOrders: List<RestaurantOrderSummaryUi> = emptyList(),
    val simulatedOrderSequence: Int = 300,
    val orderTrackingState: RestaurantOrderTrackingUiState = RestaurantOrderTrackingUiState(),
    val centerState: RestaurantCenterUiState = RestaurantCenterUiState(),
    val adminState: RestaurantAdminUiState = RestaurantAdminUiState(),
)

/**
 * Isolated offline QA fixtures matching [RestaurantsPreviewFixtures] and
 * [RestaurantsOrderingPreviewFixtures] without touching production repositories.
 */
object RestaurantsQaSampleData {
    const val QA_CUSTOMER_UID = "qa-customer-self"
    const val OTHER_CUSTOMER_UID = "qa-customer-other"
    const val QA_RESTAURANT_ID = "prev-rest-habesha"
    const val QA_KAFFA_RESTAURANT_ID = "prev-rest-kaffa"
    const val QA_ENTOTO_RESTAURANT_ID = "prev-rest-entoto"
    private const val QA_BRANCH_BOLE_ID = "prev-br-bole"
    private const val QA_BRANCH_KAZANCHIS_ID = "prev-br-kazanchis"
    private const val QA_APP_ID = "qa-app-habesha-01"

    fun centerState(
        environment: RestaurantsEnvironment,
        selectedTab: RestaurantCenterTab = RestaurantCenterTab.OVERVIEW,
        previewRole: RestaurantStaffRole = RestaurantStaffRole.RESTAURANT_OWNER,
    ): RestaurantCenterUiState {
        if (!environment.isRestaurantCenterEnabled) {
            return RestaurantCenterUiState(
                loadState = RestaurantsLoadState.Disabled,
                isCenterEnabled = false,
            )
        }
        val branches = RestaurantsPreviewFixtures.branchesByRestaurantId[QA_RESTAURANT_ID].orEmpty().map { br ->
            CenterBranchUi(
                id = br.id,
                restaurantId = br.restaurantId,
                code = br.code,
                name = br.name,
                address = br.address,
                city = br.city,
                requireStaffSessionActivation = br.requireStaffSessionActivation,
                requireSessionCode = br.requireSessionCode,
                isDineInActive = br.isDineInActive,
                status = "active",
                openingHours = br.openingHours,
            )
        }
        val secondBranchId = branches.getOrNull(1)?.id ?: QA_BRANCH_BOLE_ID
        val categories = RestaurantsPreviewFixtures.categoriesByRestaurantId[QA_RESTAURANT_ID].orEmpty().map { cat ->
            MenuCategoryDto(
                id = cat.id,
                restaurantId = QA_RESTAURANT_ID,
                name = cat.name,
                sortOrder = cat.sortOrder,
                isActive = true,
            )
        }
        val categoryNames = categories.associate { it.id to it.name }
        val menuItems = RestaurantsPreviewFixtures.menuItemsFor(QA_RESTAURANT_ID, QA_BRANCH_BOLE_ID).map { item ->
            CenterMenuItemUi(
                id = item.id,
                restaurantId = item.restaurantId,
                categoryId = item.categoryId,
                categoryName = categoryNames[item.categoryId] ?: "Menu",
                name = item.name,
                description = item.description,
                basePriceMinor = item.basePriceMinor,
                branchPriceOverrideMinor = item.priceOverrideMinor,
                isAvailableAtBranch = item.isAvailableAtBranch,
                isActive = true,
                photoPath = null,
            )
        }
        return RestaurantCenterUiState(
            loadState = RestaurantsLoadState.Content,
            isCenterEnabled = true,
            activeAccountUid = "qa-uid-owner",
            selectedTab = selectedTab,
            applications = listOf(
                CenterApplicationUi(
                    id = QA_APP_ID,
                    applicantProfileId = "qa-uid-owner",
                    legalName = "Habesha Culinary PLC",
                    brandName = "Habesha Hearth",
                    description = "Wood-fired Ethiopian platters & clay-pot specialties in Addis Ababa.",
                    businessType = "Full-Service Dine-In",
                    contactPhone = "+251900000001",
                    contactEmail = "qa-partner@example.invalid",
                    tinNumber = "0041234567",
                    logoPath = null,
                    coverPath = null,
                    status = RestaurantApplicationStatus.DRAFT,
                    reviewNotes = "Sample QA draft — tap document/media pickers to verify native picker UX.",
                    approvedRestaurantId = null,
                    branchDraftCount = branches.size,
                    documents = listOf(
                        CenterApplicationDocUi(
                            id = "qa-doc-license",
                            docType = "business_license",
                            fileName = "sample-trade-license.pdf",
                            mimeType = "application/pdf",
                            byteSize = 248_000L,
                        ),
                    ),
                ),
            ),
            ownedRestaurants = listOf(
                CenterRestaurantUi(
                    id = QA_RESTAURANT_ID,
                    slug = "habesha-hearth",
                    name = "Habesha Hearth",
                    description = "Wood-fired traditional Ethiopian platters, clay-pot stews, and freshly roasted buna ceremony.",
                    status = RestaurantOperationalStatus.ACTIVE,
                    isLiveOrderingEnabled = false,
                    suspendedReason = null,
                    logoPath = null,
                    coverPath = null,
                    currentUserRole = previewRole,
                    currentUserMembershipStatus = "active",
                    assignedBranchId = null,
                ),
            ),
            selectedRestaurantId = QA_RESTAURANT_ID,
            branches = branches,
            selectedBranchId = QA_BRANCH_BOLE_ID,
            categories = categories,
            menuItems = menuItems,
            tables = listOf(
                CenterTableUi(
                    id = "prev-table-4",
                    restaurantId = QA_RESTAURANT_ID,
                    branchId = QA_BRANCH_BOLE_ID,
                    tableCode = "TBL-04",
                    label = "Table 4 • Patio",
                    zone = "Patio",
                    capacity = 4,
                    isActive = true,
                    activeQrVersion = 3,
                    activeQrStatus = "active",
                    openSessionId = "prev-session-001",
                    openSessionExpiresAt = "Today until 10:30 PM",
                    hasSessionCode = true,
                    latestGeneratedSessionCode = "4812",
                    latestQrUrl = null,
                ),
                CenterTableUi(
                    id = "prev-table-2",
                    restaurantId = QA_RESTAURANT_ID,
                    branchId = QA_BRANCH_BOLE_ID,
                    tableCode = "TBL-02",
                    label = "Table 2 • Main Hall",
                    zone = "Main Hall",
                    capacity = 2,
                    isActive = true,
                    activeQrVersion = 1,
                    activeQrStatus = "active",
                    openSessionId = null,
                    openSessionExpiresAt = null,
                    hasSessionCode = false,
                ),
            ),
            orders = listOf(
                CenterOrderTicketUi(
                    orderId = "qa-ord-101",
                    orderNumber = "ORD-101",
                    restaurantId = QA_RESTAURANT_ID,
                    branchId = QA_BRANCH_BOLE_ID,
                    tableLabel = "Table 4 • Patio",
                    itemCountLabel = "2x Clay-Pot Shekla Tibs, 1x Jebena Buna",
                    status = RestaurantOrderStatus.ORDER_SUBMITTED,
                    paymentStatus = PaymentStatusUi.UNPAID,
                    totalMinor = 183000L,
                    customerNotes = "Water with lemon if available",
                    createdAtLabel = "Just now",
                ),
                CenterOrderTicketUi(
                    orderId = "qa-ord-102",
                    orderNumber = "ORD-102",
                    restaurantId = QA_RESTAURANT_ID,
                    branchId = QA_BRANCH_BOLE_ID,
                    tableLabel = "Table 2 • Main Hall",
                    itemCountLabel = "1x Beyaynetu Royal Fasting",
                    status = RestaurantOrderStatus.PREPARING,
                    paymentStatus = PaymentStatusUi.UNPAID,
                    totalMinor = 42000L,
                    createdAtLabel = "10 min ago",
                ),
                CenterOrderTicketUi(
                    orderId = "qa-ord-103",
                    orderNumber = "ORD-103",
                    restaurantId = QA_RESTAURANT_ID,
                    branchId = QA_BRANCH_BOLE_ID,
                    tableLabel = "Table 1 • Window",
                    itemCountLabel = "1x Special Bozena Shiro",
                    status = RestaurantOrderStatus.SERVED,
                    paymentStatus = PaymentStatusUi.UNPAID,
                    totalMinor = 36000L,
                    createdAtLabel = "25 min ago",
                ),
                CenterOrderTicketUi(
                    orderId = "qa-ord-104",
                    orderNumber = "ORD-104",
                    restaurantId = QA_RESTAURANT_ID,
                    branchId = secondBranchId,
                    tableLabel = "Table 7 • Lounge",
                    itemCountLabel = "1x Jebena Buna Service",
                    status = RestaurantOrderStatus.COMPLETED,
                    paymentStatus = PaymentStatusUi.PAID,
                    totalMinor = 28000L,
                    createdAtLabel = "Earlier today",
                ),
            ),
            staffMembers = listOf(
                CenterStaffMemberUi(
                    staffMemberId = "qa-staff-owner",
                    restaurantId = QA_RESTAURANT_ID,
                    branchId = null,
                    profileId = "qa-uid-owner",
                    displayName = "Abebe Bikila (Owner)",
                    role = RestaurantStaffRole.RESTAURANT_OWNER,
                    status = "active",
                ),
                CenterStaffMemberUi(
                    staffMemberId = "qa-staff-kitchen",
                    restaurantId = QA_RESTAURANT_ID,
                    branchId = QA_BRANCH_BOLE_ID,
                    profileId = "qa-uid-kitchen",
                    displayName = "Tigist Haile (Chef)",
                    role = RestaurantStaffRole.KITCHEN_STAFF,
                    status = "active",
                ),
                CenterStaffMemberUi(
                    staffMemberId = "qa-staff-cashier",
                    restaurantId = QA_RESTAURANT_ID,
                    branchId = QA_BRANCH_BOLE_ID,
                    profileId = "qa-uid-cashier",
                    displayName = "Dawit Tadesse (Cashier)",
                    role = RestaurantStaffRole.CASHIER,
                    status = "active",
                ),
            ),
            staffInvitations = listOf(
                CenterStaffInvitationUi(
                    id = "qa-inv-1",
                    restaurantId = QA_RESTAURANT_ID,
                    branchId = QA_BRANCH_BOLE_ID,
                    role = RestaurantStaffRole.WAITER,
                    invitedIdentifier = "+251900000099",
                    status = "pending",
                    expiresAt = "48h",
                ),
            ),
        )
    }

    fun adminState(
        environment: RestaurantsEnvironment,
        selectedTab: RestaurantAdminTab = RestaurantAdminTab.APPLICATIONS,
    ): RestaurantAdminUiState {
        if (!environment.isAdminEnabled) {
            return RestaurantAdminUiState(
                loadState = RestaurantsLoadState.Disabled,
                isAdminEnabled = false,
            )
        }
        return RestaurantAdminUiState(
            loadState = RestaurantsLoadState.Content,
            isAdminEnabled = true,
            activeAccountUid = "qa-admin-uid",
            isAuthorizedAdmin = true,
            selectedTab = selectedTab,
            applications = listOf(
                AdminApplicationUi(
                    id = QA_APP_ID,
                    applicantProfileId = "qa-uid-owner",
                    brandName = "Habesha Hearth",
                    legalName = "Habesha Culinary PLC",
                    contactEmail = "qa-partner@example.invalid",
                    contactPhone = "+251900000001",
                    tinNumber = "0041234567",
                    status = RestaurantApplicationStatus.UNDER_REVIEW,
                    submittedAt = "2026-11-10",
                    branchCount = 2,
                    draftMenuItemsCount = 3,
                    documents = listOf(
                        AdminApplicationDocUi(
                            id = "qa-doc-license",
                            docType = "business_license",
                            fileName = "sample-trade-license.pdf",
                            mimeType = "application/pdf",
                            byteSize = 248_000L,
                        ),
                    ),
                ),
            ),
            selectedApplicationId = QA_APP_ID,
            restaurants = listOf(
                AdminRestaurantOverviewUi(
                    id = QA_RESTAURANT_ID,
                    name = "Habesha Hearth",
                    slug = "habesha-hearth",
                    status = RestaurantOperationalStatus.ACTIVE,
                    isLiveOrderingEnabled = false,
                    branches = RestaurantsPreviewFixtures.branchesByRestaurantId[QA_RESTAURANT_ID].orEmpty(),
                ),
            ),
            auditLogs = listOf(
                AdminAuditEntryUi(
                    id = "qa-audit-1",
                    restaurantId = QA_RESTAURANT_ID,
                    actorProfileId = "qa-admin-uid",
                    action = "qa_preview_initialized",
                    targetType = "restaurant_application",
                    targetId = QA_APP_ID,
                    createdAt = "Offline QA sample",
                ),
            ),
        )
    }

    fun initialSessionsByRestaurantId(): Map<String, ActiveDiningSessionUi> = mapOf(
        QA_RESTAURANT_ID to ActiveDiningSessionUi(
            ownerAdisChatUid = QA_CUSTOMER_UID,
            restaurantId = QA_RESTAURANT_ID,
            restaurantName = "Habesha Hearth",
            branchId = QA_BRANCH_BOLE_ID,
            branchName = "Bole Flagship",
            tableId = "qa-tbl-bole-4",
            tableLabel = "Table 4 • Patio",
            diningSessionId = "qa-ds-habesha-bole-4",
            expiresAt = "Today • 23:30",
        ),
        QA_KAFFA_RESTAURANT_ID to ActiveDiningSessionUi(
            ownerAdisChatUid = QA_CUSTOMER_UID,
            restaurantId = QA_KAFFA_RESTAURANT_ID,
            restaurantName = "Kaffa Roastery & Bistro",
            branchId = QA_BRANCH_KAZANCHIS_ID,
            branchName = "Kazanchis Roastery",
            tableId = "qa-tbl-kaffa-2",
            tableLabel = "Table 2 • Terrace",
            diningSessionId = "qa-ds-kaffa-kaz-2",
            expiresAt = "Today • 22:00",
        ),
    )

    fun initialCartsByRestaurantId(dineInEnabled: Boolean): Map<String, RestaurantCartUiState> {
        val sessions = initialSessionsByRestaurantId()
        val habeshaSession = sessions.getValue(QA_RESTAURANT_ID)
        val kaffaSession = sessions.getValue(QA_KAFFA_RESTAURANT_ID)
        val habeshaItems = listOf(
            DineInCartItemUi(
                lineId = "qa-line-habesha-tibs",
                menuItemId = "item-shekla-tibs",
                itemName = "Clay-Pot Shekla Tibs",
                restaurantId = QA_RESTAURANT_ID,
                branchId = QA_BRANCH_BOLE_ID,
                baseUnitPriceMinor = 68000L,
                selectedOptions = listOf(
                    SelectedCartOptionUi("opt-single", "Solo Platter", "Portion Size", 0L),
                    SelectedCartOptionUi("opt-mild", "Mild Awaze", "Heat Level", 0L),
                ),
                quantity = 1,
                specialInstructions = "Local QA preview item",
            ),
        )
        return mapOf(
            QA_RESTAURANT_ID to RestaurantCartUiState(
                activeSession = habeshaSession,
                items = habeshaItems,
                isDineInEnabled = dineInEnabled,
            ),
            QA_KAFFA_RESTAURANT_ID to RestaurantCartUiState(
                activeSession = kaffaSession,
                items = emptyList(),
                isDineInEnabled = dineInEnabled,
            ),
        )
    }

    fun initialWorkspaceOrders(environment: RestaurantsEnvironment): List<RestaurantOrderSummaryUi> {
        val syncNote = OrderTrackingSyncState.OfflineOrReconnecting(
            environment.stagingStatusMessage("Order status sync"),
        )
        val habeshaCmcBranch = RestaurantsPreviewFixtures.branchesByRestaurantId[QA_RESTAURANT_ID]
            ?.getOrNull(1)
        val habeshaPreparingTracking = buildTrackingSnapshot(
            orderId = "qa-ord-habesha-104",
            orderNumber = "104",
            restaurantName = "Habesha Hearth",
            branchName = "Bole Flagship",
            tableLabel = "Table 4 • Patio",
            status = RestaurantOrderStatus.PREPARING,
            paymentStatus = PaymentStatusUi.UNPAID,
            totalMinor = 103000L,
            items = listOf(
                TrackingOrderLineUi("li-1", "Shekla Tibs", 1, 68000L),
                TrackingOrderLineUi("li-2", "Vegetarian Beyaynetu", 1, 35000L),
            ),
            syncState = syncNote,
            isDineInEnabled = environment.isDineInEnabled,
        )
        val habeshaCompletedTracking = buildTrackingSnapshot(
            orderId = "qa-ord-habesha-098",
            orderNumber = "098",
            restaurantName = "Habesha Hearth",
            branchName = "Bole Flagship",
            tableLabel = "Table 1 • Indoor",
            status = RestaurantOrderStatus.COMPLETED,
            paymentStatus = PaymentStatusUi.PAID,
            totalMinor = 136000L,
            items = listOf(
                TrackingOrderLineUi("li-3", "Special Doro Wot", 2, 124000L),
                TrackingOrderLineUi("li-4", "Traditional Jebena Buna", 1, 12000L),
            ),
            syncState = syncNote,
            isDineInEnabled = environment.isDineInEnabled,
            receiptRef = "RCPT-2026-098",
        )
        val habeshaRejectedTracking = buildTrackingSnapshot(
            orderId = "qa-ord-habesha-091",
            orderNumber = "091",
            restaurantName = "Habesha Hearth",
            branchName = habeshaCmcBranch?.name ?: "CMC Branch",
            tableLabel = "Table 6 • Main Hall",
            status = RestaurantOrderStatus.REJECTED,
            paymentStatus = PaymentStatusUi.UNPAID,
            totalMinor = 71000L,
            items = listOf(
                TrackingOrderLineUi("li-5", "Shekla Tibs", 1, 71000L),
            ),
            syncState = syncNote,
            isDineInEnabled = environment.isDineInEnabled,
            terminalReason = "Kitchen closed for evening cleaning",
        )
        val kaffaAcceptedTracking = buildTrackingSnapshot(
            orderId = "qa-ord-kaffa-207",
            orderNumber = "207",
            restaurantName = "Kaffa Roastery & Bistro",
            branchName = "Kazanchis Roastery",
            tableLabel = "Table 2 • Terrace",
            status = RestaurantOrderStatus.ACCEPTED,
            paymentStatus = PaymentStatusUi.UNPAID,
            totalMinor = 67000L,
            items = listOf(
                TrackingOrderLineUi("li-6", "Roast Chicken Focaccia", 1, 49000L),
                TrackingOrderLineUi("li-7", "Ethiopian Single-Origin Pour-Over", 1, 18000L),
            ),
            syncState = syncNote,
            isDineInEnabled = environment.isDineInEnabled,
        )
        val kaffaCompletedTracking = buildTrackingSnapshot(
            orderId = "qa-ord-kaffa-201",
            orderNumber = "201",
            restaurantName = "Kaffa Roastery & Bistro",
            branchName = "Kazanchis Roastery",
            tableLabel = "Table 5 • Window",
            status = RestaurantOrderStatus.COMPLETED,
            paymentStatus = PaymentStatusUi.PAID,
            totalMinor = 44000L,
            items = listOf(
                TrackingOrderLineUi("li-8", "Spiced Honey Croissant", 2, 44000L),
            ),
            syncState = syncNote,
            isDineInEnabled = environment.isDineInEnabled,
            receiptRef = "RCPT-2026-201",
        )

        return listOf(
            RestaurantOrderSummaryUi(
                orderId = "qa-ord-habesha-104",
                orderNumber = "104",
                restaurantId = QA_RESTAURANT_ID,
                restaurantName = "Habesha Hearth",
                branchId = QA_BRANCH_BOLE_ID,
                branchName = "Bole Flagship",
                tableLabel = "Table 4 • Patio",
                diningSessionId = "qa-ds-habesha-bole-4",
                customerUid = QA_CUSTOMER_UID,
                status = RestaurantOrderStatus.PREPARING,
                paymentStatus = PaymentStatusUi.UNPAID,
                itemsSummaryLabel = "1x Shekla Tibs, 1x Vegetarian Beyaynetu",
                itemsCount = 2,
                totalMinor = 103000L,
                createdAtLabel = "Today • 18:10",
                isSimulatedQa = true,
                trackingSnapshot = habeshaPreparingTracking,
            ),
            RestaurantOrderSummaryUi(
                orderId = "qa-ord-habesha-098",
                orderNumber = "098",
                restaurantId = QA_RESTAURANT_ID,
                restaurantName = "Habesha Hearth",
                branchId = QA_BRANCH_BOLE_ID,
                branchName = "Bole Flagship",
                tableLabel = "Table 1 • Indoor",
                diningSessionId = "qa-ds-habesha-past-1",
                customerUid = QA_CUSTOMER_UID,
                status = RestaurantOrderStatus.COMPLETED,
                paymentStatus = PaymentStatusUi.PAID,
                itemsSummaryLabel = "2x Special Doro Wot, 1x Traditional Jebena Buna",
                itemsCount = 3,
                totalMinor = 136000L,
                createdAtLabel = "Yesterday • 19:40",
                isSimulatedQa = true,
                trackingSnapshot = habeshaCompletedTracking,
            ),
            RestaurantOrderSummaryUi(
                orderId = "qa-ord-habesha-091",
                orderNumber = "091",
                restaurantId = QA_RESTAURANT_ID,
                restaurantName = "Habesha Hearth",
                branchId = habeshaCmcBranch?.id ?: "prev-br-cmc",
                branchName = habeshaCmcBranch?.name ?: "CMC Branch",
                tableLabel = "Table 6 • Main Hall",
                diningSessionId = "qa-ds-habesha-past-2",
                customerUid = QA_CUSTOMER_UID,
                status = RestaurantOrderStatus.REJECTED,
                paymentStatus = PaymentStatusUi.UNPAID,
                itemsSummaryLabel = "1x Shekla Tibs",
                itemsCount = 1,
                totalMinor = 71000L,
                createdAtLabel = "3 days ago • 21:05",
                isSimulatedQa = true,
                trackingSnapshot = habeshaRejectedTracking,
            ),
            RestaurantOrderSummaryUi(
                orderId = "qa-ord-kaffa-207",
                orderNumber = "207",
                restaurantId = QA_KAFFA_RESTAURANT_ID,
                restaurantName = "Kaffa Roastery & Bistro",
                branchId = QA_BRANCH_KAZANCHIS_ID,
                branchName = "Kazanchis Roastery",
                tableLabel = "Table 2 • Terrace",
                diningSessionId = "qa-ds-kaffa-kaz-2",
                customerUid = QA_CUSTOMER_UID,
                status = RestaurantOrderStatus.ACCEPTED,
                paymentStatus = PaymentStatusUi.UNPAID,
                itemsSummaryLabel = "1x Roast Chicken Focaccia, 1x Single-Origin Pour-Over",
                itemsCount = 2,
                totalMinor = 67000L,
                createdAtLabel = "Today • 17:50",
                isSimulatedQa = true,
                trackingSnapshot = kaffaAcceptedTracking,
            ),
            RestaurantOrderSummaryUi(
                orderId = "qa-ord-kaffa-201",
                orderNumber = "201",
                restaurantId = QA_KAFFA_RESTAURANT_ID,
                restaurantName = "Kaffa Roastery & Bistro",
                branchId = QA_BRANCH_KAZANCHIS_ID,
                branchName = "Kazanchis Roastery",
                tableLabel = "Table 5 • Window",
                diningSessionId = "qa-ds-kaffa-past-1",
                customerUid = QA_CUSTOMER_UID,
                status = RestaurantOrderStatus.COMPLETED,
                paymentStatus = PaymentStatusUi.PAID,
                itemsSummaryLabel = "2x Spiced Honey Croissant",
                itemsCount = 2,
                totalMinor = 44000L,
                createdAtLabel = "Last week • 10:15",
                isSimulatedQa = true,
                trackingSnapshot = kaffaCompletedTracking,
            ),
            // Unowned order belonging to another customer UID — must never leak to currentCustomerUid
            RestaurantOrderSummaryUi(
                orderId = "qa-ord-other-user-999",
                orderNumber = "999",
                restaurantId = QA_RESTAURANT_ID,
                restaurantName = "Habesha Hearth",
                branchId = QA_BRANCH_BOLE_ID,
                branchName = "Bole Flagship",
                tableLabel = "Table 9 • VIP",
                diningSessionId = "qa-ds-other-user",
                customerUid = OTHER_CUSTOMER_UID,
                status = RestaurantOrderStatus.PREPARING,
                paymentStatus = PaymentStatusUi.UNPAID,
                itemsSummaryLabel = "3x Special Doro Wot (Other customer)",
                itemsCount = 3,
                totalMinor = 186000L,
                createdAtLabel = "Today • 18:12",
                isSimulatedQa = true,
                trackingSnapshot = null,
            ),
        )
    }

    fun buildTrackingSnapshot(
        orderId: String,
        orderNumber: String,
        restaurantName: String,
        branchName: String,
        tableLabel: String,
        status: RestaurantOrderStatus,
        paymentStatus: PaymentStatusUi,
        totalMinor: Long,
        items: List<TrackingOrderLineUi>,
        syncState: OrderTrackingSyncState,
        isDineInEnabled: Boolean,
        terminalReason: String? = null,
        receiptRef: String? = null,
    ): RestaurantOrderTrackingUiState = RestaurantOrderTrackingUiState(
        loadState = RestaurantsLoadState.Content,
        syncState = syncState,
        orderId = orderId,
        orderNumber = orderNumber,
        restaurantName = restaurantName,
        branchName = branchName,
        tableLabel = tableLabel,
        currentStatus = status,
        terminalReason = terminalReason,
        paymentStatus = paymentStatus,
        paymentReceiptRef = receiptRef,
        confirmedTotalMinor = totalMinor,
        items = items,
        timelineSteps = OrderTrackingReducers.buildTimelineSteps(
            currentStatus = status,
        ),
        isDineInEnabled = isDineInEnabled,
    )
}

/**
 * Coordinates isolated offline QA preview navigation and user actions for Restaurants.
 * Strictly blocked when [RestaurantsEnvironment.isQaBuild] is false, and never fakes remote
 * mutations, approvals, uploads, or order placements when staging is disconnected.
 */
class RestaurantsQaPreviewController(
    private val environment: RestaurantsEnvironment = RestaurantsEnvironment(),
) {
    private val _state = MutableStateFlow(buildInitialState())
    val state: StateFlow<RestaurantsQaUiState> = _state.asStateFlow()

    private fun buildInitialState(): RestaurantsQaUiState {
        val qaAllowed = (environment.isQaBuild || environment.isOfflinePreview) && (
            environment.isDineInEnabled ||
                environment.isRestaurantCenterEnabled ||
                environment.isAdminEnabled
            )
        if (!qaAllowed) {
            return RestaurantsQaUiState(
                isQaAllowed = false,
                isStagingConnected = environment.isStagingConnected(),
                stagingBannerText = "QA Preview Disabled",
                currentCustomerUid = null,
                discoverState = RestaurantsDiscoverUiState(
                    loadState = RestaurantsLoadState.Disabled,
                    isDineInEnabled = false,
                ),
                menuState = RestaurantMenuUiState(
                    loadState = RestaurantsLoadState.Disabled,
                    isDineInEnabled = false,
                ),
                tableScanState = RestaurantTableScanUiState(
                    phase = TableScanPhase.Disabled,
                    isDineInEnabled = false,
                ),
                cartState = RestaurantCartUiState(isDineInEnabled = false),
                orderTrackingState = RestaurantOrderTrackingUiState(
                    loadState = RestaurantsLoadState.Disabled,
                    isDineInEnabled = false,
                ),
                centerState = RestaurantCenterUiState(
                    loadState = RestaurantsLoadState.Disabled,
                    isCenterEnabled = false,
                ),
                adminState = RestaurantAdminUiState(
                    loadState = RestaurantsLoadState.Disabled,
                    isAdminEnabled = false,
                ),
            )
        }

        val dineInEnabled = environment.isDineInEnabled
        val sessions = if (dineInEnabled) {
            RestaurantsQaSampleData.initialSessionsByRestaurantId()
        } else {
            emptyMap()
        }
        val carts = if (dineInEnabled) {
            RestaurantsQaSampleData.initialCartsByRestaurantId(dineInEnabled = true)
        } else {
            emptyMap()
        }
        val initialRestaurantId = RestaurantsQaSampleData.QA_RESTAURANT_ID
        val initialCart = carts[initialRestaurantId] ?: RestaurantCartUiState(
            activeSession = sessions[initialRestaurantId],
            isDineInEnabled = dineInEnabled,
        )
        val initialBranchId = initialCart.activeSession?.branchId
            ?: RestaurantsPreviewFixtures.branchesByRestaurantId[initialRestaurantId]?.firstOrNull()?.id
        val initialMenu = RestaurantsPreviewFixtures.menuState(
            restaurantId = initialRestaurantId,
            branchId = initialBranchId,
            isDineInEnabled = dineInEnabled,
        ).copy(
            isBrowseOnly = initialCart.activeSession == null ||
                initialCart.activeSession.branchId != initialBranchId,
        )
        val initialOrders = if (dineInEnabled) {
            RestaurantsQaSampleData.initialWorkspaceOrders(environment)
        } else {
            emptyList()
        }

        return RestaurantsQaUiState(
            isQaAllowed = true,
            isStagingConnected = environment.isStagingConnected(),
            stagingBannerText = when {
                environment.isOfflinePreview ->
                    "${RestaurantsEnvironment.OFFLINE_QA_PREVIEW_LABEL} • Local fixtures only (No backend)"
                environment.isStagingConnected() ->
                    "QA PREVIEW • Connected to isolated staging backend"
                else ->
                    "QA PREVIEW • ${RestaurantsEnvironment.STAGING_NOT_CONNECTED_MESSAGE}"
            },
            currentCustomerUid = RestaurantsQaSampleData.QA_CUSTOMER_UID,
            selectedRestaurantId = initialRestaurantId,
            discoverState = RestaurantsPreviewFixtures.discoverState(isDineInEnabled = dineInEnabled),
            menuState = initialMenu,
            tableScanState = RestaurantTableScanUiState(
                phase = if (dineInEnabled) TableScanPhase.AwaitingScan else TableScanPhase.Disabled,
                isDineInEnabled = dineInEnabled,
            ),
            cartState = initialCart,
            sessionsByRestaurantId = sessions,
            cartsByRestaurantId = carts,
            workspaceOrders = initialOrders,
            orderTrackingState = initialOrders.firstOrNull {
                it.restaurantId == initialRestaurantId
            }?.trackingSnapshot
                ?: RestaurantsOrderingPreviewFixtures.previewPreparingTrackingState().copy(
                    isDineInEnabled = dineInEnabled,
                    syncState = OrderTrackingSyncState.OfflineOrReconnecting(
                        environment.stagingStatusMessage("Order status sync"),
                    ),
                ),
            centerState = RestaurantsQaSampleData.centerState(environment),
            adminState = RestaurantsQaSampleData.adminState(environment),
        )
    }

    fun dismissBannerNotice() {
        _state.update { it.copy(bannerNotice = null) }
    }

    fun openRoute(route: RestaurantsQaRoute) {
        if (route == RestaurantsQaRoute.WORKSPACE) {
            openRestaurantWorkspace(_state.value.selectedRestaurantId)
            return
        }
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val updatedStack = when {
                route == RestaurantsQaRoute.HUB -> listOf(RestaurantsQaRoute.HUB)
                current.currentRoute == route -> current.backStack
                else -> current.backStack + route
            }
            current.copy(
                currentRoute = route,
                backStack = updatedStack,
                activeMenuItemSheet = null,
                isWorkspaceCartOpen = false,
                selectedWorkspaceOrderId = null,
                bannerNotice = null,
            )
        }
    }

    fun openCenterTab(tab: RestaurantCenterTab) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val nextStack = if (current.currentRoute == RestaurantsQaRoute.CENTER) {
                current.backStack
            } else {
                current.backStack + RestaurantsQaRoute.CENTER
            }
            current.copy(
                currentRoute = RestaurantsQaRoute.CENTER,
                backStack = nextStack,
                centerState = current.centerState.copy(
                    selectedTab = tab,
                    errorBanner = null,
                    actionFeedback = null,
                ),
                bannerNotice = null,
            )
        }
    }

    fun openAdminTab(tab: RestaurantAdminTab) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val nextStack = if (current.currentRoute == RestaurantsQaRoute.ADMIN) {
                current.backStack
            } else {
                current.backStack + RestaurantsQaRoute.ADMIN
            }
            current.copy(
                currentRoute = RestaurantsQaRoute.ADMIN,
                backStack = nextStack,
                adminState = current.adminState.copy(
                    selectedTab = tab,
                    errorBanner = null,
                    actionNotice = null,
                ),
                bannerNotice = null,
            )
        }
    }

    fun navigateBack(): Boolean {
        val current = _state.value
        if (!current.isQaAllowed) return false
        if (current.activeMenuItemSheet != null) {
            _state.value = current.copy(activeMenuItemSheet = null, bannerNotice = null)
            return true
        }
        if (current.currentRoute == RestaurantsQaRoute.WORKSPACE && current.isWorkspaceCartOpen) {
            if (current.checkoutSheetStep == CheckoutSheetStep.CHECKOUT_CONFIRM) {
                _state.value = current.copy(checkoutSheetStep = CheckoutSheetStep.CART_REVIEW)
            } else {
                _state.value = current.copy(
                    isWorkspaceCartOpen = false,
                    checkoutSheetStep = CheckoutSheetStep.CART_REVIEW,
                    bannerNotice = null,
                )
            }
            return true
        }
        if (current.currentRoute == RestaurantsQaRoute.WORKSPACE && current.isOrdersSheetOpen) {
            if (current.selectedWorkspaceOrderId != null) {
                _state.value = current.copy(selectedWorkspaceOrderId = null, bannerNotice = null)
            } else {
                _state.value = current.copy(isOrdersSheetOpen = false, bannerNotice = null)
            }
            return true
        }
        if (current.currentRoute == RestaurantsQaRoute.WORKSPACE && current.selectedWorkspaceOrderId != null) {
            _state.value = current.copy(selectedWorkspaceOrderId = null, bannerNotice = null)
            return true
        }
        if (current.backStack.size <= 1) return false
        val trimmed = current.backStack.dropLast(1)
        _state.value = current.copy(
            currentRoute = trimmed.last(),
            backStack = trimmed,
            activeMenuItemSheet = null,
            isWorkspaceCartOpen = false,
            checkoutSheetStep = CheckoutSheetStep.CART_REVIEW,
            isOrdersSheetOpen = false,
            selectedWorkspaceOrderId = null,
            bannerNotice = null,
        )
        return true
    }

    fun onDiscoverSearchChange(query: String) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(discoverState = current.discoverState.copy(searchQuery = query))
        }
    }

    fun onDiscoverCuisineSelected(cuisine: String) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(discoverState = current.discoverState.copy(selectedCuisineFilter = cuisine))
        }
    }

    fun onDiscoverRetry() {
        notifyStagingUnavailable("Refresh restaurants")
    }

    /**
     * Opens the unified customer restaurant workspace for [restaurantId], keeping its branch,
     * dining session, cart, active orders, and history completely isolated from other restaurants.
     */
    fun openRestaurantWorkspace(restaurantId: String, branchId: String? = null) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val restaurantSession = current.sessionsByRestaurantId[restaurantId]
            val branches = RestaurantsPreviewFixtures.branchesByRestaurantId[restaurantId].orEmpty()
            val resolvedBranchId = branchId
                ?: restaurantSession?.branchId
                ?: branches.firstOrNull()?.id

            val baseCart = current.cartsByRestaurantId[restaurantId] ?: RestaurantCartUiState(
                activeSession = restaurantSession,
                isDineInEnabled = environment.isDineInEnabled,
            )
            val syncedCart = if (baseCart.activeSession?.restaurantId == restaurantId) {
                baseCart
            } else {
                baseCart.copy(activeSession = restaurantSession)
            }
            val matchesSession = !current.currentCustomerUid.isNullOrBlank() &&
                restaurantSession != null &&
                restaurantSession.ownerAdisChatUid == current.currentCustomerUid &&
                restaurantSession.restaurantId == restaurantId &&
                (resolvedBranchId == null || restaurantSession.branchId == resolvedBranchId)

            val menu = RestaurantsPreviewFixtures.menuState(
                restaurantId = restaurantId,
                branchId = resolvedBranchId,
                isDineInEnabled = environment.isDineInEnabled,
            ).copy(isBrowseOnly = !matchesSession)

            val nextStack = if (current.currentRoute == RestaurantsQaRoute.WORKSPACE) {
                current.backStack
            } else {
                current.backStack + RestaurantsQaRoute.WORKSPACE
            }
            current.copy(
                currentRoute = RestaurantsQaRoute.WORKSPACE,
                backStack = nextStack,
                selectedRestaurantId = restaurantId,
                selectedWorkspaceTab = RestaurantWorkspaceTab.MENU,
                isWorkspaceCartOpen = false,
                checkoutSheetStep = CheckoutSheetStep.CART_REVIEW,
                isOrdersSheetOpen = false,
                selectedWorkspaceOrderId = null,
                menuState = menu,
                cartState = syncedCart,
                cartsByRestaurantId = current.cartsByRestaurantId + (restaurantId to syncedCart),
                activeMenuItemSheet = null,
                bannerNotice = null,
            )
        }
    }

    fun onWorkspaceBranchSelected(branchId: String) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val restaurantId = current.selectedRestaurantId
            val session = current.sessionsByRestaurantId[restaurantId]
            val matchesSession = !current.currentCustomerUid.isNullOrBlank() &&
                session != null &&
                session.ownerAdisChatUid == current.currentCustomerUid &&
                session.branchId == branchId
            current.copy(
                menuState = RestaurantsPreviewFixtures.menuState(
                    restaurantId = restaurantId,
                    branchId = branchId,
                    isDineInEnabled = environment.isDineInEnabled,
                ).copy(
                    selectedCategoryId = current.menuState.selectedCategoryId,
                    isBrowseOnly = !matchesSession,
                ),
                activeMenuItemSheet = null,
                bannerNotice = null,
            )
        }
    }

    fun selectWorkspaceTab(tab: RestaurantWorkspaceTab) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                selectedWorkspaceTab = tab,
                isWorkspaceCartOpen = false,
                selectedWorkspaceOrderId = null,
                bannerNotice = null,
            )
        }
    }

    fun openWorkspaceCart() {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                isWorkspaceCartOpen = true,
                checkoutSheetStep = CheckoutSheetStep.CART_REVIEW,
                isOrdersSheetOpen = false,
                selectedWorkspaceOrderId = null,
                activeMenuItemSheet = null,
            )
        }
    }

    fun closeWorkspaceCart() {
        _state.update {
            it.copy(
                isWorkspaceCartOpen = false,
                checkoutSheetStep = CheckoutSheetStep.CART_REVIEW,
            )
        }
    }

    fun onCheckoutStepChange(step: CheckoutSheetStep) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(checkoutSheetStep = step)
        }
    }

    fun onSelectOrderMode(mode: DineInOrderMode) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val activeCart = current.cartsByRestaurantId[current.selectedRestaurantId] ?: current.cartState
            when (val outcome = DineInCartReducers.selectOrderMode(activeCart, mode)) {
                is CartMutationOutcome.Updated -> current.copy(
                    cartState = outcome.state,
                    cartsByRestaurantId = current.cartsByRestaurantId + (current.selectedRestaurantId to outcome.state),
                    bannerNotice = null,
                )

                is CartMutationOutcome.Rejected -> current.copy(
                    bannerNotice = outcome.reason,
                )
            }
        }
    }

    fun openOrdersSheet(tab: RestaurantWorkspaceTab = RestaurantWorkspaceTab.MY_ORDERS) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                isOrdersSheetOpen = true,
                selectedWorkspaceTab = if (tab == RestaurantWorkspaceTab.HISTORY) {
                    RestaurantWorkspaceTab.HISTORY
                } else {
                    RestaurantWorkspaceTab.MY_ORDERS
                },
                selectedWorkspaceOrderId = null,
                isWorkspaceCartOpen = false,
                activeMenuItemSheet = null,
                bannerNotice = null,
            )
        }
    }

    fun closeOrdersSheet() {
        _state.update {
            it.copy(
                isOrdersSheetOpen = false,
                selectedWorkspaceOrderId = null,
            )
        }
    }

    fun openWorkspaceOrderTracking(orderId: String) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val order = current.workspaceOrders.firstOrNull {
                it.orderId == orderId &&
                    it.restaurantId == current.selectedRestaurantId &&
                    it.customerUid == current.currentCustomerUid
            } ?: return@update current
            val tracking = order.trackingSnapshot ?: RestaurantsQaSampleData.buildTrackingSnapshot(
                orderId = order.orderId,
                orderNumber = order.orderNumber,
                restaurantName = order.restaurantName,
                branchName = order.branchName,
                tableLabel = order.tableLabel,
                status = order.status,
                paymentStatus = order.paymentStatus,
                totalMinor = order.totalMinor,
                items = listOf(TrackingOrderLineUi("li-${order.orderId}", order.itemsSummaryLabel, order.itemsCount, order.totalMinor)),
                syncState = OrderTrackingSyncState.OfflineOrReconnecting(
                    environment.stagingStatusMessage("Order status sync"),
                ),
                isDineInEnabled = environment.isDineInEnabled,
            )
            current.copy(
                isOrdersSheetOpen = true,
                selectedWorkspaceOrderId = order.orderId,
                orderTrackingState = tracking,
                isWorkspaceCartOpen = false,
            )
        }
    }

    fun closeWorkspaceOrderTracking() {
        _state.update { it.copy(selectedWorkspaceOrderId = null) }
    }

    fun onOrderMoreFood() {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                selectedWorkspaceTab = RestaurantWorkspaceTab.MENU,
                isOrdersSheetOpen = false,
                selectedWorkspaceOrderId = null,
                isWorkspaceCartOpen = false,
                checkoutSheetStep = CheckoutSheetStep.CART_REVIEW,
                bannerNotice = null,
            )
        }
    }

    fun onMenuCategorySelected(categoryId: String?) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(menuState = current.menuState.copy(selectedCategoryId = categoryId))
        }
    }

    fun onMenuRetry() {
        notifyStagingUnavailable("Refresh menu")
    }

    fun openMenuItemSheet(item: MenuItemUiModel) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val uid = current.currentCustomerUid?.takeIf { it.isNotBlank() }
            val session = current.sessionsByRestaurantId[current.selectedRestaurantId]
            val matchesSession = uid != null &&
                session != null &&
                session.ownerAdisChatUid == uid &&
                session.branchId == current.menuState.selectedBranchId
            current.copy(
                activeMenuItemSheet = MenuItemSheetState.initialFor(item)
                    .copy(isBrowseOnly = !matchesSession),
            )
        }
    }

    fun onQuickAddMenuItem(item: MenuItemUiModel) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val uid = current.currentCustomerUid?.takeIf { it.isNotBlank() } ?: return@update current.copy(
                bannerNotice = "Sign in with AdisChat before adding items to a dine-in cart.",
            )
            val restaurantId = current.selectedRestaurantId
            val branchId = current.menuState.selectedBranchId
                ?: return@update current.copy(bannerNotice = "Select a branch before adding items.")
            val session = current.sessionsByRestaurantId[restaurantId]
            if (session == null ||
                session.ownerAdisChatUid != uid ||
                session.restaurantId != restaurantId ||
                session.branchId != branchId
            ) {
                return@update current.copy(
                    bannerNotice = "Scan Table QR for this branch to start a dining session before adding items.",
                )
            }
            val currentCart = (current.cartsByRestaurantId[restaurantId] ?: current.cartState)
                .let { existing ->
                    if (existing.activeSession == session) existing else DineInCartReducers.bindActiveSession(existing, session)
                }
            when (val outcome = DineInCartReducers.quickAddItem(currentCart, item, branchId)) {
                is QuickAddOutcome.Updated -> current.copy(
                    cartState = outcome.state,
                    cartsByRestaurantId = current.cartsByRestaurantId + (restaurantId to outcome.state),
                    activeMenuItemSheet = null,
                    bannerNotice = null,
                )

                is QuickAddOutcome.RequiresCustomization -> current.copy(
                    activeMenuItemSheet = outcome.sheetState,
                    bannerNotice = null,
                )

                is QuickAddOutcome.Rejected -> current.copy(
                    bannerNotice = outcome.reason,
                )
            }
        }
    }

    fun onQuickDecrementMenuItem(menuItemId: String) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val uid = current.currentCustomerUid?.takeIf { it.isNotBlank() } ?: return@update current.copy(
                bannerNotice = "Sign in with AdisChat before updating your cart.",
            )
            val restaurantId = current.selectedRestaurantId
            val branchId = current.menuState.selectedBranchId ?: return@update current
            val session = current.sessionsByRestaurantId[restaurantId]
            if (session == null || session.ownerAdisChatUid != uid || session.branchId != branchId) {
                return@update current.copy(
                    bannerNotice = "Switch back to your active table branch to adjust cart items.",
                )
            }
            val currentCart = current.cartsByRestaurantId[restaurantId] ?: current.cartState
            when (val outcome = DineInCartReducers.quickDecrementItem(currentCart, menuItemId, branchId)) {
                is QuickDecrementOutcome.Updated -> current.copy(
                    cartState = outcome.state,
                    cartsByRestaurantId = current.cartsByRestaurantId + (restaurantId to outcome.state),
                    bannerNotice = null,
                )

                is QuickDecrementOutcome.RequiresVariantResolution -> current.copy(
                    isWorkspaceCartOpen = true,
                    bannerNotice = outcome.reason,
                )

                is QuickDecrementOutcome.Rejected -> current.copy(
                    bannerNotice = outcome.reason,
                )

                is QuickDecrementOutcome.NoOp -> current
            }
        }
    }

    fun dismissMenuItemSheet() {
        _state.update { it.copy(activeMenuItemSheet = null) }
    }

    fun onSheetToggleOption(groupId: String, optionId: String) {
        _state.update { current ->
            val sheet = current.activeMenuItemSheet ?: return@update current
            current.copy(activeMenuItemSheet = sheet.toggleOption(groupId, optionId))
        }
    }

    fun onSheetIncrementQuantity() {
        _state.update { current ->
            val sheet = current.activeMenuItemSheet ?: return@update current
            current.copy(activeMenuItemSheet = sheet.incrementQuantity())
        }
    }

    fun onSheetDecrementQuantity() {
        _state.update { current ->
            val sheet = current.activeMenuItemSheet ?: return@update current
            current.copy(activeMenuItemSheet = sheet.decrementQuantity())
        }
    }

    /**
     * Adds an item to the active restaurant's isolated offline QA preview cart only after checking
     * authenticated customer UID and matching dining session for that restaurant & branch.
     */
    fun addSheetItemToPreviewCart(specialInstructions: String? = null) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val uid = current.currentCustomerUid?.takeIf { it.isNotBlank() } ?: return@update current.copy(
                bannerNotice = "Sign in with AdisChat before adding items to a dine-in cart.",
            )
            val sheet = current.activeMenuItemSheet ?: return@update current
            val restaurantId = current.selectedRestaurantId
            val branchId = current.menuState.selectedBranchId
                ?: return@update current.copy(bannerNotice = "Select a branch before adding items.")
            val session = current.sessionsByRestaurantId[restaurantId]
            if (session == null ||
                session.ownerAdisChatUid != uid ||
                session.restaurantId != restaurantId ||
                session.branchId != branchId
            ) {
                return@update current.copy(
                    bannerNotice = "Scan Table QR for this branch to start a dining session before adding items.",
                )
            }
            val currentCart = (current.cartsByRestaurantId[restaurantId] ?: current.cartState)
                .let { existing ->
                    if (existing.activeSession == session) existing else DineInCartReducers.bindActiveSession(existing, session)
                }
            when (
                val outcome = DineInCartReducers.addItemFromSheet(
                    state = currentCart,
                    sheetState = sheet,
                    branchId = branchId,
                    specialInstructions = specialInstructions,
                )
            ) {
                is CartMutationOutcome.Updated -> current.copy(
                    cartState = outcome.state,
                    cartsByRestaurantId = current.cartsByRestaurantId + (restaurantId to outcome.state),
                    activeMenuItemSheet = null,
                    bannerNotice = "Added ${sheet.item.name} to ${session.restaurantName} preview cart.",
                )
                is CartMutationOutcome.Rejected -> current.copy(
                    bannerNotice = outcome.reason,
                )
            }
        }
    }

    fun openTableScan() {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val nextStack = if (current.currentRoute == RestaurantsQaRoute.TABLE_SCAN) {
                current.backStack
            } else {
                current.backStack + RestaurantsQaRoute.TABLE_SCAN
            }
            current.copy(
                currentRoute = RestaurantsQaRoute.TABLE_SCAN,
                backStack = nextStack,
                isWorkspaceCartOpen = false,
                checkoutSheetStep = CheckoutSheetStep.CART_REVIEW,
                isOrdersSheetOpen = false,
                activeMenuItemSheet = null,
                tableScanState = RestaurantTableScanUiState(
                    phase = if (environment.isDineInEnabled) {
                        TableScanPhase.AwaitingScan
                    } else {
                        TableScanPhase.Disabled
                    },
                    isDineInEnabled = environment.isDineInEnabled,
                ),
                bannerNotice = null,
            )
        }
    }

    fun onQrTokenScanned(rawToken: String) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val captured = TableQrScanReducers.onQrTokenCaptured(current.tableScanState, rawToken)
            if (captured.phase is TableScanPhase.Error || captured.phase is TableScanPhase.Disabled) {
                return@update current.copy(tableScanState = captured)
            }
            if (!environment.isStagingConnected()) {
                val msg = environment.stagingStatusMessage("Table QR verification")
                return@update current.copy(
                    tableScanState = TableQrScanReducers.onResolveFailure(
                        state = captured,
                        failure = RestaurantsFailure.NotConfigured(msg),
                    ),
                    bannerNotice = msg,
                )
            }
            current.copy(tableScanState = captured)
        }
    }

    fun onScanSessionCodeChanged(code: String) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                tableScanState = current.tableScanState.copy(
                    sessionCodeInput = code.take(TableQrScanReducers.MAX_SESSION_CODE_LENGTH),
                ),
            )
        }
    }

    fun onSubmitScanSessionCode() {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            if (!environment.isStagingConnected()) {
                val msg = environment.stagingStatusMessage("Session code verification")
                return@update current.copy(
                    tableScanState = TableQrScanReducers.onResolveFailure(
                        state = current.tableScanState,
                        failure = RestaurantsFailure.NotConfigured(msg),
                    ),
                    bannerNotice = msg,
                )
            }
            current
        }
    }

    fun onRetryTableScan() {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            if (!environment.isStagingConnected()) {
                val msg = environment.stagingStatusMessage("Table QR verification")
                return@update current.copy(
                    tableScanState = current.tableScanState.copy(phase = TableScanPhase.Error(msg)),
                    bannerNotice = msg,
                )
            }
            current.copy(tableScanState = TableQrScanReducers.onRetryWaiterCheck(current.tableScanState))
        }
    }

    fun onScanAgain() {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                tableScanState = TableQrScanReducers.onResetOrCancel(
                    state = current.tableScanState,
                    cancelledByUser = false,
                ),
            )
        }
    }

    fun onCancelTableScan() {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                tableScanState = TableQrScanReducers.onResetOrCancel(
                    state = current.tableScanState,
                    cancelledByUser = true,
                ),
            )
        }
    }

    fun onContinueAfterScanConfirmed(session: ActiveDiningSessionUi) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val existingCart = current.cartsByRestaurantId[session.restaurantId]
                ?: RestaurantCartUiState(isDineInEnabled = environment.isDineInEnabled)
            val boundCart = DineInCartReducers.bindActiveSession(existingCart, session)
            val cleanedStack = current.backStack.filterNot { it == RestaurantsQaRoute.TABLE_SCAN }
            current.copy(
                sessionsByRestaurantId = current.sessionsByRestaurantId + (session.restaurantId to session),
                cartsByRestaurantId = current.cartsByRestaurantId + (session.restaurantId to boundCart),
                cartState = boundCart,
                selectedRestaurantId = session.restaurantId,
                backStack = cleanedStack,
            )
        }
        openRestaurantWorkspace(session.restaurantId, session.branchId)
    }

    fun onCartIncrementQuantity(lineId: String, currentQty: Int) {
        mutateActiveRestaurantCart { cart ->
            DineInCartReducers.updateItemQuantity(cart, lineId, currentQty + 1)
        }
    }

    fun onCartDecrementQuantity(lineId: String, currentQty: Int) {
        mutateActiveRestaurantCart { cart ->
            DineInCartReducers.updateItemQuantity(cart, lineId, currentQty - 1)
        }
    }

    fun onCartRemoveItem(lineId: String) {
        mutateActiveRestaurantCart { cart ->
            DineInCartReducers.removeItem(cart, lineId)
        }
    }

    fun onCartNotesChange(notes: String) {
        mutateActiveRestaurantCart { cart ->
            DineInCartReducers.updateCustomerNotes(cart, notes)
        }
    }

    private fun mutateActiveRestaurantCart(
        transform: (RestaurantCartUiState) -> RestaurantCartUiState,
    ) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val uid = current.currentCustomerUid?.takeIf { it.isNotBlank() } ?: return@update current
            val restaurantId = current.selectedRestaurantId
            val session = current.sessionsByRestaurantId[restaurantId]
            if (session == null || session.ownerAdisChatUid != uid) return@update current
            val activeCart = current.cartsByRestaurantId[restaurantId] ?: current.cartState
            if (activeCart.activeSession?.restaurantId != restaurantId) return@update current
            val updatedCart = transform(activeCart)
            current.copy(
                cartState = updatedCart,
                cartsByRestaurantId = current.cartsByRestaurantId + (restaurantId to updatedCart),
            )
        }
    }

    /**
     * Real order submission remains strictly blocked when staging is disconnected.
     */
    fun onSubmitDineInOrder() {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            if (!environment.isStagingConnected()) {
                val msg = environment.stagingStatusMessage("Order submission")
                val updatedCart = DineInCartReducers.onSubmitFailed(current.cartState, msg)
                return@update current.copy(
                    cartState = updatedCart,
                    cartsByRestaurantId = current.cartsByRestaurantId + (current.selectedRestaurantId to updatedCart),
                    bannerNotice = msg,
                )
            }
            current
        }
    }

    /**
     * Offline QA demo action only: creates a clearly labeled local simulated order inside the
     * active restaurant's My Orders while preserving [ActiveDiningSessionUi] for additional rounds.
     */
    fun createSimulatedQaOrder() {
        _state.update { current ->
            if (!current.isQaAllowed || environment.isStagingConnected()) return@update current
            val uid = current.currentCustomerUid?.takeIf { it.isNotBlank() }
                ?: return@update current.copy(bannerNotice = "Sign in with AdisChat before creating a simulated QA order.")
            val restaurantId = current.selectedRestaurantId
            val cart = current.cartsByRestaurantId[restaurantId] ?: current.cartState
            val session = cart.activeSession
            val selectedBranchId = current.menuState.selectedBranchId
            if (session == null ||
                session.ownerAdisChatUid != uid ||
                session.restaurantId != restaurantId ||
                (selectedBranchId != null && session.branchId != selectedBranchId)
            ) {
                return@update current.copy(
                    bannerNotice = "Active table session for this branch required to simulate a dine-in order.",
                )
            }
            if (cart.items.isEmpty()) {
                return@update current.copy(bannerNotice = "Add items to cart before creating a simulated order.")
            }

            val seq = current.simulatedOrderSequence + 1
            val orderId = "qa-sim-ord-$seq"
            val orderNumber = "QA-$seq"
            val totalMinor = cart.estimatedSubtotalMinor
            val simulatedResult = SubmitOrderResultDto(
                orderId = orderId,
                orderNumber = orderNumber,
                status = RestaurantOrderStatus.ORDER_SUBMITTED.wireValue,
                paymentMethod = DineInCartReducers.PAYMENT_METHOD_PAY_AT_RESTAURANT,
                paymentStatus = "UNPAID",
                totalMinor = totalMinor,
                idempotentReplay = false,
            )
            val tracking = OrderTrackingReducers.fromServerSubmitResult(
                result = simulatedResult,
                session = session,
                submittedItems = cart.items,
                isDineInEnabled = environment.isDineInEnabled,
            ).copy(
                syncState = OrderTrackingSyncState.OfflineOrReconnecting(
                    "Simulated QA Order (Local Only) • Offline demo only; no real kitchen acceptance",
                ),
            )
            val itemsSummary = cart.items.joinToString(", ") { "${it.quantity}x ${it.itemName}" }
            val newOrder = RestaurantOrderSummaryUi(
                orderId = orderId,
                orderNumber = orderNumber,
                restaurantId = session.restaurantId,
                restaurantName = session.restaurantName,
                branchId = session.branchId,
                branchName = session.branchName,
                tableLabel = session.tableLabel,
                diningSessionId = session.diningSessionId,
                customerUid = uid,
                status = RestaurantOrderStatus.ORDER_SUBMITTED,
                paymentStatus = PaymentStatusUi.UNPAID,
                itemsSummaryLabel = itemsSummary,
                itemsCount = cart.totalItemCount,
                totalMinor = totalMinor,
                createdAtLabel = "Just now (Simulated QA)",
                isSimulatedQa = true,
                trackingSnapshot = tracking,
            )

            // Clear cart items and notes while strictly preserving activeSession for ordering more rounds!
            val clearedCart = DineInCartReducers.onSubmitSucceeded(cart, simulatedResult)
            current.copy(
                simulatedOrderSequence = seq,
                cartState = clearedCart,
                cartsByRestaurantId = current.cartsByRestaurantId + (restaurantId to clearedCart),
                workspaceOrders = listOf(newOrder) + current.workspaceOrders,
                orderTrackingState = tracking,
                selectedWorkspaceTab = RestaurantWorkspaceTab.MY_ORDERS,
                isWorkspaceCartOpen = false,
                checkoutSheetStep = CheckoutSheetStep.CART_REVIEW,
                isOrdersSheetOpen = true,
                selectedWorkspaceOrderId = orderId,
                bannerNotice = "Simulated QA Order #$orderNumber created locally (no real restaurant dispatch).",
            )
        }
    }

    fun onRefreshOrderTracking() {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val msg = environment.stagingStatusMessage("Order status refresh")
            current.copy(
                orderTrackingState = current.orderTrackingState.copy(
                    syncState = OrderTrackingSyncState.OfflineOrReconnecting(msg),
                ),
                bannerNotice = msg,
            )
        }
    }

    fun onRefreshWorkspaceOrders() {
        notifyStagingUnavailable("Refresh restaurant orders")
    }

    fun setCustomerUidForQa(uid: String?) {
        val cleanUid = uid?.takeIf { it.isNotBlank() }
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            if (cleanUid == null) {
                val emptyCart = RestaurantCartUiState(isDineInEnabled = environment.isDineInEnabled)
                current.copy(
                    currentCustomerUid = null,
                    sessionsByRestaurantId = emptyMap(),
                    cartsByRestaurantId = emptyMap(),
                    cartState = emptyCart,
                    menuState = current.menuState.copy(isBrowseOnly = true),
                    isWorkspaceCartOpen = false,
                    selectedWorkspaceOrderId = null,
                    bannerNotice = "Signed out in QA preview. Dining sessions and carts cleared.",
                )
            } else {
                val filteredSessions = current.sessionsByRestaurantId.filterValues { it.ownerAdisChatUid == cleanUid }
                val filteredCarts = current.cartsByRestaurantId.filterValues {
                    it.activeSession?.ownerAdisChatUid == cleanUid
                }
                val activeCart = filteredCarts[current.selectedRestaurantId]
                    ?: RestaurantCartUiState(isDineInEnabled = environment.isDineInEnabled)
                current.copy(
                    currentCustomerUid = cleanUid,
                    sessionsByRestaurantId = filteredSessions,
                    cartsByRestaurantId = filteredCarts,
                    cartState = activeCart,
                    menuState = current.menuState.copy(
                        isBrowseOnly = filteredSessions[current.selectedRestaurantId]?.branchId != current.menuState.selectedBranchId,
                    ),
                    isWorkspaceCartOpen = false,
                    selectedWorkspaceOrderId = null,
                )
            }
        }
    }

    fun bindDiningSessionForQa(session: ActiveDiningSessionUi) {
        _state.update { current ->
            if (!current.isQaAllowed || current.currentCustomerUid != session.ownerAdisChatUid) {
                return@update current
            }
            val existingCart = current.cartsByRestaurantId[session.restaurantId]
                ?: RestaurantCartUiState(isDineInEnabled = environment.isDineInEnabled)
            val bound = DineInCartReducers.bindActiveSession(existingCart, session)
            val updatedMenu = if (current.selectedRestaurantId == session.restaurantId) {
                RestaurantsPreviewFixtures.menuState(
                    restaurantId = session.restaurantId,
                    branchId = session.branchId,
                    isDineInEnabled = environment.isDineInEnabled,
                ).copy(isBrowseOnly = false)
            } else {
                current.menuState
            }
            current.copy(
                sessionsByRestaurantId = current.sessionsByRestaurantId + (session.restaurantId to session),
                cartsByRestaurantId = current.cartsByRestaurantId + (session.restaurantId to bound),
                cartState = if (current.selectedRestaurantId == session.restaurantId) bound else current.cartState,
                menuState = updatedMenu,
            )
        }
    }

    fun onCenterTabSelected(tab: RestaurantCenterTab) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                centerState = current.centerState.copy(
                    selectedTab = tab,
                    errorBanner = null,
                    actionFeedback = null,
                ),
            )
        }
    }

    fun onCenterRolePreviewSelected(role: RestaurantStaffRole) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val updatedOwned = current.centerState.ownedRestaurants.map { restaurant ->
                if (restaurant.id == current.centerState.selectedRestaurantId) {
                    restaurant.copy(currentUserRole = role)
                } else {
                    restaurant
                }
            }
            current.copy(
                centerState = current.centerState.copy(
                    ownedRestaurants = updatedOwned,
                    actionFeedback = "Previewing ${role.label} RBAC permissions (local UI preview only).",
                ),
            )
        }
    }

    fun onCenterRestaurantSelected(restaurantId: String) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val nextBranch = current.centerState.branches
                .firstOrNull { it.restaurantId == restaurantId }?.id
            current.copy(
                centerState = current.centerState.copy(
                    selectedRestaurantId = restaurantId,
                    selectedBranchId = nextBranch,
                ),
            )
        }
    }

    fun onCenterBranchSelected(branchId: String) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(centerState = current.centerState.copy(selectedBranchId = branchId))
        }
    }

    fun onCenterOrderFilterSelected(filter: OrderBoardFilter) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(centerState = current.centerState.copy(orderFilter = filter))
        }
    }

    fun onCenterBackendActionBlocked(actionLabel: String) {
        if (!environment.isStagingConnected()) {
            val msg = environment.stagingStatusMessage(actionLabel)
            _state.update { current ->
                if (!current.isQaAllowed) return@update current
                current.copy(
                    centerState = current.centerState.copy(
                        errorBanner = msg,
                        actionFeedback = null,
                    ),
                    bannerNotice = null,
                )
            }
        }
    }

    fun onUploadPickedFile(
        target: RestaurantsPendingPickerTarget,
        source: RestaurantsBinaryUploadSource,
    ) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            val disconnectedMsg = if (!environment.isStagingConnected()) {
                environment.stagingStatusMessage("Signed upload")
            } else {
                null
            }
            if (disconnectedMsg != null) {
                return@update current.copy(
                    centerState = current.centerState.copy(
                        uploadState = CenterUploadUiState(
                            phase = CenterUploadPhase.FAILED,
                            targetKey = target.targetKey,
                            label = target.displayLabel,
                            fileName = source.fileName,
                            mimeType = source.mimeType,
                            byteSize = source.byteSize,
                            errorMessage = disconnectedMsg,
                            canRetry = false,
                        ),
                        errorBanner = null,
                        actionFeedback = null,
                    ),
                    bannerNotice = null,
                )
            }
            current
        }
    }

    fun onUploadValidationError(
        target: RestaurantsPendingPickerTarget,
        message: String,
    ) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                centerState = current.centerState.copy(
                    uploadState = CenterUploadUiState(
                        phase = CenterUploadPhase.FAILED,
                        targetKey = target.targetKey,
                        label = target.displayLabel,
                        errorMessage = message,
                        canRetry = false,
                    ),
                    errorBanner = null,
                    actionFeedback = null,
                ),
                bannerNotice = null,
            )
        }
    }

    fun onPickerCancelled() {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                centerState = current.centerState.copy(
                    uploadState = CenterUploadUiState(
                        phase = CenterUploadPhase.CANCELLED,
                        statusMessage = "File selection cancelled.",
                    ),
                    errorBanner = null,
                    actionFeedback = null,
                ),
                bannerNotice = null,
            )
        }
    }

    fun onCancelUpload() {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                centerState = current.centerState.copy(
                    uploadState = current.centerState.uploadState.copy(
                        phase = CenterUploadPhase.CANCELLED,
                        statusMessage = "Upload cancelled.",
                    ),
                ),
            )
        }
    }

    fun onRetryUpload() {
        onCenterBackendActionBlocked("Retry signed upload")
    }

    fun onDismissUploadStatus() {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                centerState = current.centerState.copy(uploadState = CenterUploadUiState()),
            )
        }
    }

    fun onAdminTabSelected(tab: RestaurantAdminTab) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                adminState = current.adminState.copy(
                    selectedTab = tab,
                    errorBanner = null,
                    actionNotice = null,
                ),
            )
        }
    }

    fun onAdminSelectApplication(applicationId: String) {
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(
                adminState = current.adminState.copy(selectedApplicationId = applicationId),
            )
        }
    }

    fun onAdminBackendActionBlocked(actionLabel: String) {
        if (!environment.isStagingConnected()) {
            val msg = environment.stagingStatusMessage(actionLabel)
            _state.update { current ->
                if (!current.isQaAllowed) return@update current
                current.copy(
                    adminState = current.adminState.copy(
                        errorBanner = msg,
                        actionNotice = null,
                    ),
                    bannerNotice = null,
                )
            }
        }
    }

    private fun notifyStagingUnavailable(actionLabel: String) {
        val msg = environment.stagingStatusMessage(actionLabel)
        _state.update { current ->
            if (!current.isQaAllowed) return@update current
            current.copy(bannerNotice = msg)
        }
    }
}
